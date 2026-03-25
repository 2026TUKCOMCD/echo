package com.example.echo.location.service;

import com.example.echo.location.client.GeocodingClient;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final GeocodingClient geocodingClient;

    public LocationData enrichLocationData(RawLocationData raw) {
        if (raw == null) {
            return null;
        }

        String currentCity = geocodingClient.getCityName(
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
        GeocodingResult result = geocodingClient.reverseGeocode(
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
