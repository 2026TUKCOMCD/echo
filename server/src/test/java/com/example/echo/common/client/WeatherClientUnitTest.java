package com.example.echo.common.client;

import com.example.echo.common.dto.TimemachineApiResponse;
import com.example.echo.common.dto.VisitWeather;
import com.example.echo.common.dto.WeatherApiResponse;
import com.example.echo.common.dto.WeatherData;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * WeatherClient 단위 테스트
 *
 * Mock을 사용하여 외부 API 의존성 제거
 */
@ExtendWith(MockitoExtension.class)
class WeatherClientUnitTest {

    @Mock
    private WeatherApiClient weatherApiClient;

    private WeatherClient weatherClient;

    private static final String API_KEY = "test-api-key";

    @BeforeEach
    void setUp() {
        weatherClient = new WeatherClient(weatherApiClient, API_KEY);
    }

    @Nested
    @DisplayName("getCurrentWeather 메서드")
    class GetCurrentWeather {

        @Test
        @DisplayName("성공: 정상 좌표로 날씨 조회")
        void success_withValidCoordinates() {
            // given
            Double latitude = 37.8813;
            Double longitude = 127.7298;
            WeatherApiResponse mockResponse = createMockResponse("맑음", 20.5);

            given(weatherApiClient.getWeather(any(), any(), anyString(), anyString(), anyString()))
                    .willReturn(mockResponse);

            // when
            WeatherData result = weatherClient.getCurrentWeather(latitude, longitude);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getDescription()).isEqualTo("맑음");
            assertThat(result.getTemperature()).isEqualTo(20);

            then(weatherApiClient).should(times(1))
                    .getWeather(latitude, longitude, API_KEY, "metric", "kr");
        }

