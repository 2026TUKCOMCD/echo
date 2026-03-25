package com.example.echo.location.service;

import com.example.echo.location.client.GeocodingClient;
import com.example.echo.location.dto.GeocodingResult;
import com.example.echo.location.dto.KakaoGeocodingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 역지오코딩 서비스
 *
 * GeocodingClient(Feign)를 호출하여 좌표를 장소명/주소로 변환한다.
 * 호출 포맷 변환(lat/lon → lon/lat), 응답 파싱, 예외 처리를 담당한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private final GeocodingClient geocodingClient;

    public String getCityName(double lat, double lon) {
        try {
            KakaoGeocodingResponse response = geocodingClient.reverseGeocode(lon, lat, "WGS84");
            return extractAddress(response);
        } catch (Exception e) {
            log.warn("현재 위치 역지오코딩 실패 - lat:{}, lon:{}, 이유:{}", lat, lon, e.getMessage());
            return null;
        }
    }

    public GeocodingResult reverseGeocode(double lat, double lon) {
        try {
            KakaoGeocodingResponse response = geocodingClient.reverseGeocode(lon, lat, "WGS84");
            return GeocodingResult.builder()
                    .placeName(response.getBestPlaceName())
                    .address(extractAddress(response))
                    .build();
        } catch (Exception e) {
            log.warn("방문 장소 역지오코딩 실패 - lat:{}, lon:{}, 이유:{}", lat, lon, e.getMessage());
            return GeocodingResult.builder().build();
        }
    }

    private String extractAddress(KakaoGeocodingResponse response) {
        if (response.getDocuments() == null || response.getDocuments().isEmpty()) return null;
        KakaoGeocodingResponse.Document doc = response.getDocuments().get(0);
        if (doc.getRoadAddress() != null && doc.getRoadAddress().getAddressName() != null) {
            return doc.getRoadAddress().getAddressName();
        }
        if (doc.getAddress() != null) {
            return doc.getAddress().getAddressName();
        }
        return null;
    }
}
