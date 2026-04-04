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
     * 원시 위치 데이터를 보강된 위치 데이터로 변환
     *
     * @param raw 앱에서 받은 원시 위치 데이터
     * @return 장소명, 주소, 날씨가 추가된 위치 데이터
     */
    public LocationData enrichLocationData(RawLocationData raw) {
        if (raw == null) {
            return null;
        }

        log.debug("위치 데이터 보강 시작 - 현재좌표: ({}, {}), 방문장소 수: {}",
                raw.getCurrentLatitude(), raw.getCurrentLongitude(),
                raw.getVisitedPlaces() != null ? raw.getVisitedPlaces().size() : 0);

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
                enrichedPlaces.add(enrichVisitedPlace(rawPlace));
            }
        }

        log.info("위치 데이터 보강 완료 - currentCity: {}, 방문장소 수: {}, 총 이동거리: {}km",
                currentCity, enrichedPlaces.size(), raw.getTotalDistanceKm());

        return LocationData.builder()
                .currentCity(currentCity)
                .visitedPlaces(enrichedPlaces)
                .totalDistanceKm(raw.getTotalDistanceKm())
                .build();
    }

    /**
     * 방문 장소 정보 보강
     *
     * - 역지오코딩으로 장소명/주소 추가
     * - Timemachine API로 방문 시점 날씨 추가
     */
    private VisitedPlace enrichVisitedPlace(RawVisitedPlace raw) {
        // 1. 역지오코딩
        GeocodingResult result = geocodingService.reverseGeocode(
                raw.getLatitude(),
                raw.getLongitude()
        );

        // 2. 방문 시점 날씨 조회 (Timemachine API)
        VisitWeather visitWeather = weatherClient.getWeatherForVisit(
                raw.getLatitude(),
                raw.getLongitude(),
                raw.getVisitStartTime()
        );

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
                .build();
    }
}
 
