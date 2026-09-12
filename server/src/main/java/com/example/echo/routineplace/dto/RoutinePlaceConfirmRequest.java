package com.example.echo.routineplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 루틴 장소 후보 확정 / 라벨 수정 요청.
 * 요일은 사용자가 직접 고르지 않고 감지된 값을 그대로 쓰므로 이 요청에는 포함하지 않는다
 * (routineDays는 RoutinePlaceDetectionService가 계속 갱신).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoutinePlaceConfirmRequest {

    @NotBlank(message = "카테고리 라벨을 입력해주세요.")
    @Size(max = 50)
    private String category;
}
