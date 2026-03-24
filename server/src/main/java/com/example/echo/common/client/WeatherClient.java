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
 * - 위치 정보가 없으면 조회하지 않음 (null 반환)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherClient {

    private final WeatherApiClient weatherApiClient;

    @Value("${weather.api.key}")
    private String apiKey;

    /**
     * 현재 날씨 조회
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
}
