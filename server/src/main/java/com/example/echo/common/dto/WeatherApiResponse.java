package com.example.echo.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OpenWeatherMap API 응답 DTO
 *
 * API 응답 예시:
 * {
 *   "weather": [{"id": 800, "main": "Clear", "description": "맑음"}],
 *   "main": {"temp": 18.5, "feels_like": 17.2, "humidity": 45}
 * }
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeatherApiResponse {

    private List<Weather> weather;
    private Main main;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Weather {
        private String description;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Main {
        private Double temp;
    }

    /**
     * WeatherData로 변환
     */
    public WeatherData toWeatherData() {
        String description = (weather != null && !weather.isEmpty())
                ? weather.get(0).getDescription()
                : "알 수 없음";

        Integer temperature = (main != null && main.getTemp() != null)
                ? main.getTemp().intValue()
                : null;

        return WeatherData.builder()
                .description(description)
                .temperature(temperature)
                .build();
    }
}
