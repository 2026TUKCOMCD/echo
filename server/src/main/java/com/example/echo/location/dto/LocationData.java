package com.example.echo.location.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 역지오코딩으로 보강된 위치 데이터
 *
 * RawLocationData(좌표) → GeocodingService 호출 → LocationData(장소명)
 * 변환 결과는 UserContext에 저장되어 대화 세션 동안 재사용된다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationData {

    /** 현재 위치 주소 (역지오코딩) */
    private String currentCity;

    /** 보강된 방문 장소 목록 */
    private List<VisitedPlace> visitedPlaces;

    /** 총 이동 거리 (km) */
    private Double totalDistanceKm;
}
