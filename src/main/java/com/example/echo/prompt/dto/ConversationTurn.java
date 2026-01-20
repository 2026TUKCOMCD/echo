package com.example.echo.prompt.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 대화 턴 DTO
 *
 * 하나의 대화 턴 = 사용자 발화 + AI 응답
 * 대화 히스토리는 이 턴들의 리스트로 구성됨
 */
@Getter
@Builder
public class ConversationTurn {

    /**
     * 사용자 발화 내용
     * 첫 인사(AI가 먼저 말할 때)는 null일 수 있음
     */
    private String userMessage;

    /**
     * AI 응답 내용
     */
    private String aiResponse;

    /**
     * 해당 턴의 시간
     */
    private LocalDateTime timestamp;

    /**
     * 대화 히스토리 포맷팅용 문자열 변환
     * 예: "사용자: 안녕하세요\nAI: 안녕하세요, 홍길동님!"
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (userMessage != null) {
            sb.append("사용자: ").append(userMessage).append("\n");
        }
        sb.append("AI: ").append(aiResponse);
        return sb.toString();
    }
}
