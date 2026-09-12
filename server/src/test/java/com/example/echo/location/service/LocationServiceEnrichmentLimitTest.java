package com.example.echo.location.service;

import com.example.echo.common.client.WeatherClient;
import com.example.echo.common.dto.VisitWeather;
import com.example.echo.location.dto.GeocodingResult;
import com.example.echo.location.dto.LocationData;
import com.example.echo.location.dto.RawLocationData;
import com.example.echo.location.dto.RawVisitedPlace;
import com.example.echo.location.dto.VisitedPlace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 방문 장소 지오코딩/날씨 조회 최적화 검증.
 *
 * - 집으로 판정된 곳은 지오코딩/날씨 조회를 하지 않는다(이름을 이미 알고 있으므로).
 * - 외출 후보는 체류 시간 상위 OUTING_ENRICHMENT_LIMIT곳만 지오코딩/날씨 조회 대상이 된다.
 *   (buildTodayActivityGuide가 결국 체류 시간이 가장 긴 1곳만 언급하도록 지시하므로,
 *   나머지 짧은 체류지까지 전부 외부 API를 호출하는 것은 낭비였음)
 */
@ExtendWith(MockitoExtension.class)
class LocationServiceEnrichmentLimitTest {

    private static final double HOME_LAT = 37.5665;
    private static final double HOME_LNG = 126.9780;

    @Mock
    private GeocodingService geocodingService;

    @Mock
    private WeatherClient weatherClient;

    @InjectMocks
    private LocationService locationService;

    private RawVisitedPlace outing(double lat, double lng, int stayDurationMinutes) {
        return RawVisitedPlace.builder()
                .latitude(lat)
                .longitude(lng)
                .visitStartTime(LocalTime.of(14, 0))
                .visitEndTime(LocalTime.of(14, stayDurationMinutes % 60))
                .stayDurationMinutes(stayDurationMinutes)
                .build();
    }

    @Test
    @DisplayName("집으로 판정된 방문은 지오코딩/날씨 조회를 하지 않는다")
    void homeVisit_skipsGeocodingAndWeather() {
        lenient().when(geocodingService.getCityName(anyDouble(), anyDouble())).thenReturn("서울");

        // 체류 60분(30분 이상)이라도 집이면 날씨 조회 자체가 스킵되어야 한다
        RawVisitedPlace homePlace = outing(HOME_LAT, HOME_LNG, 60);
        RawLocationData raw = RawLocationData.builder()
                .currentLatitude(HOME_LAT)
                .currentLongitude(HOME_LNG)
                .visitedPlaces(List.of(homePlace))
                .totalDistanceKm(0.0)
                .build();

        LocationData result = locationService.enrichLocationData(raw, HOME_LAT, HOME_LNG);

        verify(geocodingService, never()).reverseGeocode(anyDouble(), anyDouble());
        verify(weatherClient, never()).getWeatherForVisit(anyDouble(), anyDouble(), any());

        VisitedPlace place = result.getVisitedPlaces().get(0);
        assertThat(place.isHome()).isTrue();
        assertThat(place.getPlaceName()).isNull();
    }

    @Test
    @DisplayName("외출 후보가 상한을 넘으면 체류 시간 상위 N곳만 지오코딩/날씨 조회한다")
    void outingsBeyondLimit_onlyTopNEnriched() {
        lenient().when(geocodingService.getCityName(anyDouble(), anyDouble())).thenReturn("서울");
        lenient().when(geocodingService.reverseGeocode(anyDouble(), anyDouble()))
                .thenReturn(GeocodingResult.builder().placeName("어떤 장소").address("주소").build());
        lenient().when(weatherClient.getWeatherForVisit(anyDouble(), anyDouble(), any()))
                .thenReturn(VisitWeather.builder().description("맑음").temperature(20).build());

        // 집에서 충분히 먼 좌표 5곳, 체류 시간은 서로 다르게 - 상위 3곳(90, 80, 70분)만 조회 대상
        RawVisitedPlace top1 = outing(37.60, 127.10, 90);
        RawVisitedPlace top2 = outing(37.61, 127.11, 80);
        RawVisitedPlace top3 = outing(37.62, 127.12, 70);
        RawVisitedPlace excluded1 = outing(37.63, 127.13, 40);
        RawVisitedPlace excluded2 = outing(37.64, 127.14, 35);

        RawLocationData raw = RawLocationData.builder()
                .currentLatitude(HOME_LAT)
                .currentLongitude(HOME_LNG)
                .visitedPlaces(List.of(excluded1, top1, excluded2, top2, top3))
                .totalDistanceKm(5.0)
                .build();

        LocationData result = locationService.enrichLocationData(raw, HOME_LAT, HOME_LNG);

        verify(geocodingService, times(1)).reverseGeocode(top1.getLatitude(), top1.getLongitude());
        verify(geocodingService, times(1)).reverseGeocode(top2.getLatitude(), top2.getLongitude());
        verify(geocodingService, times(1)).reverseGeocode(top3.getLatitude(), top3.getLongitude());
        verify(geocodingService, never()).reverseGeocode(excluded1.getLatitude(), excluded1.getLongitude());
        verify(geocodingService, never()).reverseGeocode(excluded2.getLatitude(), excluded2.getLongitude());

        // 상한에 걸려 제외된 곳은 애초에 VisitedPlace로 만들어지지 않는다(= 프롬프트에도 안 나타남)
        assertThat(result.getVisitedPlaces()).hasSize(3);
    }
}
