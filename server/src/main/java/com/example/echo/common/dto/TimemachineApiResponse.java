package com.example.echo.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

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
 *   "lat": 37.88,
 *   "lon": 127.73,
 *   "timezone": "Asia/Seoul",
 *   "data": [{
 *     "dt": 1711688400,
 *     "temp": 18.5,
 *     "humidity": 65,
 *     "weather": [{"id": 800, "main": "Clear", "description": "맑음"}],
 *     "rain": {"1h": 0.5}
 *   }]
 * }
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TimemachineApiResponse {

    private Double lat;
    private Double lon;
    private String timezone;
    private List<TimemachineData> data;

    /**
     * 시점별 날씨 데이터
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TimemachineData {

        /** Unix timestamp */
        private Long dt;

        /** 기온 (섭씨) */
        private Double temp;

        /** 습도 (%) */
        private Integer humidity;

        /** 날씨 정보 목록 */
        private List<Weather> weather;

        /** 강수량 (mm/h) - 비가 있을 때만 존재 */
        private Rain rain;

        /** 적설량 (mm/h) - 눈이 있을 때만 존재 */
        private Snow snow;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Weather {
        private Integer id;
        private String main;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Rain {
        @JsonProperty("1h")
        private Double oneHour;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Snow {
        @JsonProperty("1h")
        private Double oneHour;
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
        boolean hadRain = checkHadRain(weatherData);

        return VisitWeather.builder()
                .description(description)
                .temperature(temperature)
                .hadRain(hadRain)
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

    /**
     * 비 여부 확인
     * - rain 필드가 있거나
     * - weather.main이 Rain, Drizzle, Thunderstorm인 경우
     */
    private boolean checkHadRain(TimemachineData data) {
        // rain 필드 확인
        if (data.getRain() != null && data.getRain().getOneHour() != null
                && data.getRain().getOneHour() > 0) {
            return true;
        }

        // weather.main 확인
        if (data.getWeather() != null) {
            Set<String> rainTypes = Set.of("Rain", "Drizzle", "Thunderstorm");
            return data.getWeather().stream()
                    .anyMatch(w -> rainTypes.contains(w.getMain()));
        }

        return false;
    }
}
