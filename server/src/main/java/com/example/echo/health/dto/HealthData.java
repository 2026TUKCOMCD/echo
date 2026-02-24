package com.example.echo.health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthData {
    private Integer steps;
    private Integer sleepDurationMinutes;
    private LocalTime sleepStartTime;
    private LocalTime wakeUpTime;
    private Double exerciseDistanceKm;
    private String exerciseActivity;
    private String activityList;
}

