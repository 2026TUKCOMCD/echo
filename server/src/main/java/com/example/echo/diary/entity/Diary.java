package com.example.echo.diary.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 일기 엔티티
 *
 * 하루 대화 내용을 AI가 일기 형식으로 요약한 결과를 저장
 * - 사용자당 하루 1개 (user_id + diary_date unique)
 * - 대화 종료 시마다 [기존 일기 + 새 대화]로 증분 재생성
 * - 생성 실패 시에도 status=FAILED로 흔적을 남김 (기존 content는 보존)
 */
@Entity
@Table(name = "diaries",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_diaries_user_date",
                columnNames = {"user_id", "diary_date"}
        ),
        indexes = {
                @Index(name = "idx_diaries_user_date", columnList = "user_id, diary_date")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Diary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "diary_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "diary_date", nullable = false)
    private LocalDate diaryDate;

    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "mood", length = 50)
    private String mood;

    @Column(name = "weather", length = 100)
    private String weather;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DiaryStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public Diary(Long userId, LocalDate diaryDate, String title, String content,
                 String mood, String weather, DiaryStatus status, String failureReason) {
        this.userId = userId;
        this.diaryDate = diaryDate;
        this.title = title;
        this.content = content;
        this.mood = mood;
        this.weather = weather;
        this.status = status;
        this.failureReason = failureReason;
    }

    /**
     * UPSERT용: 일기 생성 성공 반영
     */
    public void markSuccess(String content, String weather) {
        this.content = content;
        this.weather = weather;
        this.status = DiaryStatus.SUCCESS;
        this.failureReason = null;
    }

    /**
     * UPSERT용: 일기 생성 실패 반영
     *
     * 같은 날 이미 성공한 일기가 있으면 content를 보존한 채
     * 상태와 실패 사유만 갱신 (성공본 유실 방지)
     */
    public void markFailed(String reason) {
        this.status = DiaryStatus.FAILED;
        this.failureReason = reason;
    }
}
