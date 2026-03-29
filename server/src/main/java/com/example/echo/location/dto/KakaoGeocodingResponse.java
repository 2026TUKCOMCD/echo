package com.example.echo.location.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Kakao 좌표 → 주소 변환 API 응답 DTO
 *
 * API: GET /v2/local/geo/coord2address.json
 * 문서: https://developers.kakao.com/docs/latest/ko/local/dev-guide#coord-to-address
 */
@Data
public class KakaoGeocodingResponse {

    private List<Document> documents;

    @Data
    public static class Document {
        private Address address;

        @JsonProperty("road_address")
        private RoadAddress roadAddress;
    }

    @Data
    public static class Address {
        @JsonProperty("address_name")
        private String addressName;      // 예: "서울 강남구 역삼동 737"
    }

    @Data
    public static class RoadAddress {
        @JsonProperty("address_name")
        private String addressName;      // 예: "서울 강남구 테헤란로 152"

        @JsonProperty("building_name")
        private String buildingName;     // 예: "강남파이낸스센터" (없으면 빈 문자열)
    }

    /**
     * 빌딩명 > 도로명주소 > 지번주소 순으로 가장 의미 있는 장소명 반환
     */
    public String getBestPlaceName() {
        if (documents == null || documents.isEmpty()) return null;

        Document doc = documents.get(0);

        if (doc.getRoadAddress() != null
                && doc.getRoadAddress().getBuildingName() != null
                && !doc.getRoadAddress().getBuildingName().isBlank()) {
            return doc.getRoadAddress().getBuildingName();
        }
        if (doc.getRoadAddress() != null
                && doc.getRoadAddress().getAddressName() != null) {
            return doc.getRoadAddress().getAddressName();
        }
        if (doc.getAddress() != null) {
            return doc.getAddress().getAddressName();
        }
        return null;
    }
}
