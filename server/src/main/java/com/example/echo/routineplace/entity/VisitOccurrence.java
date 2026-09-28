package com.example.echo.routineplace.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 루틴 방문 장소 패턴 감지를 위한 원재료(임시) 데이터.
 *
 * - 상호명·주소는 저장하지 않는다(최소 수집 원칙). 좌표+요일+시간대만 저장해
 *   {@link RoutinePlaceDetectionService}가 다일간 반복 패턴을 찾는 데만 쓴다.
 * - 최근 몇 주만 보관하고 그 이전 데이터는 자동 삭제된다(보관 기간은
 *   RoutinePlaceDetectionService.WINDOW_WEEKS 참고).
 * - 집으로 판정된 방문은 애초에 기록하지 않는다(VisitOccurrenceRecordingService에서 필터링).
 */
@Entity
@Table(name = "visit_occurrences",
        indexes = {
                @Index(name = "idx_visit_occurrences_user_date", columnList = "user_id, visit_date")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitOccurrence {

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

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "visit_start_time")
    private LocalTime visitStartTime;

    @Column(name = "visit_end_time")
    private LocalTime visitEndTime;

    @Column(name = "stay_duration_minutes")
    private Integer stayDurationMinutes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public VisitOccurrence(Long userId, Double latitude, Double longitude, LocalDate visitDate,
                            LocalTime visitStartTime, LocalTime visitEndTime, Integer stayDurationMinutes) {
        this.userId = userId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.visitDate = visitDate;
        this.dayOfWeek = visitDate != null ? visitDate.getDayOfWeek() : null;
        this.visitStartTime = visitStartTime;
        this.visitEndTime = visitEndTime;
        this.stayDurationMinutes = stayDurationMinutes;
    }
}