        @Test
        @DisplayName("성공: 위도가 null이면 API 호출하지 않고 null 반환")
        void success_withNullLatitude_returnsNull() {
            // when
            WeatherData result = weatherClient.getCurrentWeather(null, 127.0);

            // then
            assertThat(result).isNull();
            then(weatherApiClient).should(never()).getWeather(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("성공: 경도가 null이면 API 호출하지 않고 null 반환")
        void success_withNullLongitude_returnsNull() {
            // when
            WeatherData result = weatherClient.getCurrentWeather(37.5, null);

            // then
            assertThat(result).isNull();
            then(weatherApiClient).should(never()).getWeather(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("성공: API 실패 시 null 반환")
        void success_apiFailure_returnsNull() {
            // given
            Double latitude = 37.5665;
            Double longitude = 126.9780;

            given(weatherApiClient.getWeather(any(), any(), anyString(), anyString(), anyString()))
                    .willThrow(new RuntimeException("API 서버 오류"));

            // when
            WeatherData result = weatherClient.getCurrentWeather(latitude, longitude);

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("성공: FeignException 발생 시 null 반환")
        void success_feignException_returnsNull() {
            // given
            Double latitude = 37.5665;
            Double longitude = 126.9780;

            given(weatherApiClient.getWeather(any(), any(), anyString(), anyString(), anyString()))
                    .willThrow(FeignException.class);

            // when
            WeatherData result = weatherClient.getCurrentWeather(latitude, longitude);

            // then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("캐시 동작")
    class CacheBehavior {

        @Test
        @DisplayName("성공: 같은 좌표 재조회 시 API 한 번만 호출 (캐시 히트)")
        void success_cacheHit_callsApiOnce() {
            // given
            Double latitude = 37.5665;
            Double longitude = 126.9780;
            WeatherApiResponse mockResponse = createMockResponse("흐림", 15.0);

            given(weatherApiClient.getWeather(any(), any(), anyString(), anyString(), anyString()))
                    .willReturn(mockResponse);

            // when - 같은 좌표로 3번 호출
            WeatherData first = weatherClient.getCurrentWeather(latitude, longitude);
            WeatherData second = weatherClient.getCurrentWeather(latitude, longitude);
            WeatherData third = weatherClient.getCurrentWeather(latitude, longitude);

            // then - API는 1번만 호출됨
            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
            assertThat(third).isNotNull();
            assertThat(first.getDescription()).isEqualTo(second.getDescription());

            then(weatherApiClient).should(times(1))
                    .getWeather(any(), any(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("성공: 다른 좌표 조회 시 API 각각 호출")
        void success_differentCoordinates_callsApiMultipleTimes() {
            // given
            WeatherApiResponse seoulResponse = createMockResponse("맑음", 20.0);
            WeatherApiResponse busanResponse = createMockResponse("흐림", 18.0);

            given(weatherApiClient.getWeather(any(), any(), anyString(), anyString(), anyString()))
                    .willReturn(seoulResponse)
                    .willReturn(busanResponse);

            // when - 서로 다른 좌표로 호출
            WeatherData seoul = weatherClient.getCurrentWeather(37.57, 126.98);
            WeatherData busan = weatherClient.getCurrentWeather(35.18, 129.08);

            // then - API 2번 호출됨
            assertThat(seoul).isNotNull();
            assertThat(busan).isNotNull();

            then(weatherApiClient).should(times(2))
                    .getWeather(any(), any(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("성공: 소수점 1자리가 같으면 같은 캐시 사용")
        void success_sameRoundedCoordinates_usesSameCache() {
            // given
            WeatherApiResponse mockResponse = createMockResponse("비", 12.0);

            given(weatherApiClient.getWeather(any(), any(), anyString(), anyString(), anyString()))
                    .willReturn(mockResponse);

            // when - 소수점 2자리 이하만 다른 좌표
            // 37.5165 → 37.5, 37.5490 → 37.5 (반올림 후 같음)
            // 126.9180 → 126.9, 126.9499 → 126.9 (반올림 후 같음)
            WeatherData first = weatherClient.getCurrentWeather(37.5165, 126.9180);
            WeatherData second = weatherClient.getCurrentWeather(37.5490, 126.9499);

            // then - API 1번만 호출됨 (같은 캐시 키: 37.5, 126.9)
            assertThat(first).isNotNull();
            assertThat(second).isNotNull();

            then(weatherApiClient).should(times(1))
                    .getWeather(any(), any(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("getWeatherForVisit 메서드")
    class GetWeatherForVisit {

        @Test
        @DisplayName("성공: 정상 좌표와 시간으로 방문 날씨 조회")
        void success_withValidParameters() {
            // given
            Double latitude = 37.8813;
            Double longitude = 127.7298;
            LocalTime visitTime = LocalTime.of(14, 30);
            TimemachineApiResponse mockResponse = createMockTimemachineResponse("맑음", 22.5);

            given(weatherApiClient.getTimemachineWeather(any(), any(), anyLong(), anyString(), anyString(), anyString()))
                    .willReturn(mockResponse);

            // when
            VisitWeather result = weatherClient.getWeatherForVisit(latitude, longitude, visitTime);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getDescription()).isEqualTo("맑음");
            assertThat(result.getTemperature()).isEqualTo(22);

            then(weatherApiClient).should(times(1))
                    .getTimemachineWeather(any(), any(), anyLong(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("성공: 위도가 null이면 API 호출하지 않고 null 반환")
        void success_withNullLatitude_returnsNull() {
            // when
            VisitWeather result = weatherClient.getWeatherForVisit(null, 127.0, LocalTime.of(14, 0));

            // then
            assertThat(result).isNull();
            then(weatherApiClient).should(never())
                    .getTimemachineWeather(any(), any(), anyLong(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("성공: 경도가 null이면 API 호출하지 않고 null 반환")
        void success_withNullLongitude_returnsNull() {
            // when
            VisitWeather result = weatherClient.getWeatherForVisit(37.5, null, LocalTime.of(14, 0));

            // then
            assertThat(result).isNull();
            then(weatherApiClient).should(never())
                    .getTimemachineWeather(any(), any(), anyLong(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("성공: 시간이 null이면 API 호출하지 않고 null 반환")
        void success_withNullTime_returnsNull() {
            // when
            VisitWeather result = weatherClient.getWeatherForVisit(37.5, 127.0, null);

            // then
            assertThat(result).isNull();
            then(weatherApiClient).should(never())
                    .getTimemachineWeather(any(), any(), anyLong(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("성공: API 실패 시 null 반환")
        void success_apiFailure_returnsNull() {
            // given
            given(weatherApiClient.getTimemachineWeather(any(), any(), anyLong(), anyString(), anyString(), anyString()))
                    .willThrow(new RuntimeException("API 서버 오류"));

            // when
            VisitWeather result = weatherClient.getWeatherForVisit(37.5, 127.0, LocalTime.of(14, 0));

            // then
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("성공: 같은 좌표와 시간 재조회 시 캐시 히트")
        void success_cacheHit_callsApiOnce() {
            // given
            TimemachineApiResponse mockResponse = createMockTimemachineResponse("흐림", 18.0);

            given(weatherApiClient.getTimemachineWeather(any(), any(), anyLong(), anyString(), anyString(), anyString()))
                    .willReturn(mockResponse);

            // when - 같은 좌표, 같은 시간(정시 기준)으로 3번 호출
            LocalTime time1 = LocalTime.of(14, 15);
            LocalTime time2 = LocalTime.of(14, 45);  // 같은 14시
            LocalTime time3 = LocalTime.of(14, 0);

            VisitWeather first = weatherClient.getWeatherForVisit(37.5, 127.0, time1);
            VisitWeather second = weatherClient.getWeatherForVisit(37.5, 127.0, time2);
            VisitWeather third = weatherClient.getWeatherForVisit(37.5, 127.0, time3);

            // then - API 1번만 호출됨 (같은 캐시 키)
            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
            assertThat(third).isNotNull();

            then(weatherApiClient).should(times(1))
                    .getTimemachineWeather(any(), any(), anyLong(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("성공: 다른 시간 조회 시 API 각각 호출")
        void success_differentHours_callsApiMultipleTimes() {
            // given
            TimemachineApiResponse response1 = createMockTimemachineResponse("맑음", 20.0);
            TimemachineApiResponse response2 = createMockTimemachineResponse("흐림", 18.0);

            given(weatherApiClient.getTimemachineWeather(any(), any(), anyLong(), anyString(), anyString(), anyString()))
                    .willReturn(response1)
                    .willReturn(response2);

            // when - 다른 시간대로 호출
            VisitWeather morning = weatherClient.getWeatherForVisit(37.5, 127.0, LocalTime.of(9, 0));
            VisitWeather afternoon = weatherClient.getWeatherForVisit(37.5, 127.0, LocalTime.of(14, 0));

            // then - API 2번 호출됨
            assertThat(morning).isNotNull();
            assertThat(afternoon).isNotNull();

            then(weatherApiClient).should(times(2))
                    .getTimemachineWeather(any(), any(), anyLong(), anyString(), anyString(), anyString());
        }
    }

    /**
     * Mock WeatherApiResponse 생성
     */
    private WeatherApiResponse createMockResponse(String description, Double temperature) {
        WeatherApiResponse response = new WeatherApiResponse();

        WeatherApiResponse.Weather weather = new WeatherApiResponse.Weather();
        weather.setDescription(description);
        response.setWeather(List.of(weather));

        WeatherApiResponse.Main main = new WeatherApiResponse.Main();
        main.setTemp(temperature);
        response.setMain(main);

        return response;
    }

    /**
     * Mock TimemachineApiResponse 생성
     */
    private TimemachineApiResponse createMockTimemachineResponse(String description, Double temperature) {
        TimemachineApiResponse response = new TimemachineApiResponse();

        TimemachineApiResponse.Weather weather = new TimemachineApiResponse.Weather();
        weather.setDescription(description);

        TimemachineApiResponse.TimemachineData data = new TimemachineApiResponse.TimemachineData();
        data.setTemp(temperature);
        data.setWeather(List.of(weather));

        response.setData(List.of(data));

        return response;
    }
}
