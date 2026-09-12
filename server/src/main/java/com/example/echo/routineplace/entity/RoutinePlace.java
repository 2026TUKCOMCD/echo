package com.example.echo.routineplace.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 확정(또는 후보) 루틴 방문 장소.
 *
 * - 최소 수집 원칙: 정확한 상호명·지번주소는 저장하지 않는다. 좌표+반경+사용자가 붙인
 *   카테고리 라벨+루틴 요일/시간대만 저장하고, 주소가 필요하면(후보 확인 화면 등) 그때그때
 *   역지오코딩한다(RoutinePlaceService.getCandidates 참고).
 * - status가 SUGGESTED인 동안은 category/routineDays가 비어있을 수 있다(아직 사용자 확인 전).
 * - 사용자가 CONFIRMED로 확정하기 전까지는 대화 프롬프트에 노출되지 않는다.
 */
@Entity
@Table(name = "routine_places",
        indexes = {
                @Index(name = "idx_routine_places_user", columnList = "user_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoutinePlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "radius_meters", nullable = false)
    private Double radiusMeters;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoutinePlaceStatus status;

    /** 사용자가 붙인 카테고리 라벨 (예: "회사", "병원", "복지관"). 확정 전엔 null. */
    @Column(name = "category", length = 50)
    private String category;

    /** 루틴 요일, CSV 형식 (예: "MON,WED,FRI") - Memory.tags와 동일한 컨벤션 */
    @Column(name = "routine_days", length = 30)
    private String routineDays;

    @Column(name = "routine_time_range_start")
    private LocalTime routineTimeRangeStart;

    @Column(name = "routine_time_range_end")
    private LocalTime routineTimeRangeEnd;

    /** 최근 감지 윈도우 내 발견된 발생 횟수 (감지 신뢰도 참고용) */
    @Column(name = "occurrence_count")
    private Integer occurrenceCount;

    @Column(name = "last_detected_at")
    private LocalDateTime lastDetectedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /**
     * 사용자가 요일/시간대를 직접 수정했는지 여부.
     * true가 되면 RoutinePlaceDetectionService의 야간 재계산이 이 장소의 요일/시간대를
     * 더 이상 덮어쓰지 않는다(사용자가 직접 고친 값을 존중).
     */
    @Column(name = "manual_schedule", nullable = false)
    private boolean manualSchedule = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public static final double DEFAULT_RADIUS_METERS = 100.0;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public RoutinePlace(Long userId, Double latitude, Double longitude, Double radiusMeters,
                         RoutinePlaceStatus status, String category, String routineDays,
                         LocalTime routineTimeRangeStart, LocalTime routineTimeRangeEnd,
                         Integer occurrenceCount, LocalDateTime lastDetectedAt, LocalDateTime confirmedAt) {
        this.userId = userId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusMeters = radiusMeters != null ? radiusMeters : DEFAULT_RADIUS_METERS;
        this.status = status != null ? status : RoutinePlaceStatus.SUGGESTED;
        this.category = category;
        this.routineDays = routineDays;
        this.routineTimeRangeStart = routineTimeRangeStart;
        this.routineTimeRangeEnd = routineTimeRangeEnd;
        this.occurrenceCount = occurrenceCount;
        this.lastDetectedAt = lastDetectedAt;
        this.confirmedAt = confirmedAt;
    }

    /**
     * 감지 스케줄러가 매일 새로 계산한 통계로 갱신.
     * 이미 CONFIRMED인 장소도 요일/시간대가 최신 패턴을 반영하도록 계속 갱신되지만,
     * 사용자가 직접 요일/시간대를 수정한(manualSchedule=true) 장소는 건드리지 않는다.
     */
    public void refreshStats(int occurrenceCount, String routineDays,
                              LocalTime rangeStart, LocalTime rangeEnd, LocalDateTime detectedAt) {
        this.occurrenceCount = occurrenceCount;
        this.lastDetectedAt = detectedAt;
        if (!manualSchedule) {
            this.routineDays = routineDays;
            this.routineTimeRangeStart = rangeStart;
            this.routineTimeRangeEnd = rangeEnd;
        }
    }

    /** 후보를 사용자가 확정 - 카테고리 라벨과 함께 상태를 CONFIRMED로 전환 */
    public void confirm(String category) {
        this.category = category;
        this.status = RoutinePlaceStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
    }

    /** 확정된 장소의 라벨만 수정 */
    public void updateCategory(String category) {
        this.category = category;
    }

    /**
     * 사용자가 요일/시간대를 직접 수정 - 이후 감지 스케줄러가 이 값을 덮어쓰지 않는다.
     * null로 넘긴 필드는 기존 값을 유지한다(요일만 고치고 시간대는 그대로 두는 식의 부분 수정 허용).
     */
    public void applyManualSchedule(String routineDays, LocalTime rangeStart, LocalTime rangeEnd) {
        if (routineDays != null) {
            this.routineDays = routineDays;
        }
        if (rangeStart != null) {
            this.routineTimeRangeStart = rangeStart;
        }
        if (rangeEnd != null) {
            this.routineTimeRangeEnd = rangeEnd;
        }
        this.manualSchedule = true;
    }

    /** 후보를 거절 - 같은 좌표는 이후 재제안되지 않는다(RoutinePlaceDetectionService 참고) */
    public void dismiss() {
        this.status = RoutinePlaceStatus.DISMISSED;
    }
}
