package com.example.echo.location.service;

import com.example.echo.common.client.WeatherClient;
import com.example.echo.common.dto.VisitWeather;
import com.example.echo.location.dto.GeocodingResult;
import com.example.echo.location.dto.LocationData;
import com.example.echo.location.dto.RawLocationData;
import com.example.echo.location.dto.RawVisitedPlace;
import com.example.echo.location.dto.VisitedPlace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
    private static final double HOME_MATCH_RADIUS_METERS = 250.0;

    /**
     * 원시 위치 데이터를 보강된 위치 데이터로 변환 (집 좌표 없이 - 모두 외출로 취급)
     *
     * @param raw 앱에서 받은 원시 위치 데이터
     * @return 장소명, 주소, 날씨가 추가된 위치 데이터
     */
    public LocationData enrichLocationData(RawLocationData raw) {
        return enrichLocationData(raw, null, null);
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
            for (RawVisitedPlace rawPlace : raw.getVisitedPlaces()) {
                enrichedPlaces.add(enrichVisitedPlace(rawPlace, homeLatitude, homeLongitude));
            }
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
     * 방문 장소 정보 보강
     *
     * - 역지오코딩으로 장소명/주소 추가
     * - Timemachine API로 방문 시점 날씨 추가 (30분 이상 체류 시에만)
     * - 등록된 집 좌표가 있으면 집/외출 분류(isHome)
     */
    private VisitedPlace enrichVisitedPlace(RawVisitedPlace raw, Double homeLatitude, Double homeLongitude) {
        // 1. 역지오코딩
        GeocodingResult result = geocodingService.reverseGeocode(
                raw.getLatitude(),
                raw.getLongitude()
        );

        // 1-1. 집/외출 분류: 집 좌표가 등록돼 있고, 방문 좌표가 반경 이내이면 집
        boolean isHome = isAtHome(raw.getLatitude(), raw.getLongitude(), homeLatitude, homeLongitude);

        // 2. 방문 시점 날씨 조회 (30분 이상 체류 시에만 API 호출)
        VisitWeather visitWeather = null;
        Integer stayDuration = raw.getStayDurationMinutes();
        if (stayDuration != null && stayDuration >= MIN_STAY_DURATION_FOR_WEATHER) {
            visitWeather = weatherClient.getWeatherForVisit(
                    raw.getLatitude(),
                    raw.getLongitude(),
                    raw.getVisitStartTime()
            );
            log.debug("방문 시점 날씨 조회 - 체류 {}분 >= {}분, 날씨: {}",
                    stayDuration, MIN_STAY_DURATION_FOR_WEATHER,
                    visitWeather != null ? visitWeather.getDescription() : "null");
        } else {
            log.debug("방문 시점 날씨 조회 생략 - 체류 {}분 < {}분 (API 절약)",
                    stayDuration, MIN_STAY_DURATION_FOR_WEATHER);
        }

        log.debug("방문 장소 보강 완료 - placeName: {}, address: {}, 체류: {}분, 날씨: {}, 집: {}",
                result.getPlaceName(), result.getAddress(),
                raw.getStayDurationMinutes(),
                visitWeather != null ? visitWeather.getDescription() : "null",
                isHome);

        return VisitedPlace.builder()
                .placeName(result.getPlaceName())
                .address(result.getAddress())
                .weather(visitWeather)
                .latitude(raw.getLatitude())
                .longitude(raw.getLongitude())
                .visitStartTime(raw.getVisitStartTime())
                .visitEndTime(raw.getVisitEndTime())
                .stayDurationMinutes(raw.getStayDurationMinutes())
                .isHome(isHome)
                .build();
    }

    /**
     * 방문 좌표가 등록된 집 좌표의 반경(HOME_MATCH_RADIUS_METERS) 이내인지 판정.
     * 집 좌표나 방문 좌표가 없으면 false(분류 불가 → 외출로 취급).
     */
    private boolean isAtHome(Double visitLat, Double visitLng, Double homeLat, Double homeLng) {
        if (visitLat == null || visitLng == null || homeLat == null || homeLng == null) {
            return false;
        }
        double distance = haversineMeters(visitLat, visitLng, homeLat, homeLng);
        return distance <= HOME_MATCH_RADIUS_METERS;
    }

    /**
     * 두 좌표 사이의 거리(미터)를 Haversine 공식으로 계산.
     * 위경도 차이를 지구 곡률을 반영한 실제 지표면 거리로 변환한다.
     * (안드로이드 StayPointDetector와 동일한 방식)
     */
    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadiusMeters = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusMeters * c;
    }
}
 
