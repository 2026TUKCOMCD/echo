package com.example.echo.location.dto;

import com.example.echo.common.dto.VisitWeather;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * 서버 내부에서 사용하는 보강된 방문 장소 정보
 *
 * 원시 좌표(latitude/longitude)로부터 장소명/주소/날씨 정보를 추가한 형태
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitedPlace {

    // ===== 보강된 장소 정보 =====
    /** 장소명 (좌표 → 역지오코딩) */
    private String placeName;

    /** 주소 (좌표 → 역지오코딩) */
    private String address;

    /** 방문 시점 날씨 (Timemachine API) */
    private VisitWeather weather;

    // ===== 원시 데이터 (RawVisitedPlace에서 복사) =====
    private Double latitude;
    private Double longitude;

    @Schema(type = "string")
    private LocalTime visitStartTime;

    @Schema(type = "string")
    private LocalTime visitEndTime;

    private Integer stayDurationMinutes;
}
