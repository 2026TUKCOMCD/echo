package com.example.echo.location.service;

import com.example.echo.location.client.GeocodingClient;
import com.example.echo.location.dto.GeocodingResult;
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

    // ===== getCityName 테스트 =====

    @Test
    @DisplayName("getCityName - 도로명주소가 있으면 도로명주소를 반환한다")
    void getCityName_returnsRoadAddress() {
        double lat = 37.5665, lon = 126.9780;
        KakaoGeocodingResponse response = buildResponse("강남파이낸스센터", "서울 강남구 테헤란로 152", "서울 강남구 역삼동 737");
        when(geocodingClient.reverseGeocode(lon, lat, "WGS84")).thenReturn(response);

        String result = geocodingService.getCityName(lat, lon);

        // 빌딩명이 아닌 도로명주소 반환
        assertThat(result).isEqualTo("서울 강남구 테헤란로 152");
    }

    @Test
    @DisplayName("getCityName - API 실패 시 null을 반환한다")
    void getCityName_returnsNullOnApiFailure() {
        double lat = 37.5665, lon = 126.9780;
        when(geocodingClient.reverseGeocode(lon, lat, "WGS84"))
                .thenThrow(new RuntimeException("Connection refused"));

        String result = geocodingService.getCityName(lat, lon);

        assertThat(result).isNull();
    }

    // ===== reverseGeocode 테스트 =====

    @Test
    @DisplayName("reverseGeocode - 빌딩명이 있으면 placeName에 빌딩명, address에 도로명주소를 반환한다")
    void reverseGeocode_returnsBuildingNameAsPlaceName() {
        double lat = 37.5665, lon = 126.9780;
        KakaoGeocodingResponse response = buildResponse("강남파이낸스센터", "서울 강남구 테헤란로 152", "서울 강남구 역삼동 737");
        when(geocodingClient.reverseGeocode(lon, lat, "WGS84")).thenReturn(response);

        GeocodingResult result = geocodingService.reverseGeocode(lat, lon);

        assertThat(result.getPlaceName()).isEqualTo("강남파이낸스센터");
        assertThat(result.getAddress()).isEqualTo("서울 강남구 테헤란로 152");
    }

    @Test
    @DisplayName("reverseGeocode - 빌딩명 없으면 placeName에 도로명주소를 반환한다")
    void reverseGeocode_returnsRoadAddressAsPlaceNameWhenNoBuildingName() {
        double lat = 37.5665, lon = 126.9780;
        KakaoGeocodingResponse response = buildResponse(null, "서울 강남구 테헤란로 152", "서울 강남구 역삼동 737");
        when(geocodingClient.reverseGeocode(lon, lat, "WGS84")).thenReturn(response);

        GeocodingResult result = geocodingService.reverseGeocode(lat, lon);

        assertThat(result.getPlaceName()).isEqualTo("서울 강남구 테헤란로 152");
        assertThat(result.getAddress()).isEqualTo("서울 강남구 테헤란로 152");
    }

    @Test
    @DisplayName("reverseGeocode - documents가 비어있으면 placeName과 address가 null인 빈 결과를 반환한다")
    void reverseGeocode_returnsEmptyResultWhenDocumentsEmpty() {
        double lat = 37.5665, lon = 126.9780;
        KakaoGeocodingResponse response = new KakaoGeocodingResponse();
        response.setDocuments(Collections.emptyList());
        when(geocodingClient.reverseGeocode(lon, lat, "WGS84")).thenReturn(response);

        GeocodingResult result = geocodingService.reverseGeocode(lat, lon);

        assertThat(result).isNotNull();
        assertThat(result.getPlaceName()).isNull();
        assertThat(result.getAddress()).isNull();
    }

    @Test
    @DisplayName("reverseGeocode - API 실패 시 예외를 던지지 않고 빈 결과를 반환한다")
    void reverseGeocode_returnsEmptyResultOnApiFailure() {
        double lat = 37.5665, lon = 126.9780;
        when(geocodingClient.reverseGeocode(lon, lat, "WGS84"))
                .thenThrow(new RuntimeException("Connection refused"));

        GeocodingResult result = geocodingService.reverseGeocode(lat, lon);

        assertThat(result).isNotNull();
        assertThat(result.getPlaceName()).isNull();
        assertThat(result.getAddress()).isNull();
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
