package com.example.echo.location.service;

import com.example.echo.location.client.GeocodingClient;
import com.example.echo.location.dto.KakaoGeocodingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private final GeocodingClient geocodingClient;

    // Feign 인터페이스에 @Cacheable 직접 불가 → 이 Service 메서드에 적용
    // 같은 좌표 반복 조회 방지 (24시간 TTL - CacheConfig에서 설정)
    @Cacheable(value = "geocoding", key = "#lat + '_' + #lon")
    public String reverseGeocode(double lat, double lon) {
        try {
            // Kakao: x=경도(lon), y=위도(lat)
            KakaoGeocodingResponse response =
                    geocodingClient.reverseGeocode(lon, lat, "WGS84");
            String placeName = response.getBestPlaceName();
            return placeName != null ? placeName : "알 수 없는 위치";
        } catch (Exception e) {
            log.warn("역지오코딩 실패 - lat:{}, lon:{}, 이유:{}", lat, lon, e.getMessage());
            return null;  // 예외 미전파 - 대화 중단 방지
        }
    }
}
