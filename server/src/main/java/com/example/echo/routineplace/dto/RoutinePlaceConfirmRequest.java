package com.example.echo.routineplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * 루틴 장소 후보 확정 / 라벨·일정 수정 요청.
 *
 * routineDays/routineTimeRange*는 선택 항목이다 - 비워두면(null) 시스템이 감지한 값을 그대로
 * 두고, 값을 채워 보내면 사용자가 직접 고친 것으로 간주해 이후 야간 재계산이 덮어쓰지 않는다
 * (RoutinePlace.applyManualSchedule 참고).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoutinePlaceConfirmRequest {

    @NotBlank(message = "카테고리 라벨을 입력해주세요.")
    @Size(max = 50)
    private String category;

    private List<DayOfWeek> routineDays;

    private LocalTime routineTimeRangeStart;

    private LocalTime routineTimeRangeEnd;
}
