package com.example.echo.location.client;

import com.example.echo.location.dto.GeocodingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GeocodingClient {

    public String getCityName(Double latitude, Double longitude) {
        // TODO: 실제 역지오코딩 API 연동 시 구현
        return "서울";
    }

    public GeocodingResult reverseGeocode(Double latitude, Double longitude) {
        // TODO: 실제 역지오코딩 API 연동 시 구현
        return GeocodingResult.builder()
                .placeName("알 수 없는 장소")
                .address("주소 정보 없음")
                .build();
    }
}
