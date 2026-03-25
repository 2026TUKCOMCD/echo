package com.example.echo.location.client;

import com.example.echo.location.dto.GeocodingResult;
import com.example.echo.location.dto.KakaoGeocodingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 역지오코딩 클라이언트
 *
 * GeocodingFeignClient(Kakao API)의 래퍼.
 * LocationService에서 사용하는 비즈니스 메서드를 제공한다.
 *
 * 주의: Kakao API는 x=경도(lon), y=위도(lat) 순서
 *       GPS의 lat/lon과 반대이므로 내부에서 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeocodingClient {

    private final GeocodingFeignClient geocodingFeignClient;

    public String getCityName(double lat, double lon) {
        try {
            KakaoGeocodingResponse response = geocodingFeignClient.reverseGeocode(lon, lat, "WGS84");
            return extractAddress(response);
        } catch (Exception e) {
            log.warn("현재 위치 역지오코딩 실패 - lat:{}, lon:{}, 이유:{}", lat, lon, e.getMessage());
            return null;
        }
    }

    public GeocodingResult reverseGeocode(double lat, double lon) {
        try {
            KakaoGeocodingResponse response = geocodingFeignClient.reverseGeocode(lon, lat, "WGS84");
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
