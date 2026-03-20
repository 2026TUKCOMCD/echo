package com.example.echo.location.service;

import com.example.echo.location.client.GeocodingClient;
import com.example.echo.location.dto.GeocodingResult;
import com.example.echo.location.dto.LocationData;
import com.example.echo.location.dto.RawLocationData;
import com.example.echo.location.dto.RawVisitedPlace;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationServiceTest {

    @Mock
    private GeocodingClient geocodingClient;

    @InjectMocks
    private LocationService locationService;

    @Nested
    @DisplayName("enrichLocationData 테스트")
    class EnrichLocationDataTest {

        @Test
        @DisplayName("raw == null → null 반환")
        void enrichLocationData_null() {
            LocationData result = locationService.enrichLocationData(null);
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("visitedPlaces == null → 빈 리스트")
        void enrichLocationData_nullVisitedPlaces() {
            RawLocationData raw = RawLocationData.builder()
                    .currentLatitude(37.5665)
                    .currentLongitude(126.9780)
                    .visitedPlaces(null)
                    .totalDistanceKm(2.5)
                    .build();

            when(geocodingClient.getCityName(any(), any())).thenReturn("서울");

            LocationData result = locationService.enrichLocationData(raw);

            assertThat(result).isNotNull();
            assertThat(result.getVisitedPlaces()).isEmpty();
        }

        @Test
        @DisplayName("정상 변환 → currentCity, totalDistanceKm 복사 확인")
        void enrichLocationData_success() {
            RawLocationData raw = RawLocationData.builder()
                    .currentLatitude(37.5665)
                    .currentLongitude(126.9780)
                    .visitedPlaces(List.of())
                    .totalDistanceKm(3.2)
                    .build();

            when(geocodingClient.getCityName(37.5665, 126.9780)).thenReturn("서울");

            LocationData result = locationService.enrichLocationData(raw);

            assertThat(result.getCurrentCity()).isEqualTo("서울");
            assertThat(result.getTotalDistanceKm()).isEqualTo(3.2);
        }

        @Test
        @DisplayName("방문 장소 → placeName/address 매핑 확인")
        void enrichLocationData_visitedPlaceMapping() {
            RawVisitedPlace rawPlace = RawVisitedPlace.builder()
                    .latitude(37.5172)
                    .longitude(127.0473)
                    .visitStartTime(LocalTime.of(14, 0))
                    .visitEndTime(LocalTime.of(15, 30))
                    .stayDurationMinutes(90)
                    .build();

            RawLocationData raw = RawLocationData.builder()
                    .currentLatitude(37.5665)
                    .currentLongitude(126.9780)
                    .visitedPlaces(List.of(rawPlace))
                    .totalDistanceKm(1.0)
                    .build();

            when(geocodingClient.getCityName(any(), any())).thenReturn("서울");
            when(geocodingClient.reverseGeocode(37.5172, 127.0473)).thenReturn(
                    GeocodingResult.builder()
                            .placeName("스타벅스 강남점")
                            .address("서울 강남구 테헤란로 101")
                            .build()
            );

            LocationData result = locationService.enrichLocationData(raw);

            assertThat(result.getVisitedPlaces()).hasSize(1);
            assertThat(result.getVisitedPlaces().get(0).getPlaceName()).isEqualTo("스타벅스 강남점");
            assertThat(result.getVisitedPlaces().get(0).getAddress()).isEqualTo("서울 강남구 테헤란로 101");
            assertThat(result.getVisitedPlaces().get(0).getLatitude()).isEqualTo(37.5172);
            assertThat(result.getVisitedPlaces().get(0).getLongitude()).isEqualTo(127.0473);
            assertThat(result.getVisitedPlaces().get(0).getVisitStartTime()).isEqualTo(LocalTime.of(14, 0));
            assertThat(result.getVisitedPlaces().get(0).getVisitEndTime()).isEqualTo(LocalTime.of(15, 30));
            assertThat(result.getVisitedPlaces().get(0).getStayDurationMinutes()).isEqualTo(90);
        }
    }
}
