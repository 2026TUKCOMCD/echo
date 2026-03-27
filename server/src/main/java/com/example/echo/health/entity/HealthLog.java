package com.example.echo.health.entity;

import com.example.echo.health.dto.HealthData;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 건강 데이터 로그 엔티티
 *
 * 사용자의 일별 건강 데이터를 저장
 * - 걸음 수, 수면 시간, 운동 거리/활동 등 기록
 * - 7일 평균 계산 및 대화용 건강 정보 조회에 사용
 */
@Entity
@Table(name = "health_logs",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_health_logs_user_date",
                columnNames = {"user_id", "recorded_date"}
        ),
        indexes = {
                @Index(name = "idx_health_logs_user_date", columnList = "user_id, recorded_date")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HealthLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recorded_date", nullable = false)
    private LocalDate recordedDate;

    @Column(name = "steps")
    private Integer steps;

    @Column(name = "sleep_duration_minutes")
    private Integer sleepDurationMinutes;

    @Column(name = "sleep_start_time")
    private LocalTime sleepStartTime;

    @Column(name = "wake_up_time")
    private LocalTime wakeUpTime;

    @Column(name = "exercise_distance_km")
    private Double exerciseDistanceKm;

    @Column(name = "exercise_activity", length = 100)
    private String exerciseActivity;

    @Column(name = "activity_list", length = 500)
    private String activityList;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public HealthLog(Long userId, LocalDate recordedDate, Integer steps,
                     Integer sleepDurationMinutes, LocalTime sleepStartTime,
                     LocalTime wakeUpTime, Double exerciseDistanceKm,
                     String exerciseActivity, String activityList) {
        this.userId = userId;
        this.recordedDate = recordedDate;
        this.steps = steps;
        this.sleepDurationMinutes = sleepDurationMinutes;
        this.sleepStartTime = sleepStartTime;
        this.wakeUpTime = wakeUpTime;
        this.exerciseDistanceKm = exerciseDistanceKm;
        this.exerciseActivity = exerciseActivity;
        this.activityList = activityList;
    }

    /**
     * HealthLog를 HealthData DTO로 변환
     */
    public HealthData toHealthData() {
        return HealthData.builder()
                .steps(this.steps)
                .sleepDurationMinutes(this.sleepDurationMinutes)
                .sleepStartTime(this.sleepStartTime)
                .wakeUpTime(this.wakeUpTime)
                .exerciseDistanceKm(this.exerciseDistanceKm)
                .exerciseActivity(this.exerciseActivity)
                .activityList(this.activityList)
                .build();
    }

    /**
     * HealthData DTO에서 HealthLog 엔티티 생성
     */
    public static HealthLog fromHealthData(Long userId, LocalDate recordedDate, HealthData healthData) {
        return HealthLog.builder()
                .userId(userId)
                .recordedDate(recordedDate)
                .steps(healthData.getSteps())
                .sleepDurationMinutes(healthData.getSleepDurationMinutes())
                .sleepStartTime(healthData.getSleepStartTime())
                .wakeUpTime(healthData.getWakeUpTime())
                .exerciseDistanceKm(healthData.getExerciseDistanceKm())
                .exerciseActivity(healthData.getExerciseActivity())
                .activityList(healthData.getActivityList())
                .build();
    }

    /**
     * UPSERT용 업데이트 메서드
     *
     * 기존 HealthLog 엔티티의 건강 데이터를 새 데이터로 갱신
     * 같은 날짜에 대해 데이터가 이미 존재할 경우 사용
     *
     * @param data 새 건강 데이터
     */
    public void update(HealthData data) {
        this.steps = data.getSteps();
        this.sleepDurationMinutes = data.getSleepDurationMinutes();
        this.sleepStartTime = data.getSleepStartTime();
        this.wakeUpTime = data.getWakeUpTime();
        this.exerciseDistanceKm = data.getExerciseDistanceKm();
        this.exerciseActivity = data.getExerciseActivity();
        this.activityList = data.getActivityList();
    }
}
