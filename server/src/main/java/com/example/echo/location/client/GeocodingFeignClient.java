package com.example.echo.location.client;

import com.example.echo.location.dto.KakaoGeocodingResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Kakao 역지오코딩 Feign 클라이언트
 *
 * 좌표(위도/경도) → 주소명 변환
 *
 * 주의: Kakao API는 x=경도(lon), y=위도(lat) 순서
 *       GPS의 lat/lon과 반대이므로 호출 시 반드시 확인
 */
@FeignClient(
        name = "kakao-geocoding",
        url = "${kakao.api.url}",
        configuration = KakaoFeignConfig.class
)
public interface GeocodingFeignClient {

    @GetMapping("/v2/local/geo/coord2address.json")
    KakaoGeocodingResponse reverseGeocode(
            @RequestParam("x") double lon,
            @RequestParam("y") double lat,
            @RequestParam("input_coord") String inputCoord
    );
}
