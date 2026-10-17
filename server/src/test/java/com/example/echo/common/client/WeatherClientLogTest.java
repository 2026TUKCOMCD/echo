package com.example.echo.common.client;

import com.example.echo.common.LogCapture;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * OpenWeatherMap은 API 키를 쿼리 파라미터(appid)로 받아서, Feign 예외 메시지(요청 URL 포함)를 그대로 로그에 남기면
 * API 키와 정확한 좌표가 함께 남는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WeatherClient - 실패 로그에 API 키·좌표를 남기지 않는다")
class WeatherClientLogTest {

    private static final String API_KEY = "SECRET-OWM-KEY-1234";
    private static final double LAT = 37.56654;
    private static final double LON = 126.97796;

    @Mock
    private WeatherApiClient weatherApiClient;

    private WeatherClient weatherClient;

    @BeforeEach
    void setUp() {
        weatherClient = new WeatherClient(weatherApiClient, API_KEY);
    }

    @Test
    @DisplayName("현재 날씨 조회 실패")
    void currentWeatherFailure_logsNoApiKeyOrCoordinates() {
        when(weatherApiClient.getWeather(any(), any(), any(), any(), any()))
                .thenThrow(feignError(500, "https://api.openweathermap.org/data/2.5/weather?lat=" + LAT
                        + "&lon=" + LON + "&appid=" + API_KEY + "&units=metric&lang=kr"));

        try (LogCapture logs = LogCapture.of(WeatherClient.class)) {
            assertThat(weatherClient.getCurrentWeather(LAT, LON)).isNull();

            assertThat(logs.allText())
                    .contains("500")
                    .doesNotContain(API_KEY)
                    .doesNotContain(String.valueOf(LAT))
                    .doesNotContain(String.valueOf(LON));
        }
    }

    @Test
    @DisplayName("방문 시점 날씨 조회 실패")
    void visitWeatherFailure_logsNoApiKeyOrCoordinates() {
        when(weatherApiClient.getTimemachineWeather(any(), any(), any(), any(), any(), any()))
                .thenThrow(feignError(500, "https://api.openweathermap.org/data/3.0/onecall/timemachine?lat=" + LAT
                        + "&lon=" + LON + "&dt=1760000000&appid=" + API_KEY + "&units=metric&lang=kr"));

        try (LogCapture logs = LogCapture.of(WeatherClient.class)) {
            assertThat(weatherClient.getWeatherForVisit(LAT, LON, LocalTime.of(10, 0))).isNull();

            assertThat(logs.allText())
                    .contains("500")
                    .doesNotContain(API_KEY)
                    .doesNotContain(String.valueOf(LAT))
                    .doesNotContain(String.valueOf(LON));
        }
    }

    /** 실제 Feign과 같은 형식의 예외 - 메시지에 요청 URL과 응답 본문이 들어간다 */
    private static FeignException feignError(int status, String url) {
        Request request = Request.create(Request.HttpMethod.GET, url, Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .status(status)
                .reason("Error")
                .request(request)
                .headers(Map.of())
                .body("{\"cod\":" + status + ",\"message\":\"internal error\"}", StandardCharsets.UTF_8)
                .build();
        return FeignException.errorStatus("WeatherApiClient#call", response);
    }
}
