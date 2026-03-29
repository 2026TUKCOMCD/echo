package com.example.echo.location.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawLocationData {

    private Double currentLatitude;
    private Double currentLongitude;
    private List<RawVisitedPlace> visitedPlaces;
    private Double totalDistanceKm;
}
