package com.example.echo.location.service;

import com.example.echo.common.client.WeatherClient;
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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.lenient;

/**
 * 방문 장소의 집/외출 분류(isHome) 검증.
 *
 * 등록된 집 좌표와 방문 좌표의 Haversine 거리가 판정 반경(150m) 이내이면 집으로 분류된다.
 * 체류 30분 미만으로 잡아 날씨 조회(WeatherClient)를 타지 않게 해 mock을 최소화한다.
 */
@ExtendWith(MockitoExtension.class)
class LocationServiceHomeClassificationTest {

    // 서울시청 부근을 집 좌표로 사용
    private static final double HOME_LAT = 37.5665;
    private static final double HOME_LNG = 126.9780;

    @Mock
    private GeocodingService geocodingService;

    @Mock
    private WeatherClient weatherClient;

    @InjectMocks
    private LocationService locationService;

    private RawLocationData rawWithVisit(double lat, double lng) {
        RawVisitedPlace place = RawVisitedPlace.builder()
                .latitude(lat)
                .longitude(lng)
                .visitStartTime(LocalTime.of(14, 0))
                .visitEndTime(LocalTime.of(14, 20))
                .stayDurationMinutes(20) // 30분 미만 → 날씨 조회 안 함
                .build();
        return RawLocationData.builder()
                .currentLatitude(HOME_LAT)
                .currentLongitude(HOME_LNG)
                .visitedPlaces(List.of(place))
                .totalDistanceKm(1.0)
                .build();
    }

    private void stubGeocoding() {
        lenient().when(geocodingService.getCityName(anyDouble(), anyDouble())).thenReturn("서울");
        lenient().when(geocodingService.reverseGeocode(anyDouble(), anyDouble())).thenReturn(
                GeocodingResult.builder().placeName("어떤 장소").address("서울 어딘가").build());
    }

    @Test
    @DisplayName("집 좌표 반경 이내(~55m) 방문 → isHome = true")
    void visitNearHome_isHomeTrue() {
        stubGeocoding();
        // 위도 +0.0005 ≈ 약 55m → 150m 이내
        RawLocationData raw = rawWithVisit(HOME_LAT + 0.0005, HOME_LNG);

        LocationData result = locationService.enrichLocationData(raw, HOME_LAT, HOME_LNG);

        VisitedPlace place = result.getVisitedPlaces().get(0);
        assertThat(place.isHome()).isTrue();
    }

    @Test
    @DisplayName("집 좌표에서 먼 방문(다른 동네) → isHome = false")
    void visitFarFromHome_isHomeFalse() {
        stubGeocoding();
        // 위도 +0.0035 ≈ 약 390m → 150m 밖
        RawLocationData raw = rawWithVisit(HOME_LAT + 0.0035, HOME_LNG + 0.0100);

        LocationData result = locationService.enrichLocationData(raw, HOME_LAT, HOME_LNG);

        VisitedPlace place = result.getVisitedPlaces().get(0);
        assertThat(place.isHome()).isFalse();
    }

    @Test
    @DisplayName("집 좌표 미등록(null) → 분류 불가, 모두 isHome = false")
    void noHomeCoords_allOutings() {
        stubGeocoding();
        // 방문 좌표가 집 부근이라도 집 좌표가 없으면 분류 불가
        RawLocationData raw = rawWithVisit(HOME_LAT, HOME_LNG);

        LocationData result = locationService.enrichLocationData(raw, null, null);

        VisitedPlace place = result.getVisitedPlaces().get(0);
        assertThat(place.isHome()).isFalse();
    }

    @Test
    @DisplayName("단일 인자 오버로드(집 좌표 없음) → isHome = false")
    void legacyOverload_noClassification() {
        stubGeocoding();
        RawLocationData raw = rawWithVisit(HOME_LAT, HOME_LNG);

        LocationData result = locationService.enrichLocationData(raw);

        assertThat(result.getVisitedPlaces().get(0).isHome()).isFalse();
    }
}
