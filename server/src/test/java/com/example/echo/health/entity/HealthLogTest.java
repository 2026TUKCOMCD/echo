package com.example.echo.health.entity;

import com.example.echo.health.dto.HealthData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class HealthLogTest {

    @Test
    @DisplayName("Builder - 모든 필드가 정상적으로 설정됨")
    void builder_allFields() {
        // Given
        Long userId = 1L;
        LocalDate recordedDate = LocalDate.of(2024, 1, 15);
        Integer steps = 8000;
        Integer sleepDurationMinutes = 420;
        LocalTime sleepStartTime = LocalTime.of(23, 0);
        LocalTime wakeUpTime = LocalTime.of(7, 0);
        Double exerciseDistanceKm = 3.5;
        String exerciseActivity = "아침 산책";
        String activityList = "걷기,산책";

        // When
        HealthLog healthLog = HealthLog.builder()
                .userId(userId)
                .recordedDate(recordedDate)
                .steps(steps)
                .sleepDurationMinutes(sleepDurationMinutes)
                .sleepStartTime(sleepStartTime)
                .wakeUpTime(wakeUpTime)
                .exerciseDistanceKm(exerciseDistanceKm)
                .exerciseActivity(exerciseActivity)
                .activityList(activityList)
                .build();

        // Then
        assertThat(healthLog.getUserId()).isEqualTo(userId);
        assertThat(healthLog.getRecordedDate()).isEqualTo(recordedDate);
        assertThat(healthLog.getSteps()).isEqualTo(steps);
        assertThat(healthLog.getSleepDurationMinutes()).isEqualTo(sleepDurationMinutes);
        assertThat(healthLog.getSleepStartTime()).isEqualTo(sleepStartTime);
        assertThat(healthLog.getWakeUpTime()).isEqualTo(wakeUpTime);
        assertThat(healthLog.getExerciseDistanceKm()).isEqualTo(exerciseDistanceKm);
        assertThat(healthLog.getExerciseActivity()).isEqualTo(exerciseActivity);
        assertThat(healthLog.getActivityList()).isEqualTo(activityList);
    }

    @Test
    @DisplayName("toHealthData - HealthLog를 HealthData DTO로 변환")
    void toHealthData() {
        // Given
        HealthLog healthLog = HealthLog.builder()
                .userId(1L)
                .recordedDate(LocalDate.now())
                .steps(5000)
                .sleepDurationMinutes(390)
                .sleepStartTime(LocalTime.of(22, 30))
                .wakeUpTime(LocalTime.of(6, 0))
                .exerciseDistanceKm(2.0)
                .exerciseActivity("조깅")
                .activityList("조깅,스트레칭")
                .build();

        // When
        HealthData healthData = healthLog.toHealthData();

        // Then
        assertThat(healthData.getSteps()).isEqualTo(5000);
        assertThat(healthData.getSleepDurationMinutes()).isEqualTo(390);
        assertThat(healthData.getSleepStartTime()).isEqualTo(LocalTime.of(22, 30));
        assertThat(healthData.getWakeUpTime()).isEqualTo(LocalTime.of(6, 0));
        assertThat(healthData.getExerciseDistanceKm()).isEqualTo(2.0);
        assertThat(healthData.getExerciseActivity()).isEqualTo("조깅");
        assertThat(healthData.getActivityList()).isEqualTo("조깅,스트레칭");
    }

    @Test
    @DisplayName("fromHealthData - HealthData DTO에서 HealthLog 엔티티 생성")
    void fromHealthData() {
        // Given
        Long userId = 1L;
        LocalDate date = LocalDate.of(2024, 1, 15);
        HealthData healthData = HealthData.builder()
                .steps(6000)
                .sleepDurationMinutes(480)
                .sleepStartTime(LocalTime.of(23, 0))
                .wakeUpTime(LocalTime.of(7, 0))
                .exerciseDistanceKm(1.5)
                .exerciseActivity("산책")
                .activityList("산책")
                .build();

        // When
        HealthLog healthLog = HealthLog.fromHealthData(userId, date, healthData);

        // Then
        assertThat(healthLog.getUserId()).isEqualTo(userId);
        assertThat(healthLog.getRecordedDate()).isEqualTo(date);
        assertThat(healthLog.getSteps()).isEqualTo(6000);
        assertThat(healthLog.getSleepDurationMinutes()).isEqualTo(480);
        assertThat(healthLog.getSleepStartTime()).isEqualTo(LocalTime.of(23, 0));
        assertThat(healthLog.getWakeUpTime()).isEqualTo(LocalTime.of(7, 0));
        assertThat(healthLog.getExerciseDistanceKm()).isEqualTo(1.5);
        assertThat(healthLog.getExerciseActivity()).isEqualTo("산책");
        assertThat(healthLog.getActivityList()).isEqualTo("산책");
    }

    @Test
    @DisplayName("toHealthData - null 필드도 정상 변환")
    void toHealthData_withNullFields() {
        // Given
        HealthLog healthLog = HealthLog.builder()
                .userId(1L)
                .recordedDate(LocalDate.now())
                .steps(null)
                .sleepDurationMinutes(null)
                .sleepStartTime(null)
                .wakeUpTime(null)
                .exerciseDistanceKm(null)
                .exerciseActivity(null)
                .activityList(null)
                .build();

        // When
        HealthData healthData = healthLog.toHealthData();

        // Then
        assertThat(healthData.getSteps()).isNull();
        assertThat(healthData.getSleepDurationMinutes()).isNull();
        assertThat(healthData.getSleepStartTime()).isNull();
        assertThat(healthData.getWakeUpTime()).isNull();
        assertThat(healthData.getExerciseDistanceKm()).isNull();
        assertThat(healthData.getExerciseActivity()).isNull();
        assertThat(healthData.getActivityList()).isNull();
    }
}
