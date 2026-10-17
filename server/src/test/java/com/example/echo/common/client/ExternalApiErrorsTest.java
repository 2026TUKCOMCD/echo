package com.example.echo.common.client;

import feign.FeignException;
import feign.Request;
import feign.Response;
import feign.RetryableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExternalApiErrors - 외부 API 실패 로그 요약")
class ExternalApiErrorsTest {

    private static final String URL = "https://api.openweathermap.org/data/2.5/weather?lat=37.56654&lon=126.97796&appid=SECRET-KEY";
    private static final Request REQUEST =
            Request.create(Request.HttpMethod.GET, URL, Map.of(), null, StandardCharsets.UTF_8, null);

    @Test
    @DisplayName("HTTP 오류는 예외 종류와 상태 코드만 남기고 URL·응답 본문은 남기지 않는다")
    void httpError_keepsStatusOnly() {
        Response response = Response.builder()
                .status(401).reason("Unauthorized").request(REQUEST).headers(Map.of())
                .body("{\"message\":\"Invalid API key\"}", StandardCharsets.UTF_8)
                .build();

        String summary = ExternalApiErrors.describe(FeignException.errorStatus("WeatherApiClient#getWeather", response));

        assertThat(summary).isEqualTo("Unauthorized (HTTP 401)");
    }

    @Test
    @DisplayName("타임아웃(메시지에 요청 URL이 들어감)은 예외 종류와 원인 종류만 남긴다")
    void timeout_keepsCauseTypeOnly() {
        RetryableException timeout = new RetryableException(-1, "Read timed out executing GET " + URL,
                Request.HttpMethod.GET, new SocketTimeoutException("Read timed out"), (Long) null, REQUEST);

        String summary = ExternalApiErrors.describe(timeout);

        assertThat(summary).isEqualTo("RetryableException - SocketTimeoutException");
    }

    @Test
    @DisplayName("Feign 예외가 아니면 예외 종류만 남긴다")
    void otherException_keepsTypeOnly() {
        assertThat(ExternalApiErrors.describe(new IllegalStateException("lat=37.56654"))).isEqualTo("IllegalStateException");
    }
}
