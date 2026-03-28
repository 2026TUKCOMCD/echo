package com.example.echo.common.client;

import com.example.echo.common.dto.TimemachineApiResponse;
import com.example.echo.common.dto.WeatherApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * OpenWeatherMap API 클라이언트
 *
 * OpenFeign을 사용한 선언적 HTTP 클라이언트
 * - 위도/경도 기반 현재 날씨 조회 (Current Weather API)
 * - 과거 특정 시점 날씨 조회 (One Call API 3.0 Timemachine)
 * - 한국어 응답 지원 (lang=kr)
 */
@FeignClient(
        name = "weather-api-client",
        url = "${weather.api.url}"
)
public interface WeatherApiClient {

    /**
     * 현재 날씨 조회 (Current Weather API)
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

    /**
     * 과거 특정 시점 날씨 조회 (One Call API 3.0 Timemachine)
     *
     * 방문 기록의 시작 시간에 해당하는 날씨를 조회할 때 사용
     * - One Call API 3.0 구독 필요
     * - 1979년 1월 1일부터 현재까지 조회 가능
     * - timestamp는 정시(00분)로 정규화하여 호출 권장 (캐싱 효율)
     *
     * @param latitude 위도
     * @param longitude 경도
     * @param timestamp Unix timestamp (정시 기준, 예: 14:00:00)
     * @param apiKey API 키
     * @param units 온도 단위 (metric = 섭씨)
     * @param lang 응답 언어 (kr = 한국어)
     * @return 해당 시점의 날씨 정보
     */
    @GetMapping("/data/3.0/onecall/timemachine")
    TimemachineApiResponse getTimemachineWeather(
            @RequestParam("lat") Double latitude,
            @RequestParam("lon") Double longitude,
            @RequestParam("dt") Long timestamp,
            @RequestParam("appid") String apiKey,
            @RequestParam("units") String units,
            @RequestParam("lang") String lang
    );
}
