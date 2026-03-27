package com.example.echo.common.client;

import com.example.echo.common.dto.WeatherApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * OpenWeatherMap API 클라이언트
 *
 * OpenFeign을 사용한 선언적 HTTP 클라이언트
 * - 위도/경도 기반 현재 날씨 조회
 * - 한국어 응답 지원 (lang=kr)
 */
@FeignClient(
        name = "weather-api-client",
        url = "${weather.api.url}"
)
public interface WeatherApiClient {

    /**
     * 현재 날씨 조회
     *
     * @param latitude 위도
     * @param longitude 경도
     * @param apiKey API 키
     * @param units 온도 단위 (metric = 섭씨)
     * @param lang 응답 언어 (kr = 한국어)
     * @return 날씨 정보 (description, temperature)
     */
    @GetMapping("/data/2.5/weather")
    WeatherApiResponse getWeather(
            @RequestParam("lat") Double latitude,
            @RequestParam("lon") Double longitude,
            @RequestParam("appid") String apiKey,
            @RequestParam("units") String units,
            @RequestParam("lang") String lang
    );
}
