package com.example.echo.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 방문 시간대의 날씨 정보
 *
 * StayPointDetector로 결정된 방문 기록의 위치와 시간에 해당하는 날씨
 * Timemachine API를 통해 과거 특정 시점의 날씨를 조회하여 생성
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitWeather {

    /**
     * 날씨 설명
     * - 예: "맑음", "흐림", "비", "눈"
     */
    private String description;

    /**
     * 기온 (섭씨)
     */
    private Integer temperature;
}
