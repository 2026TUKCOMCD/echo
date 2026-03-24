package com.example.echo.location.service;

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
 * 원시 위치 데이터(RawLocationData) → 보강된 위치 데이터(LocationData) 변환
 *
 * GeocodingService를 통해 좌표를 장소명/주소로 변환한다.
 * 변환 결과는 ContextService에서 UserContext에 저장되어 대화 세션 동안 재사용된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final GeocodingService geocodingService;

    /**
     * RawLocationData → LocationData 변환
     *
     * @param raw 앱에서 전송된 원시 위치 데이터 (null 허용)
     * @return 보강된 위치 데이터, raw가 null이면 null 반환
     */
    public LocationData enrichLocationData(RawLocationData raw) {
        if (raw == null) {
            return null;
        }

        String currentCity = geocodingService.getCityName(
                raw.getCurrentLatitude(),
                raw.getCurrentLongitude()
        );

        List<VisitedPlace> enrichedPlaces = new ArrayList<>();
        if (raw.getVisitedPlaces() != null) {
            for (RawVisitedPlace rawPlace : raw.getVisitedPlaces()) {
                enrichedPlaces.add(enrichVisitedPlace(rawPlace));
            }
        }

        return LocationData.builder()
                .currentCity(currentCity)
                .visitedPlaces(enrichedPlaces)
                .totalDistanceKm(raw.getTotalDistanceKm())
                .build();
    }

    private VisitedPlace enrichVisitedPlace(RawVisitedPlace raw) {
        GeocodingResult result = geocodingService.reverseGeocode(
                raw.getLatitude(),
                raw.getLongitude()
        );

        return VisitedPlace.builder()
                .placeName(result.getPlaceName())
                .address(result.getAddress())
                .latitude(raw.getLatitude())
                .longitude(raw.getLongitude())
                .visitStartTime(raw.getVisitStartTime())
                .visitEndTime(raw.getVisitEndTime())
                .stayDurationMinutes(raw.getStayDurationMinutes())
                .build();
    }
}
