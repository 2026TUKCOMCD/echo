package com.example.echo.location.service;

import com.example.echo.location.client.GeocodingClient;
import com.example.echo.location.dto.GeocodingResult;
import com.example.echo.location.dto.KakaoGeocodingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Kakao 역지오코딩 서비스
 *
 * GeocodingClient(Feign)의 래퍼.
 * 캐싱은 이 클래스가 아니라 UserContext(contextStore)에서 담당한다.
 * 대화 시작 시 1회 호출 → LocationData로 변환 → UserContext에 저장.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private final GeocodingClient geocodingClient;

    /**
     * 현재 위치 좌표 → 도로명 주소 반환 (장소명 아님)
     *
     * LocationService.enrichLocationData()에서 currentCity 필드에 사용.
     *
     * @return 도로명 주소 or 지번 주소, 실패 시 null
     */
    public String getCityName(double lat, double lon) {
        try {
            KakaoGeocodingResponse response = geocodingClient.reverseGeocode(lon, lat, "WGS84");
            return extractAddress(response);
        } catch (Exception e) {
            log.warn("현재 위치 역지오코딩 실패 - lat:{}, lon:{}, 이유:{}", lat, lon, e.getMessage());
            return null;
        }
    }

    /**
     * 방문 장소 좌표 → 장소명 + 주소 반환
     *
     * LocationService.enrichLocationData()에서 각 VisitedPlace 보강에 사용.
     * 우선순위: 빌딩명 > 도로명주소 > 지번주소
     *
     * @return GeocodingResult(placeName, address), 실패 시 빈 객체 (예외 미전파)
     */
    public GeocodingResult reverseGeocode(double lat, double lon) {
        try {
            KakaoGeocodingResponse response = geocodingClient.reverseGeocode(lon, lat, "WGS84");
            return GeocodingResult.builder()
                    .placeName(response.getBestPlaceName())
                    .address(extractAddress(response))
                    .build();
        } catch (Exception e) {
            log.warn("방문 장소 역지오코딩 실패 - lat:{}, lon:{}, 이유:{}", lat, lon, e.getMessage());
            return GeocodingResult.builder().build();  // 예외 미전파 — 대화 중단 방지
        }
    }

    /**
     * 도로명 주소 or 지번 주소 추출 (빌딩명 제외)
     */
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
