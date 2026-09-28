package com.example.echo.routineplace.dto;

import com.example.echo.routineplace.entity.RoutinePlaceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 루틴 방문 장소 API 응답.
 *
 * previewAddress는 저장된 값이 아니라 조회 시점에 그때그때 역지오코딩한 결과다
 * (UserController.previewHomeAddress와 동일한 패턴 - 정확한 주소는 저장하지 않는다는
 * 최소 수집 원칙을 지키면서도, 후보 확인 화면에서는 사용자가 어디인지 알아볼 수 있어야 하기 때문).
 * 확정 목록 조회(getConfirmedPlaces)에서는 null일 수 있다(불필요한 API 호출 방지).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoutinePlaceResponse {
    private Long id;
    private RoutinePlaceStatus status;
    private String category;
    private List<String> routineDays;
    private LocalTime routineTimeRangeStart;
    private LocalTime routineTimeRangeEnd;
    private Integer occurrenceCount;
    private String previewAddress;
    private LocalDateTime lastDetectedAt;
    private LocalDateTime confirmedAt;

    /** 요일/시간대를 사용자가 직접 수정했는지 - true면 야간 재계산이 이 값을 덮어쓰지 않는다 */
    private boolean manualSchedule;
}
