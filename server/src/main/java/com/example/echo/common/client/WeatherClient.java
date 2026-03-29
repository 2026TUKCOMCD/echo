package com.example.echo.common.client;

import com.example.echo.common.dto.TimemachineApiResponse;
import com.example.echo.common.dto.VisitWeather;
import com.example.echo.common.dto.WeatherApiResponse;
import com.example.echo.common.dto.WeatherData;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 날씨 정보 조회 클라이언트
 *
 * OpenWeatherMap API를 사용하여 날씨 정보 조회
 * - 현재 날씨: Current Weather API (TTL 30분)
 * - 방문 시점 날씨: One Call API 3.0 Timemachine (TTL 24시간)
 * - 한국어 날씨 설명 지원
 * - 섭씨 온도 반환
 * - 위치 정보가 없으면 조회하지 않음 (null 반환)
 */
@Slf4j
@Component
public class WeatherClient {

    private final WeatherApiClient weatherApiClient;
    private final String apiKey;

    /**
     * 현재 날씨 캐시
     * - 키: 좌표 (소수점 1자리, 약 10km 범위)
     * - 값: WeatherData
     * - TTL: 30분 (실시간 날씨 변동)
     * - 최대 크기: 100개
     */
    private final Cache<String, WeatherData> currentWeatherCache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    /**
     * 방문 시점 날씨 캐시
     * - 키: 좌표(소수점 1자리, 약 10km) + 정시 timestamp
     * - 값: VisitWeather
     * - TTL: 24시간 (과거 날씨는 변하지 않음)
     * - 최대 크기: 500개
     */
    private final Cache<String, VisitWeather> visitWeatherCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofHours(24))
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

        // 캐시 키 생성 (소수점 1자리 = 약 10km 범위)
        String cacheKey = String.format("%.1f,%.1f", latitude, longitude);

        return currentWeatherCache.get(cacheKey, key -> {
            log.debug("현재 날씨 캐시 미스 - API 호출: {}", key);
            return callCurrentWeatherApi(latitude, longitude);
        });
    }

    /**
     * 방문 시점 날씨 조회 (캐시 적용)
     *
     * 방문 기록의 위치와 시작 시간을 기준으로 해당 시점의 날씨 조회
     * - One Call API 3.0 Timemachine 사용
     * - 캐시 키는 좌표 + 시간(hour)만 사용 (TTL 24시간)
     * - API 호출 시에만 오늘 날짜와 결합하여 timestamp 생성
     *
     * @param latitude 위도 (null이면 조회하지 않음)
     * @param longitude 경도 (null이면 조회하지 않음)
     * @param visitStartTime 방문 시작 시간 (null이면 조회하지 않음)
     * @return 방문 시점 날씨 정보, 위치/시간 정보가 없으면 null
     */
    public VisitWeather getWeatherForVisit(Double latitude, Double longitude, LocalTime visitStartTime) {
        // 필수 정보 검증
        if (latitude == null || longitude == null || visitStartTime == null) {
            log.debug("위치 또는 시간 정보가 없어 방문 날씨 조회를 건너뜁니다");
            return null;
        }

        int hour = visitStartTime.getHour();

        // 캐시 키 생성 (소수점 1자리 = 약 10km 범위 + 시간)
        String cacheKey = String.format("%.1f,%.1f:%d", latitude, longitude, hour);

        return visitWeatherCache.get(cacheKey, key -> {
            log.debug("방문 날씨 캐시 미스 - Timemachine API 호출: {}", key);

            // API 호출 시 오늘 날짜와 결합하여 timestamp 생성
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            LocalDateTime visitDateTime = LocalDateTime.of(today, LocalTime.of(hour, 0));
            long timestamp = visitDateTime.atZone(ZoneId.of("Asia/Seoul")).toEpochSecond();

            return callTimemachineApi(latitude, longitude, timestamp);
        });
    }

    /**
     * 현재 날씨 API 호출 (Current Weather API)
     */
    private WeatherData callCurrentWeatherApi(Double latitude, Double longitude) {
        try {
            log.debug("현재 날씨 조회 요청 - 위도: {}, 경도: {}", latitude, longitude);

            WeatherApiResponse response = weatherApiClient.getWeather(
                    latitude,
                    longitude,
                    apiKey,
                    "metric",  // 섭씨
                    "kr"       // 한국어
            );

            WeatherData weatherData = response.toWeatherData();
            log.info("현재 날씨 조회 성공 - {}, {}도", weatherData.getDescription(), weatherData.getTemperature());

            return weatherData;

        } catch (Exception e) {
            log.error("현재 날씨 조회 실패 - 위도: {}, 경도: {}, 오류: {}", latitude, longitude, e.getMessage());
            return null;
        }
    }

    /**
     * 방문 시점 날씨 API 호출 (One Call API 3.0 Timemachine)
     */
    private VisitWeather callTimemachineApi(Double latitude, Double longitude, long timestamp) {
        try {
            log.debug("방문 날씨 조회 요청 - 위도: {}, 경도: {}, timestamp: {}", latitude, longitude, timestamp);

            TimemachineApiResponse response = weatherApiClient.getTimemachineWeather(
                    latitude,
                    longitude,
                    timestamp,
                    apiKey,
                    "metric",  // 섭씨
                    "kr"       // 한국어
            );

            VisitWeather visitWeather = response.toVisitWeather();
            if (visitWeather != null) {
                log.info("방문 날씨 조회 성공 - {}, {}도",
                        visitWeather.getDescription(),
                        visitWeather.getTemperature());
            }

            return visitWeather;

        } catch (Exception e) {
            log.error("방문 날씨 조회 실패 - 위도: {}, 경도: {}, timestamp: {}, 오류: {}",
                    latitude, longitude, timestamp, e.getMessage());
            return null;
        }
    }

    /**
     * 현재 날씨 캐시 크기 조회 (테스트/디버깅용)
     */
    public long getCurrentWeatherCacheSize() {
        return currentWeatherCache.estimatedSize();
    }

    /**
     * 방문 날씨 캐시 크기 조회 (테스트/디버깅용)
     */
    public long getVisitWeatherCacheSize() {
        return visitWeatherCache.estimatedSize();
    }
}
