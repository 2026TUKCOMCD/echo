package com.example.echo.location.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "앱에서 전송하는 원시 위치 데이터")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawLocationData {

    @Schema(description = "현재 위도", example = "37.5665")
    private Double currentLatitude;

    @Schema(description = "현재 경도", example = "126.9780")
    private Double currentLongitude;

    @Schema(description = "방문 장소 목록")
    private List<RawVisitedPlace> visitedPlaces;

    @Schema(description = "총 이동 거리 (km)", example = "3.2")
    private Double totalDistanceKm;
}
