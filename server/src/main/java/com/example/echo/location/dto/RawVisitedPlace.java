package com.example.echo.location.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Schema(description = "원시 방문 장소 정보 (앱에서 전송)")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawVisitedPlace {

    @Schema(description = "방문 장소 위도", example = "37.8813")
    private Double latitude;

    @Schema(description = "방문 장소 경도", example = "127.7298")
    private Double longitude;

    @Schema(description = "방문 시작 시간", example = "10:00:00", type = "string")
    private LocalTime visitStartTime;

    @Schema(description = "방문 종료 시간", example = "11:30:00", type = "string")
    private LocalTime visitEndTime;

    @Schema(description = "체류 시간 (분)", example = "90")
    private Integer stayDurationMinutes;
}
