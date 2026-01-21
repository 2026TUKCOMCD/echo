package com.example.echo.health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthData {
    private Integer steps;
    private Integer sleepDurationMinutes;
    private Double exerciseDistanceKm;
    private String exerciseActivity;
}