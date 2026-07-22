package com.example.echo.memory.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 장기기억 엔티티
 *
 * 어르신이 대화 중 들려주신 영구 유효한 자전적 기억(옛날 이야기, 가족 사실, 반복 습관)을 저장
 * - 일기(Diary)가 "오늘 무슨 일이 있었나"라면, 이 엔티티는 "이 분은 어떤 분인가"를 담는다
 * - 대화 종료 시마다 [기존 기억 전체 + 이번 대화]를 AI가 통합해 전량 교체 (병합/구체화 포함)
 * - 대화 시작 시 시스템 프롬프트 뒤에 주입되어 회상 대화의 앵커로 사용됨
 *
 * lifePeriod/topic을 enum이 아닌 String으로 둔 이유:
 * 코드 분기에 쓰이지 않고 프롬프트 텍스트로만 소비되므로, AI 어휘가 흔들릴 때
 * enum은 UNKNOWN으로 뭉개져 오히려 정보를 잃는다. 허용 어휘는 MEMORY 프롬프트가 통제한다.
 */
@Entity
@Table(name = "memories",
        indexes = {
                @Index(name = "idx_memories_user", columnList = "user_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Memory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "memory_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 기억의 시기 (유년기/학창시절/청년기/중년기/노년기/최근/시기미상)
     */
    @Column(name = "life_period", length = 30)
    private String lifePeriod;

    /**
     * 기억의 주제 (가족/직업/장소/사건/취미/습관/기타)
     */
    @Column(name = "topic", length = 30)
    private String topic;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    /**
     * 대화 연결용 핵심 단어 (쉼표 구분)
     */
    @Column(name = "tags", length = 200)
    private String tags;

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
    public Memory(Long userId, String lifePeriod, String topic, String content, String tags) {
        this.userId = userId;
        this.lifePeriod = lifePeriod;
        this.topic = topic;
        this.content = content;
        this.tags = tags;
    }
}
