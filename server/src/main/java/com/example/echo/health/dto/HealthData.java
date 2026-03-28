package com.example.echo.health.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Schema(description = "건강 데이터 (Health Connect에서 수집)")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthData {

    @Schema(description = "오늘 걸음 수", example = "5000")
    private Integer steps;

    @Schema(description = "수면 시간 (분)", example = "420")
    private Integer sleepDurationMinutes;

    @Schema(description = "취침 시간", example = "23:00:00", type = "string")
    private LocalTime sleepStartTime;

    @Schema(description = "기상 시간", example = "07:00:00", type = "string")
    private LocalTime wakeUpTime;

    @Schema(description = "운동 거리 (km)", example = "2.5")
    private Double exerciseDistanceKm;

    @Schema(description = "운동 활동명", example = "산책")
    private String exerciseActivity;

    @Schema(description = "활동 목록 (쉼표 구분)", example = "산책,스트레칭")
    private String activityList;
}
