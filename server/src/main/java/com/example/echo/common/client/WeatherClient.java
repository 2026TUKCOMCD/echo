package com.example.echo.common.client;

import com.example.echo.common.dto.WeatherApiResponse;
import com.example.echo.common.dto.WeatherData;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 날씨 정보 조회 클라이언트
 *
 * OpenWeatherMap API를 사용하여 현재 날씨 정보 조회
 * - 한국어 날씨 설명 지원
 * - 섭씨 온도 반환
 * - 위치 정보가 없으면 조회하지 않음 (null 반환)
 * - Caffeine 캐시로 API 호출 최소화 (TTL 30분)
 */
@Slf4j
@Component
public class WeatherClient {

    private final WeatherApiClient weatherApiClient;
    private final String apiKey;

    /**
     * 날씨 캐시
     * - 키: 좌표 (소수점 2자리, 약 1km 범위)
     * - 값: WeatherData
     * - TTL: 30분 (날씨는 자주 변경됨)
     * - 최대 크기: 100개
     */
    private final Cache<String, WeatherData> weatherCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    public WeatherClient(WeatherApiClient weatherApiClient,
                         @Value("${weather.api.key}") String apiKey) {
        this.weatherApiClient = weatherApiClient;
        this.apiKey = apiKey;
    }

    /**
     * 현재 날씨 조회 (캐시 적용)
     *
     * @param latitude 위도 (null이면 조회하지 않음)
     * @param longitude 경도 (null이면 조회하지 않음)
     * @return 날씨 정보 (description, temperature), 위치 정보가 없으면 null
     */
    public WeatherData getCurrentWeather(Double latitude, Double longitude) {
        // 위치 정보가 없으면 조회하지 않음
        if (latitude == null || longitude == null) {
            log.debug("위치 정보가 없어 날씨 조회를 건너뜁니다");
            return null;
        }

        // 캐시 키 생성 (소수점 2자리 = 약 1km 범위)
        String cacheKey = String.format("%.2f,%.2f", latitude, longitude);

        return weatherCache.get(cacheKey, key -> {
            log.debug("캐시 미스 - 날씨 API 호출: {}", key);
            return callWeatherApi(latitude, longitude);
        });
    }

    /**
     * 실제 날씨 API 호출
     */
    private WeatherData callWeatherApi(Double latitude, Double longitude) {
        try {
            log.debug("날씨 조회 요청 - 위도: {}, 경도: {}", latitude, longitude);

            WeatherApiResponse response = weatherApiClient.getWeather(
                    latitude,
                    longitude,
                    apiKey,
                    "metric",  // 섭씨
                    "kr"       // 한국어
            );

            WeatherData weatherData = response.toWeatherData();
            log.info("날씨 조회 성공 - {}, {}도", weatherData.getDescription(), weatherData.getTemperature());

            return weatherData;

        } catch (Exception e) {
            log.error("날씨 조회 실패 - 위도: {}, 경도: {}, 오류: {}", latitude, longitude, e.getMessage());
            return null;
        }
    }

    /**
     * 캐시 통계 조회 (테스트/디버깅용)
     */
    public long getCacheSize() {
        return weatherCache.estimatedSize();
    }
}
