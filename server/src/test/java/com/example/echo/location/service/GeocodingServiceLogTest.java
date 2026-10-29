package com.example.echo.location.service;

import com.example.echo.common.LogCapture;
import com.example.echo.location.client.GeocodingClient;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** 카카오 역지오코딩 요청 URL에는 좌표(x, y)가 들어 있어, Feign 예외 메시지를 그대로 남기면 위치가 로그에 남는다 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GeocodingService - 실패 로그에 좌표를 남기지 않는다")
class GeocodingServiceLogTest {

    private static final double LAT = 37.56654;
    private static final double LON = 126.97796;

    @Mock
    private GeocodingClient geocodingClient;

    private GeocodingService geocodingService;

    @BeforeEach
    void setUp() {
        geocodingService = new GeocodingService(geocodingClient);
        when(geocodingClient.reverseGeocode(anyDouble(), anyDouble(), anyString())).thenThrow(feignError());
    }

    @Test
    @DisplayName("현재 위치 역지오코딩 실패")
    void cityNameFailure_logsNoCoordinates() {
        try (LogCapture logs = LogCapture.of(GeocodingService.class)) {
            assertThat(geocodingService.getCityName(LAT, LON)).isNull();

            assertThat(logs.allText())
                    .contains("429")
                    .doesNotContain(String.valueOf(LAT))
                    .doesNotContain(String.valueOf(LON));
        }
    }

    @Test
    @DisplayName("방문 장소 역지오코딩 실패")
    void visitPlaceFailure_logsNoCoordinates() {
        try (LogCapture logs = LogCapture.of(GeocodingService.class)) {
            assertThat(geocodingService.reverseGeocode(LAT, LON).getAddress()).isNull();

            assertThat(logs.allText())
                    .contains("429")
                    .doesNotContain(String.valueOf(LAT))
                    .doesNotContain(String.valueOf(LON));
        }
    }

    private static FeignException feignError() {
        String url = "https://dapi.kakao.com/v2/local/geo/coord2address.json?x=" + LON + "&y=" + LAT + "&input_coord=WGS84";
        Request request = Request.create(Request.HttpMethod.GET, url, Map.of(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .status(429)
                .reason("Too Many Requests")
                .request(request)
                .headers(Map.of())
                .body("{\"errorType\":\"RequestThrottled\",\"message\":\"API limit has been exceeded.\"}", StandardCharsets.UTF_8)
                .build();
        return FeignException.errorStatus("GeocodingClient#reverseGeocode", response);
    }
}
