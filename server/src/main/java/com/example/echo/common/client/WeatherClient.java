package com.example.echo.common.client;

import com.example.echo.common.dto.WeatherData;
import org.springframework.stereotype.Component;

@Component
public class WeatherClient {

    public WeatherData getCurrentWeather() {
        // TODO: 실제 날씨 API 연동 시 구현
        return WeatherData.builder()
                .description("맑음")
                .temperature(18)
                .build();
    }
}