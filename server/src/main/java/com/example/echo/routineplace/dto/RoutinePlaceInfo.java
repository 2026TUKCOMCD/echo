package com.example.echo.routineplace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

/**
 * 대화 파이프라인(LocationService/PromptService)에서 쓰는 경량 확정 루틴 장소 정보.
 *
 * routineplace 엔티티를 모듈 밖으로 노출하지 않기 위한 DTO (location 패키지의
 * GeocodingResult/VisitedPlace와 동일한 컨벤션).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutinePlaceInfo {
    private Long id;
    private Double latitude;
    private Double longitude;
    private Double radiusMeters;
    private String category;
    private List<String> routineDays;
    private LocalTime routineTimeRangeStart;
    private LocalTime routineTimeRangeEnd;
}
