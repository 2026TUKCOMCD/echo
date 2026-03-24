package com.example.echo.location.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 서버 내부에서 사용하는 보강된 위치 데이터
 *
 * 원시 위치 데이터(RawLocationData)에서 좌표를 장소명으로 변환하고,
 * 방문 장소를 주소/장소명으로 보강하여 프롬프트 생성에 활용
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationData {

    /** 현재 위치 주소 (좌표 → 역지오코딩) */
    private String currentCity;

    /** 보강된 방문 장소 목록 */
    private List<VisitedPlace> visitedPlaces;

    /** 총 이동 거리 (km) */
    private Double totalDistanceKm;
}
