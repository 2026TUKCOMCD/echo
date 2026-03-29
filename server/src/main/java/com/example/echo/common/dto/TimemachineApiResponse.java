package com.example.echo.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OpenWeatherMap One Call API 3.0 Timemachine 응답 DTO
 *
 * 과거 특정 시점의 날씨 정보를 조회하는 Timemachine 엔드포인트 응답
 *
 * API 호출 예시:
 * GET /data/3.0/onecall/timemachine?lat={lat}&lon={lon}&dt={unix_timestamp}&appid={key}
 *
 * 응답 예시:
 * {
 *   "data": [{
 *     "temp": 18.5,
 *     "weather": [{"main": "Clear", "description": "맑음"}]
 *   }]
 * }
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TimemachineApiResponse {

    private List<TimemachineData> data;

    /**
     * 시점별 날씨 데이터
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TimemachineData {

        /** 기온 (섭씨) */
        private Double temp;

        /** 날씨 정보 목록 */
        private List<Weather> weather;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Weather {
        private String description;
    }

    /**
     * VisitWeather로 변환
     *
     * @return 방문 시간대 날씨 정보
     */
    public VisitWeather toVisitWeather() {
        if (data == null || data.isEmpty()) {
            return null;
        }

        TimemachineData weatherData = data.get(0);

        String description = extractDescription(weatherData);
        Integer temperature = weatherData.getTemp() != null
                ? weatherData.getTemp().intValue()
                : null;

        return VisitWeather.builder()
                .description(description)
                .temperature(temperature)
                .build();
    }

    /**
     * 날씨 설명 추출
     */
    private String extractDescription(TimemachineData data) {
        if (data.getWeather() == null || data.getWeather().isEmpty()) {
            return "알 수 없음";
        }
        return data.getWeather().get(0).getDescription();
    }
}
