package com.example.echo.location.service;

import com.example.echo.location.client.GeocodingClient;
import com.example.echo.location.dto.KakaoGeocodingResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeocodingServiceTest {

    @Mock
    private GeocodingClient geocodingClient;

    @InjectMocks
    private GeocodingService geocodingService;

    @Test
    @DisplayName("빌딩명이 있으면 빌딩명을 반환한다")
    void reverseGeocode_returnsBuildingName() {
        // Given
        double lat = 37.5665, lon = 126.9780;

        KakaoGeocodingResponse response = buildResponse("강남파이낸스센터", "서울 강남구 테헤란로 152", "서울 강남구 역삼동 737");
        when(geocodingClient.reverseGeocode(lon, lat, "WGS84")).thenReturn(response);

        // When
        String result = geocodingService.reverseGeocode(lat, lon);

        // Then
        assertThat(result).isEqualTo("강남파이낸스센터");
    }

    @Test
    @DisplayName("빌딩명 없으면 도로명주소를 반환한다")
    void reverseGeocode_returnsRoadAddressWhenNoBuildingName() {
        // Given
        double lat = 37.5665, lon = 126.9780;

        KakaoGeocodingResponse response = buildResponse(null, "서울 강남구 테헤란로 152", "서울 강남구 역삼동 737");
        when(geocodingClient.reverseGeocode(lon, lat, "WGS84")).thenReturn(response);

        // When
        String result = geocodingService.reverseGeocode(lat, lon);

        // Then
        assertThat(result).isEqualTo("서울 강남구 테헤란로 152");
    }

    @Test
    @DisplayName("documents가 비어있으면 '알 수 없는 위치'를 반환한다")
    void reverseGeocode_returnsUnknownWhenDocumentsEmpty() {
        // Given
        double lat = 37.5665, lon = 126.9780;

        KakaoGeocodingResponse response = new KakaoGeocodingResponse();
        response.setDocuments(Collections.emptyList());
        when(geocodingClient.reverseGeocode(lon, lat, "WGS84")).thenReturn(response);

        // When
        String result = geocodingService.reverseGeocode(lat, lon);

        // Then
        assertThat(result).isEqualTo("알 수 없는 위치");
    }

    @Test
    @DisplayName("API 호출 실패 시 예외를 던지지 않고 null을 반환한다")
    void reverseGeocode_returnsNullOnApiFailure() {
        // Given
        double lat = 37.5665, lon = 126.9780;

        when(geocodingClient.reverseGeocode(lon, lat, "WGS84"))
                .thenThrow(new RuntimeException("Connection refused"));

        // When
        String result = geocodingService.reverseGeocode(lat, lon);

        // Then
        assertThat(result).isNull();
    }

    // ===== 헬퍼 메서드 =====

    private KakaoGeocodingResponse buildResponse(String buildingName, String roadAddressName, String addressName) {
        KakaoGeocodingResponse.RoadAddress roadAddress = new KakaoGeocodingResponse.RoadAddress();
        roadAddress.setBuildingName(buildingName != null ? buildingName : "");
        roadAddress.setAddressName(roadAddressName);

        KakaoGeocodingResponse.Address address = new KakaoGeocodingResponse.Address();
        address.setAddressName(addressName);

        KakaoGeocodingResponse.Document document = new KakaoGeocodingResponse.Document();
        document.setRoadAddress(roadAddress);
        document.setAddress(address);

        KakaoGeocodingResponse response = new KakaoGeocodingResponse();
        response.setDocuments(List.of(document));
        return response;
    }
}
