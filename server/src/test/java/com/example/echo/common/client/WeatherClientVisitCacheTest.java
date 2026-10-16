package com.example.echo.common.client;

import com.example.echo.common.dto.TimemachineApiResponse;
import com.example.echo.common.dto.VisitWeather;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WeatherClient - 방문 시점 날씨 캐시")
class WeatherClientVisitCacheTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final double LAT = 37.5665;
    private static final double LON = 126.978;
    private static final LocalTime VISIT_09 = LocalTime.of(9, 30);

    @Mock
    private WeatherApiClient weatherApiClient;

    private MutableClock clock;
    private WeatherClient weatherClient;

    @BeforeEach
    void setUp() {
        // 첫날 저녁 8시(KST)에 오늘 방문(오전 9시대) 날씨를 조회하는 상황에서 시작
        clock = new MutableClock(LocalDateTime.of(2026, 10, 16, 20, 0).atZone(KST).toInstant(), KST);
        weatherClient = new WeatherClient(weatherApiClient, "test-key", clock);
    }

    @Test
    @DisplayName("다음 날 같은 시간대 방문은 전날 날씨를 재사용하지 않고 그날 날씨를 조회한다")
    void nextDaySameHour_fetchesThatDaysWeather() {
        TimemachineApiResponse sunny = weatherResponse("맑음", 20);
        TimemachineApiResponse rainy = weatherResponse("비", 12);
        when(weatherApiClient.getTimemachineWeather(any(), any(), any(), any(), any(), any()))
                .thenReturn(sunny, rainy);

        VisitWeather firstDay = weatherClient.getWeatherForVisit(LAT, LON, VISIT_09);
        clock.advance(Duration.ofHours(14)); // 다음 날 오전 10시 - 캐시 TTL(24시간) 안
        VisitWeather nextDay = weatherClient.getWeatherForVisit(LAT, LON, VISIT_09);

        assertThat(firstDay.getDescription()).isEqualTo("맑음");
        assertThat(nextDay.getDescription()).isEqualTo("비");
        long nextDay9am = LocalDateTime.of(2026, 10, 17, 9, 0).atZone(KST).toEpochSecond();
        verify(weatherApiClient).getTimemachineWeather(any(), any(), eq(nextDay9am), any(), any(), any());
    }

    @Test
    @DisplayName("같은 날 같은 시간대 방문은 캐시를 써서 API를 한 번만 부른다")
    void sameDaySameHour_usesCache() {
        TimemachineApiResponse sunny = weatherResponse("맑음", 20);
        when(weatherApiClient.getTimemachineWeather(any(), any(), any(), any(), any(), any())).thenReturn(sunny);

        weatherClient.getWeatherForVisit(LAT, LON, VISIT_09);
        clock.advance(Duration.ofHours(1));
        VisitWeather again = weatherClient.getWeatherForVisit(LAT, LON, LocalTime.of(9, 50));

        assertThat(again.getDescription()).isEqualTo("맑음");
        verify(weatherApiClient, times(1)).getTimemachineWeather(any(), any(), any(), any(), any(), any());
    }

    private static TimemachineApiResponse weatherResponse(String description, int temperature) {
        TimemachineApiResponse response = mock(TimemachineApiResponse.class);
        when(response.toVisitWeather())
                .thenReturn(VisitWeather.builder().description(description).temperature(temperature).build());
        return response;
    }

    /** 테스트에서 시간을 앞으로 돌릴 수 있는 시계 (캐시는 인스턴스 안에 있어 같은 WeatherClient로 날짜를 넘겨야 한다) */
    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
