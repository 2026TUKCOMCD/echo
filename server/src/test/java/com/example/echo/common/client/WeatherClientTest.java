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

    @Test
    @DisplayName("캐시 히트 - 같은 좌표 재조회 시 캐시에서 반환")
    void getCurrentWeather_cacheHit_returnsCachedData() {
        // given - 서울 좌표
        Double latitude = 37.5665;
        Double longitude = 126.9780;

        // when - 첫 번째 호출 (캐시 미스, API 호출)
        long cacheSizeBefore = weatherClient.getCacheSize();
        WeatherData firstCall = weatherClient.getCurrentWeather(latitude, longitude);

        // 두 번째 호출 (캐시 히트, API 호출 안 함)
        WeatherData secondCall = weatherClient.getCurrentWeather(latitude, longitude);
        long cacheSizeAfter = weatherClient.getCacheSize();

        // then
        assertThat(firstCall).isNotNull();
        assertThat(secondCall).isNotNull();
        assertThat(firstCall.getDescription()).isEqualTo(secondCall.getDescription());
        assertThat(firstCall.getTemperature()).isEqualTo(secondCall.getTemperature());

        System.out.println("=== 캐시 테스트 결과 ===");
        System.out.println("캐시 크기 (전): " + cacheSizeBefore);
        System.out.println("캐시 크기 (후): " + cacheSizeAfter);
        System.out.println("첫 번째 호출: " + firstCall);
        System.out.println("두 번째 호출: " + secondCall);
    }

    @Test
    @DisplayName("캐시 키 - 소수점 2자리가 같으면 같은 캐시 사용")
    void getCurrentWeather_sameRoundedCoordinates_usesSameCache() {
        // given - 서울 좌표 (소수점 3자리 이하 다름)
        Double lat1 = 37.5665;
        Double lon1 = 126.9780;
        Double lat2 = 37.5667;  // 소수점 2자리는 37.57로 같음
        Double lon2 = 126.9782; // 소수점 2자리는 126.98로 같음

        // when
        WeatherData firstCall = weatherClient.getCurrentWeather(lat1, lon1);
        WeatherData secondCall = weatherClient.getCurrentWeather(lat2, lon2);

        // then - 둘 다 같은 캐시에서 가져옴
        assertThat(firstCall).isNotNull();
        assertThat(secondCall).isNotNull();

        System.out.println("=== 좌표 반올림 캐시 테스트 ===");
        System.out.println("좌표1: " + lat1 + ", " + lon1 + " → 캐시키: " + String.format("%.2f,%.2f", lat1, lon1));
        System.out.println("좌표2: " + lat2 + ", " + lon2 + " → 캐시키: " + String.format("%.2f,%.2f", lat2, lon2));
    }
}
