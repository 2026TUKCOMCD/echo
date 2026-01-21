package com.example.echo.prompt.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 프롬프트 생성에 필요한 컨텍스트 DTO
 *
 * 나중에 실제 UserContext(ContextService)가 완성되면
 * 해당 데이터를 이 DTO로 매핑하여 PromptService에 전달
 *
 * 현재는 임시 데이터로 테스트용으로 사용
 */
@Getter
@Builder
public class PromptContext {

    // ========== 사용자 기본 정보 ==========
    /**
     * 사용자 이름 (예: "홍길동")
     */
    private String userName;

    /**
     * 사용자 나이 (예: 75)
     */
    private Integer userAge;

    // ========== 건강 데이터 ==========
    /**
     * 오늘 걸음 수 (예: 3500)
     */
    private Integer steps;

    /**
     * 오늘 수면 시간 (예: 7.5)
     */
    private Double sleepHours;

    // ========== 날씨 정보 ==========
    /**
     * 오늘 날씨 (예: "맑음, 기온 15도")
     */
    private String weather;

    // ========== 대화 히스토리 ==========
    /**
     * 오늘의 대화 히스토리 (턴 단위)
     * 대화 시작 시 빈 리스트, 대화 진행하며 축적
     */
    private List<ConversationTurn> conversationHistory;
}
