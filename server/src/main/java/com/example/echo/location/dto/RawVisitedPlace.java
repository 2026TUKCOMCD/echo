package com.example.echo.location.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Schema(description = "앱에서 전송하는 원시 방문 장소 정보")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawVisitedPlace {

    @Schema(description = "위도", example = "37.5172")
    private Double latitude;

    @Schema(description = "경도", example = "127.0473")
    private Double longitude;

    @Schema(description = "방문 시작 시간", example = "14:00:00", type = "string")
    private LocalTime visitStartTime;

    @Schema(description = "방문 종료 시간", example = "15:30:00", type = "string")
    private LocalTime visitEndTime;

    @Schema(description = "체류 시간 (분)", example = "90")
    private Integer stayDurationMinutes;
}
