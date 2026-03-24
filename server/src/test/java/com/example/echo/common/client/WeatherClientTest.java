package com.example.echo.common.client;

import com.example.echo.common.dto.WeatherData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("local")
class WeatherClientTest {

    @Autowired
    private WeatherClient weatherClient;

    @Test
    @DisplayName("기본 위치(서울) 날씨 조회 성공")
    void getCurrentWeather_success() {
        // when
        WeatherData weatherData = weatherClient.getCurrentWeather();

        // then
        System.out.println("=== 날씨 조회 결과 ===");
        System.out.println("WeatherData: " + weatherData);
        System.out.println("날씨: " + (weatherData != null ? weatherData.getDescription() : "null"));
        System.out.println("온도: " + (weatherData != null ? weatherData.getTemperature() : "null"));

        assertThat(weatherData).isNotNull();
        assertThat(weatherData.getDescription()).isNotNull();
        assertThat(weatherData.getTemperature()).isNotNull();
    }

    @Test
    @DisplayName("특정 위치(부산) 날씨 조회 성공")
    void getWeatherByLocation_success() {
        // given - 부산 좌표
        Double latitude = 35.1796;
        Double longitude = 129.0756;

        // when
        WeatherData weatherData = weatherClient.getWeatherByLocation(latitude, longitude);

        // then
        assertThat(weatherData).isNotNull();
        assertThat(weatherData.getDescription()).isNotNull();
        assertThat(weatherData.getTemperature()).isNotNull();

        System.out.println("=== 부산 날씨 조회 결과 ===");
        System.out.println("날씨: " + weatherData.getDescription());
        System.out.println("온도: " + weatherData.getTemperature() + "도");
    }
}
