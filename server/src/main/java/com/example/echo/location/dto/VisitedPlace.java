package com.example.echo.location.dto;

import com.example.echo.common.dto.VisitWeather;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * 서버 내부에서 사용하는 보강된 방문 장소 정보
 *
 * 원시 좌표(latitude/longitude)로부터 장소명/주소/날씨 정보를 추가한 형태
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitedPlace {

    // ===== 보강된 장소 정보 =====
    /** 장소명 (좌표 → 역지오코딩) */
    private String placeName;

    /** 주소 (좌표 → 역지오코딩) */
    private String address;

    /** 방문 시점 날씨 (Timemachine API) */
    private VisitWeather weather;

    /**
     * 확정된 루틴 방문 장소와 매칭됐을 때의 카테고리 라벨 (예: "회사", "병원").
     * 매칭되면 이름을 이미 알고 있으므로 placeName을 위한 역지오코딩을 하지 않는다 -
     * PromptService가 표시 시 placeName 대신 이 값을 우선 사용한다.
     */
    private String routineCategory;

    // ===== 원시 데이터 (RawVisitedPlace에서 복사) =====
    private Double latitude;
    private Double longitude;

    @Schema(type = "string")
    private LocalTime visitStartTime;

    @Schema(type = "string")
    private LocalTime visitEndTime;

    private Integer stayDurationMinutes;

    // ===== 분류 =====
    /**
     * 거주지(집) 여부.
     * 등록된 집 좌표와 이 장소의 거리가 임계값 이내이면 true.
     * 집 좌표가 없으면 항상 false(= 분류 불가, 모두 외출로 취급).
     * true인 장소는 "다녀오셨네요"(외출)가 아니라 집에서 보낸 시간으로 다룬다.
     */
    @Builder.Default
    private boolean isHome = false;
}
