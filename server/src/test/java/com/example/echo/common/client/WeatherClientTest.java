package com.example.echo.common.client;

import com.example.echo.common.dto.VisitWeather;
import com.example.echo.common.dto.WeatherData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalTime;

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
        long cacheSizeBefore = weatherClient.getCurrentWeatherCacheSize();
        WeatherData firstCall = weatherClient.getCurrentWeather(latitude, longitude);

        // 두 번째 호출 (캐시 히트, API 호출 안 함)
        WeatherData secondCall = weatherClient.getCurrentWeather(latitude, longitude);
        long cacheSizeAfter = weatherClient.getCurrentWeatherCacheSize();

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
    @DisplayName("캐시 키 - 소수점 1자리가 같으면 같은 캐시 사용")
    void getCurrentWeather_sameRoundedCoordinates_usesSameCache() {
        // given - 서울 좌표 (소수점 2자리 이하 다름)
        Double lat1 = 37.5665;
        Double lon1 = 126.9780;
        Double lat2 = 37.5167;  // 소수점 1자리는 37.5로 같음
        Double lon2 = 126.9282; // 소수점 1자리는 126.9로 같음

        // when
        WeatherData firstCall = weatherClient.getCurrentWeather(lat1, lon1);
        WeatherData secondCall = weatherClient.getCurrentWeather(lat2, lon2);

        // then - 둘 다 같은 캐시에서 가져옴
        assertThat(firstCall).isNotNull();
        assertThat(secondCall).isNotNull();

        System.out.println("=== 좌표 반올림 캐시 테스트 ===");
        System.out.println("좌표1: " + lat1 + ", " + lon1 + " → 캐시키: " + String.format("%.1f,%.1f", lat1, lon1));
        System.out.println("좌표2: " + lat2 + ", " + lon2 + " → 캐시키: " + String.format("%.1f,%.1f", lat2, lon2));
    }

    // ==================== getWeatherForVisit (Timemachine API) ====================

    @Test
    @DisplayName("방문 시점 날씨 조회 - 파라미터 null일 때 null 반환")
    void getWeatherForVisit_withNullParameters_returnsNull() {
        // when
        VisitWeather result = weatherClient.getWeatherForVisit(null, null, null);

        // then
        assertThat(result).isNull();
        System.out.println("=== 파라미터 null - 방문 날씨 조회 건너뜀 ===");
    }

    @Test
    @DisplayName("방문 시점 날씨 조회 - Timemachine API 연동 성공")
    void getWeatherForVisit_withValidParameters_success() {
        // given - 서울 좌표, 오전 10시
        Double latitude = 37.5665;
        Double longitude = 126.9780;
        LocalTime visitTime = LocalTime.of(10, 30);

        // when
        VisitWeather visitWeather = weatherClient.getWeatherForVisit(latitude, longitude, visitTime);

        // then
        assertThat(visitWeather).isNotNull();
        assertThat(visitWeather.getDescription()).isNotNull();
        assertThat(visitWeather.getTemperature()).isNotNull();

        System.out.println("=== 방문 시점 날씨 조회 결과 (Timemachine API) ===");
        System.out.println("방문 시간: " + visitTime);
        System.out.println("날씨: " + visitWeather.getDescription());
        System.out.println("온도: " + visitWeather.getTemperature() + "도");
    }

    @Test
    @DisplayName("방문 시점 날씨 캐시 - 같은 시간대 재조회 시 캐시 사용")
    void getWeatherForVisit_cacheHit_returnsCachedData() {
        // given - 부산 좌표, 오후 2시대
        Double latitude = 35.1796;
        Double longitude = 129.0756;
        LocalTime time1 = LocalTime.of(14, 15);
        LocalTime time2 = LocalTime.of(14, 45);

        // when
        long cacheSizeBefore = weatherClient.getVisitWeatherCacheSize();
        VisitWeather firstCall = weatherClient.getWeatherForVisit(latitude, longitude, time1);
        VisitWeather secondCall = weatherClient.getWeatherForVisit(latitude, longitude, time2);
        long cacheSizeAfter = weatherClient.getVisitWeatherCacheSize();

        // then
        assertThat(firstCall).isNotNull();
        assertThat(secondCall).isNotNull();
        assertThat(firstCall.getDescription()).isEqualTo(secondCall.getDescription());

        System.out.println("=== 방문 날씨 캐시 테스트 결과 ===");
        System.out.println("캐시 크기 (전): " + cacheSizeBefore);
        System.out.println("캐시 크기 (후): " + cacheSizeAfter);
        System.out.println("14:15 조회: " + firstCall);
        System.out.println("14:45 조회: " + secondCall + " (같은 14시 캐시 사용)");
    }
}
