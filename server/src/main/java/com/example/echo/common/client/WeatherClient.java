package com.example.echo.common.client;

import com.example.echo.common.dto.TimemachineApiResponse;
import com.example.echo.common.dto.VisitWeather;
import com.example.echo.common.dto.WeatherApiResponse;
import com.example.echo.common.dto.WeatherData;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
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

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final WeatherApiClient weatherApiClient;
    private final String apiKey;
    // 방문 날씨의 "오늘" 기준 - Asia/Seoul 고정 Clock(SchedulingConfig)
    private final Clock clock;

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
     * - 키: 좌표(소수점 1자리, 약 10km) + 날짜 + 시(hour) - 날짜가 없으면 TTL 안에서 다음 날 같은 시간대에 전날 날씨가 나온다
     * - 값: VisitWeather
     * - TTL: 24시간 (과거 날씨는 변하지 않음)
     * - 최대 크기: 500개
     */
    private final Cache<String, VisitWeather> visitWeatherCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofHours(24))
            .build();

    /**
     * 사용자별 날씨 캐시 (홈 화면 ↔ 대화 프롬프트 일관성 보장)
     * - 키: userId
     * - 값: WeatherData (홈 화면에서 조회한 날씨)
     * - TTL: 30분
     * - 최대 크기: 1000명
     */
    private final Cache<Long, WeatherData> userWeatherCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

    @Autowired
    public WeatherClient(WeatherApiClient weatherApiClient,
                         @Value("${weather.api.key}") String apiKey,
                         Clock clock) {
        this.weatherApiClient = weatherApiClient;
        this.apiKey = apiKey;
        this.clock = clock;
    }

    /** Clock 없이 만들면 한국 시간 시스템 시계를 쓴다 */
    public WeatherClient(WeatherApiClient weatherApiClient, String apiKey) {
        this(weatherApiClient, apiKey, Clock.system(KST));
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
     * - 캐시 키는 좌표 + 오늘 날짜(Asia/Seoul) + 시간(hour) (TTL 24시간)
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
        LocalDate today = LocalDate.now(clock);

        // 캐시 키 생성 (소수점 1자리 = 약 10km 범위 + 날짜 + 시간)
        String cacheKey = String.format("%.1f,%.1f:%s:%d", latitude, longitude, today, hour);

        return visitWeatherCache.get(cacheKey, key -> {
            log.debug("방문 날씨 캐시 미스 - Timemachine API 호출: {}", key);

            LocalDateTime visitDateTime = LocalDateTime.of(today, LocalTime.of(hour, 0));
            long timestamp = visitDateTime.atZone(clock.getZone()).toEpochSecond();

            return callTimemachineApi(latitude, longitude, timestamp);
        });
    }

    /**
     * 현재 날씨 API 호출 (Current Weather API)
     */
    private WeatherData callCurrentWeatherApi(Double latitude, Double longitude) {
        try {
            log.debug("현재 날씨 조회 요청");

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
            log.error("현재 날씨 조회 실패 - 오류: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 방문 시점 날씨 API 호출 (One Call API 3.0 Timemachine)
     *
     * API 에러 핸들링:
     * - 429 (Too Many Requests): 일일 한도 초과 경고
     * - 401/403 (Unauthorized/Forbidden): One Call API 3.0 구독 필요 경고
     * - 기타 에러: 일반 오류 로그
     */
    private VisitWeather callTimemachineApi(Double latitude, Double longitude, long timestamp) {
        try {
            log.debug("방문 날씨 조회 요청 - timestamp: {}", timestamp);

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

        } catch (FeignException.TooManyRequests e) {
            log.error("⚠️ OpenWeatherMap API 한도 초과! 관리자 확인 필요. " +
                    "일일 무료 한도: 1000회.");
            return null;
        } catch (FeignException.Unauthorized e) {
            log.error("⚠️ OpenWeatherMap API 인증 실패! API 키를 확인하세요.");
            return null;
        } catch (FeignException.Forbidden e) {
            log.error("⚠️ One Call API 3.0 구독이 필요합니다! " +
                    "OpenWeatherMap 콘솔에서 구독 상태를 확인하세요. " +
                    "https://home.openweathermap.org/subscriptions");
            return null;
        } catch (FeignException e) {
            log.error("방문 날씨 조회 실패 - timestamp: {}, HTTP 상태: {}, 오류: {}",
                    timestamp, e.status(), e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("방문 날씨 조회 실패 - timestamp: {}, 오류: {}",
                    timestamp, e.getMessage());
            return null;
        }
    }

    public void cacheUserWeather(Long userId, WeatherData weather) {
        if (userId != null && weather != null) {
            userWeatherCache.put(userId, weather);
        }
    }

    public WeatherData getCachedUserWeather(Long userId) {
        return userId == null ? null : userWeatherCache.getIfPresent(userId);
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
