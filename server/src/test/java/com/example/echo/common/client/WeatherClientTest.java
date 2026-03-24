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
    @DisplayName("위치 파라미터 null일 때 날씨 조회하지 않고 null 반환")
    void getCurrentWeather_withNullLocation_returnsNull() {
        // when - null 전달 시 조회하지 않음
        WeatherData weatherData = weatherClient.getCurrentWeather(null, null);

        // then
        assertThat(weatherData).isNull();
        System.out.println("=== 위치 정보 없음 - 날씨 조회 건너뜀 ===");
    }

    @Test
    @DisplayName("특정 위치(부산) 날씨 조회 성공")
    void getCurrentWeather_withLocation_success() {
        // given - 부산 좌표
        Double latitude = 35.1796;
        Double longitude = 129.0756;

        // when
        WeatherData weatherData = weatherClient.getCurrentWeather(latitude, longitude);

        // then
        assertThat(weatherData).isNotNull();
        assertThat(weatherData.getDescription()).isNotNull();
        assertThat(weatherData.getTemperature()).isNotNull();

        System.out.println("=== 부산 날씨 조회 결과 ===");
        System.out.println("날씨: " + weatherData.getDescription());
        System.out.println("온도: " + weatherData.getTemperature() + "도");
    }
}
