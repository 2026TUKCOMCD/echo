package com.example.echo.location.service;

import com.example.echo.common.client.WeatherClient;
import com.example.echo.common.dto.VisitWeather;
import com.example.echo.common.util.GeoDistanceUtil;
import com.example.echo.location.dto.GeocodingResult;
import com.example.echo.location.dto.LocationData;
import com.example.echo.location.dto.RawLocationData;
import com.example.echo.location.dto.RawVisitedPlace;
import com.example.echo.location.dto.VisitedPlace;
import com.example.echo.routineplace.dto.RoutinePlaceInfo;
import com.example.echo.routineplace.entity.RoutinePlace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 위치 데이터 처리 서비스
 *
 * - 원시 위치 데이터를 보강된 위치 데이터로 변환
 * - 역지오코딩으로 장소명/주소 추가
 * - Timemachine API로 방문 시점 날씨 추가
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final GeocodingService geocodingService;
    private final WeatherClient weatherClient;

    /**
     * 거주지(집) 판정 반경 (미터)
     * - 방문 장소의 좌표가 등록된 집 좌표로부터 이 거리 이내이면 "집"으로 분류한다.
     * - 앱의 체류 판정 반경(50m) + GPS 오차/센트로이드 편차를 고려해 넉넉히 잡는다.
     * - 150m였을 때 실내 GPS 오차(집 등록 시 단발 측정 오차 + StayPoint centroid 표류가 겹치면
     *   흔히 100~200m대)로 인해 실제로 집에 있었는데도 "외출"로 오분류되는 버그가 있었음 → 250m로 상향.
     *   너무 크게 잡으면 반대로 아파트 단지 내 짧은 외출까지 "집"으로 삼켜버릴 수 있어 무한정 키우지는 않는다.
     */
    public static final double HOME_MATCH_RADIUS_METERS = 250.0;

    /**
     * 지오코딩/날씨를 조회할 외출 장소 상한.
     * - buildTodayActivityGuide(PromptService)는 결국 체류 시간이 가장 긴 외출 1곳만 언급하도록
     *   AI에 지시하므로, 짧은 체류지까지 전부 역지오코딩·날씨 조회하는 것은 API 낭비.
     * - 그래도 대화 중 다른 곳이 자연스럽게 언급될 여지를 남기기 위해 1곳이 아니라 상위 몇 곳까지는 조회.
     */
    private static final int OUTING_ENRICHMENT_LIMIT = 3;

    /**
     * 원시 위치 데이터를 보강된 위치 데이터로 변환 (집 좌표 없이 - 모두 외출로 취급)
     *
     * @param raw 앱에서 받은 원시 위치 데이터
     * @return 장소명, 주소, 날씨가 추가된 위치 데이터
     */
    public LocationData enrichLocationData(RawLocationData raw) {
        return enrichLocationData(raw, null, null, List.of());
    }

    /**
     * 원시 위치 데이터를 보강된 위치 데이터로 변환
     *
     * 등록된 집 좌표가 주어지면 각 방문 장소를 집/외출로 분류(isHome)한다.
     *
     * @param raw           앱에서 받은 원시 위치 데이터
     * @param homeLatitude  거주지 위도 (null이면 분류 생략 → 모두 외출)
     * @param homeLongitude 거주지 경도 (null이면 분류 생략 → 모두 외출)
     * @return 장소명, 주소, 날씨, 집/외출 분류가 추가된 위치 데이터
     */
    public LocationData enrichLocationData(RawLocationData raw, Double homeLatitude, Double homeLongitude) {
        return enrichLocationData(raw, homeLatitude, homeLongitude, List.of());
    }

    /**
     * 원시 위치 데이터를 보강된 위치 데이터로 변환 (확정된 루틴 방문 장소 매칭 포함)
     *
     * 등록된 집 좌표와 확정된 루틴 방문 장소(회사/병원 등) 좌표가 주어지면, 이미 이름을 아는
     * 장소는 역지오코딩을 생략하고 그 카테고리 라벨을 바로 채운다(최소 API 호출 + 최소 수집 원칙).
     *
     * @param raw                    앱에서 받은 원시 위치 데이터
     * @param homeLatitude           거주지 위도 (null이면 분류 생략 → 모두 외출)
     * @param homeLongitude          거주지 경도
     * @param confirmedRoutinePlaces 사용자가 확정한 루틴 방문 장소 목록 (비어있으면 매칭 생략)
     * @return 장소명(또는 루틴 카테고리), 주소, 날씨, 집/외출 분류가 추가된 위치 데이터
     */
    public LocationData enrichLocationData(RawLocationData raw, Double homeLatitude, Double homeLongitude,
                                            List<RoutinePlaceInfo> confirmedRoutinePlaces) {
        if (raw == null) {
            return null;
        }

        log.debug("위치 데이터 보강 시작 - 현재좌표: ({}, {}), 방문장소 수: {}, 집좌표: ({}, {})",
                raw.getCurrentLatitude(), raw.getCurrentLongitude(),
                raw.getVisitedPlaces() != null ? raw.getVisitedPlaces().size() : 0,
                homeLatitude, homeLongitude);

        String currentCity = null;
        if (raw.getCurrentLatitude() != null && raw.getCurrentLongitude() != null) {
            currentCity = geocodingService.getCityName(
                    raw.getCurrentLatitude(),
                    raw.getCurrentLongitude()
            );
        }

        List<VisitedPlace> enrichedPlaces = new ArrayList<>();
        if (raw.getVisitedPlaces() != null) {
            // 1. 집 / 루틴 매칭 / 미확인 세 갈래로 분류한다.
            //    - 집: 지오코딩/날씨 조회 불필요(이름을 이미 알고 있음 - 100% 낭비였던 부분)
            //    - 루틴 매칭: 사용자가 확정한 장소(회사/병원 등)라 지오코딩은 불필요하지만,
            //      날씨는 여전히 유효한 대화 소재이므로 조회한다.
            //    - 미확인: 체류 시간 상위 OUTING_ENRICHMENT_LIMIT곳만 지오코딩+날씨 조회 대상으로 삼는다
            //      (실제로 대화에 쓰이는 건 그중 체류 시간이 가장 긴 1곳뿐이지만, 다른 곳도 자연스럽게
            //      언급될 여지를 위해 소수는 남겨둔다 - PromptService.buildVisitedPlacesText 참고)
            List<RawVisitedPlace> homeVisits = new ArrayList<>();
            List<RawVisitedPlace> outingCandidates = new ArrayList<>();
            for (RawVisitedPlace rawPlace : raw.getVisitedPlaces()) {
                if (isAtHome(rawPlace.getLatitude(), rawPlace.getLongitude(), homeLatitude, homeLongitude)) {
                    homeVisits.add(rawPlace);
                    continue;
                }
                Optional<RoutinePlaceInfo> routineMatch = matchRoutinePlace(rawPlace, confirmedRoutinePlaces);
                if (routineMatch.isPresent()) {
                    enrichedPlaces.add(buildRoutineVisitedPlace(rawPlace, routineMatch.get()));
                } else {
                    outingCandidates.add(rawPlace);
                }
            }
            homeVisits.forEach(rawPlace -> enrichedPlaces.add(buildHomeVisitedPlace(rawPlace)));

            List<RawVisitedPlace> topOutings = outingCandidates.stream()
                    .sorted(Comparator.comparingInt(
                            (RawVisitedPlace p) -> p.getStayDurationMinutes() != null ? p.getStayDurationMinutes() : 0)
                            .reversed())
                    .limit(OUTING_ENRICHMENT_LIMIT)
                    .toList();
            topOutings.forEach(rawPlace -> enrichedPlaces.add(enrichOuting(rawPlace)));

            log.debug("방문 장소 처리 - 전체 {}곳 (집 {}곳, 미확인 외출 {}곳 중 상위 {}곳만 지오코딩/날씨 조회)",
                    raw.getVisitedPlaces().size(), homeVisits.size(), outingCandidates.size(), topOutings.size());
        }

        log.debug("위치 데이터 보강 완료 - currentCity: {}, 방문장소 수: {}, 총 이동거리: {}km",
                currentCity, enrichedPlaces.size(), raw.getTotalDistanceKm());

        return LocationData.builder()
                .currentCity(currentCity)
                .visitedPlaces(enrichedPlaces)
                .totalDistanceKm(raw.getTotalDistanceKm())
                .build();
    }

    /**
     * 방문 시점 날씨 조회를 위한 최소 체류 시간 (분)
     * - API 호출 최적화를 위해 30분 이상 체류한 장소만 날씨 조회
     * - 짧은 체류(편의점, 버스정류장 등)는 대화 주제로 부적합
     */
    private static final int MIN_STAY_DURATION_FOR_WEATHER = 30;

    /**
     * 집으로 판정된 방문 장소를 지오코딩/날씨 조회 없이 즉시 구성.
     * (PromptService.appendHomeStayLine은 애초에 placeName/address/weather를 쓰지 않는다)
     */
    private VisitedPlace buildHomeVisitedPlace(RawVisitedPlace raw) {
        return VisitedPlace.builder()
                .latitude(raw.getLatitude())
                .longitude(raw.getLongitude())
                .visitStartTime(raw.getVisitStartTime())
                .visitEndTime(raw.getVisitEndTime())
                .stayDurationMinutes(raw.getStayDurationMinutes())
                .isHome(true)
                .build();
    }

    /**
     * 외출 장소 정보 보강
     *
     * - 역지오코딩으로 장소명/주소 추가
     * - Timemachine API로 방문 시점 날씨 추가 (30분 이상 체류 시에만)
     *
     * 집 여부는 호출부(enrichLocationData)에서 이미 판정해 걸러낸 뒤 호출하므로 항상 isHome=false.
     */
    private VisitedPlace enrichOuting(RawVisitedPlace raw) {
        GeocodingResult result = geocodingService.reverseGeocode(
                raw.getLatitude(),
                raw.getLongitude()
        );
        VisitWeather visitWeather = resolveWeatherIfEligible(raw);

        log.debug("방문 장소 보강 완료 - placeName: {}, address: {}, 체류: {}분, 날씨: {}",
                result.getPlaceName(), result.getAddress(),
                raw.getStayDurationMinutes(),
                visitWeather != null ? visitWeather.getDescription() : "null");

        return VisitedPlace.builder()
                .placeName(result.getPlaceName())
                .address(result.getAddress())
                .weather(visitWeather)
                .latitude(raw.getLatitude())
                .longitude(raw.getLongitude())
                .visitStartTime(raw.getVisitStartTime())
                .visitEndTime(raw.getVisitEndTime())
                .stayDurationMinutes(raw.getStayDurationMinutes())
                .isHome(false)
                .build();
    }

    /**
     * 확정된 루틴 방문 장소와 매칭된 방문을 구성 - 이름을 이미 알고 있으므로 역지오코딩은 생략하고
     * 카테고리 라벨을 바로 채운다. 날씨는 여전히 유효한 대화 소재이므로 조회한다.
     */
    private VisitedPlace buildRoutineVisitedPlace(RawVisitedPlace raw, RoutinePlaceInfo routinePlace) {
        return VisitedPlace.builder()
                .routineCategory(routinePlace.getCategory())
                .weather(resolveWeatherIfEligible(raw))
                .latitude(raw.getLatitude())
                .longitude(raw.getLongitude())
                .visitStartTime(raw.getVisitStartTime())
                .visitEndTime(raw.getVisitEndTime())
                .stayDurationMinutes(raw.getStayDurationMinutes())
                .isHome(false)
                .build();
    }

    /**
     * 방문 시점 날씨 조회 (30분 이상 체류 시에만 API 호출 - 짧은 체류는 대화 주제로 부적합)
     */
    private VisitWeather resolveWeatherIfEligible(RawVisitedPlace raw) {
        Integer stayDuration = raw.getStayDurationMinutes();
        if (stayDuration == null || stayDuration < MIN_STAY_DURATION_FOR_WEATHER) {
            log.debug("방문 시점 날씨 조회 생략 - 체류 {}분 < {}분 (API 절약)", stayDuration, MIN_STAY_DURATION_FOR_WEATHER);
            return null;
        }
        VisitWeather visitWeather = weatherClient.getWeatherForVisit(
                raw.getLatitude(), raw.getLongitude(), raw.getVisitStartTime());
        log.debug("방문 시점 날씨 조회 - 체류 {}분 >= {}분, 날씨: {}",
                stayDuration, MIN_STAY_DURATION_FOR_WEATHER,
                visitWeather != null ? visitWeather.getDescription() : "null");
        return visitWeather;
    }

    /**
     * 방문 좌표가 확정된 루틴 방문 장소 중 하나의 반경 이내인지 판정.
     */
    private Optional<RoutinePlaceInfo> matchRoutinePlace(RawVisitedPlace raw, List<RoutinePlaceInfo> routinePlaces) {
        if (routinePlaces == null || routinePlaces.isEmpty()) {
            return Optional.empty();
        }
        return routinePlaces.stream()
                .filter(place -> GeoDistanceUtil.isWithinRadius(
                        raw.getLatitude(), raw.getLongitude(),
                        place.getLatitude(), place.getLongitude(),
                        place.getRadiusMeters() != null ? place.getRadiusMeters() : RoutinePlace.DEFAULT_RADIUS_METERS))
                .findFirst();
    }

    /**
     * 방문 좌표가 등록된 집 좌표의 반경(HOME_MATCH_RADIUS_METERS) 이내인지 판정.
     * 집 좌표나 방문 좌표가 없으면 false(분류 불가 → 외출로 취급).
     */
    private boolean isAtHome(Double visitLat, Double visitLng, Double homeLat, Double homeLng) {
        return GeoDistanceUtil.isWithinRadius(visitLat, visitLng, homeLat, homeLng, HOME_MATCH_RADIUS_METERS);
    }
}
 
