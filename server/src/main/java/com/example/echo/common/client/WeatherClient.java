package com.example.echo.common.client;

import com.example.echo.common.dto.WeatherApiResponse;
import com.example.echo.common.dto.WeatherData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 날씨 정보 조회 클라이언트
 *
 * OpenWeatherMap API를 사용하여 현재 날씨 정보 조회
 * - 한국어 날씨 설명 지원
 * - 섭씨 온도 반환
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherClient {

    private final WeatherApiClient weatherApiClient;

    @Value("${weather.api.key}")
    private String apiKey;

    // 기본 위치: 서울 (위치 파라미터는 #234에서 추가 예정)
    private static final Double DEFAULT_LATITUDE = 37.5665;
    private static final Double DEFAULT_LONGITUDE = 126.9780;

    /**
     * 현재 날씨 조회 (기본 위치: 서울)
     *
     * @return 날씨 정보 (description, temperature)
     */
    public WeatherData getCurrentWeather() {
        return getWeatherByLocation(DEFAULT_LATITUDE, DEFAULT_LONGITUDE);
    }

    /**
     * 특정 위치의 현재 날씨 조회
     *
     * @param latitude 위도
     * @param longitude 경도
     * @return 날씨 정보 (description, temperature)
     */
    public WeatherData getWeatherByLocation(Double latitude, Double longitude) {
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
            // 실패 시 기본값 반환
            return WeatherData.builder()
                    .description("알 수 없음")
                    .temperature(null)
                    .build();
        }
    }
}
