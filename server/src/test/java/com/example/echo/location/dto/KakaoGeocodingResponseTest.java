package com.example.echo.location.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoGeocodingResponseTest {

    @Test
    @DisplayName("빌딩명이 한글을 포함하면 빌딩명을 반환")
    void buildingName_withHangul_returnsBuildingName() {
        KakaoGeocodingResponse response = withRoadAddress("강남파이낸스센터", "서울 강남구 테헤란로 152");

        assertThat(response.getBestPlaceName()).isEqualTo("강남파이낸스센터");
    }

    @Test
    @DisplayName("빌딩명이 한글 없이 영어로만 되어있으면 도로명주소로 대체 (TTS 언어 전환 방지)")
    void buildingName_withoutHangul_fallsBackToRoadAddress() {
        KakaoGeocodingResponse response = withRoadAddress("Starbucks Reserve", "서울 강남구 테헤란로 152");

        assertThat(response.getBestPlaceName()).isEqualTo("서울 강남구 테헤란로 152");
    }

    @Test
    @DisplayName("빌딩명이 없으면 도로명주소를 반환")
    void noBuildingName_returnsRoadAddress() {
        KakaoGeocodingResponse response = withRoadAddress("", "서울 강남구 테헤란로 152");

        assertThat(response.getBestPlaceName()).isEqualTo("서울 강남구 테헤란로 152");
    }

    @Test
    @DisplayName("문서가 없으면 null 반환")
    void noDocuments_returnsNull() {
        KakaoGeocodingResponse response = new KakaoGeocodingResponse();
        response.setDocuments(List.of());

        assertThat(response.getBestPlaceName()).isNull();
    }

    private KakaoGeocodingResponse withRoadAddress(String buildingName, String roadAddressName) {
        KakaoGeocodingResponse.RoadAddress roadAddress = new KakaoGeocodingResponse.RoadAddress();
        roadAddress.setBuildingName(buildingName);
        roadAddress.setAddressName(roadAddressName);

        KakaoGeocodingResponse.Document document = new KakaoGeocodingResponse.Document();
        document.setRoadAddress(roadAddress);

        KakaoGeocodingResponse response = new KakaoGeocodingResponse();
        response.setDocuments(List.of(document));
        return response;
    }
}
