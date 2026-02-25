package com.example.echo.health.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * 건강 데이터 + 평가 결과를 포함하는 DTO
 *
 * 원시 건강 데이터(HealthData)에 7일 평균 및 평가 결과를 추가하여
 * 프롬프트 생성, 일기 생성 등에서 재사용 가능하도록 함
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrichedHealthData {

    // ========== 원시 데이터 (HealthData에서 복사) ==========

    /** 걸음 수 */
    private Integer steps;

    /** 수면 시간 (분) */
    private Integer sleepDurationMinutes;

    /** 취침 시간 */
    private LocalTime sleepStartTime;

    /** 기상 시간 */
    private LocalTime wakeUpTime;

    /** 운동 거리 (km) */
    private Double exerciseDistanceKm;

    /** 주요 운동 활동 */
    private String exerciseActivity;

    /** 활동 목록 (쉼표 구분) */
    private String activityList;

    // ========== 7일 평균 데이터 ==========

    /** 7일 평균 걸음 수 */
    private Double avgSteps;

    /** 7일 평균 수면 시간 (시간) */
    private Double avgSleepHours;

    /** 7일 평균 기상 시간 */
    private LocalTime avgWakeUpTime;

    // ========== 평가 결과 ==========

    /** 걸음 수 평가: "평소보다 많음", "평소와 비슷", "평소보다 적음", "데이터 없음" */
    private String stepsEvaluation;

    /** 수면 평가: "부족", "적당", "과도" */
    private String sleepEvaluation;

    /** 기상 시간 평가: "평소보다 일찍", "평소와 비슷", "평소보다 늦게", "데이터 없음" */
    private String wakeTimeEvaluation;

    // ========== 포맷팅된 문자열 (프롬프트용) ==========

    /** 포맷팅된 걸음 수: "5,000보" */
    private String stepsFormatted;

    /** 포맷팅된 수면 시간: "7시간 30분" */
    private String sleepDurationFormatted;

    /** 포맷팅된 취침 시간: "오후 11시 0분" */
    private String sleepStartTimeFormatted;

    /** 포맷팅된 기상 시간: "오전 7시 0분" */
    private String wakeUpTimeFormatted;

    /** 포맷팅된 운동 거리: "2.5km" */
    private String exerciseDistanceFormatted;

    // ========== 사용자 선호도 ==========

    /** 선호 수면 시간 (시간) */
    private Integer preferredSleepHours;

    /**
     * 원시 HealthData로부터 기본값 생성 (평가 결과 없이)
     */
    public static EnrichedHealthData fromHealthData(HealthData healthData) {
        if (healthData == null) {
            return EnrichedHealthData.builder().build();
        }

        return EnrichedHealthData.builder()
                .steps(healthData.getSteps())
                .sleepDurationMinutes(healthData.getSleepDurationMinutes())
                .sleepStartTime(healthData.getSleepStartTime())
                .wakeUpTime(healthData.getWakeUpTime())
                .exerciseDistanceKm(healthData.getExerciseDistanceKm())
                .exerciseActivity(healthData.getExerciseActivity())
                .activityList(healthData.getActivityList())
                .build();
    }
}
