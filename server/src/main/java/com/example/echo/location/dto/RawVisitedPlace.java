package com.example.echo.location.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawVisitedPlace {

    private Double latitude;
    private Double longitude;
    private LocalTime visitStartTime;
    private LocalTime visitEndTime;
    private Integer stayDurationMinutes;
}
