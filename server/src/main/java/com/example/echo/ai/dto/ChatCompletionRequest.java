/*
 * OpenAI Chat Completion API 요청 DTO
 *
 * API 문서: https://platform.openai.com/docs/api-reference/chat/create
 */
package com.example.echo.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChatCompletionRequest {

    /** 사용할 모델 (예: gpt-4o-mini) */
    private String model;

    /** 대화 메시지 목록 (system, user, assistant 역할) */
    private List<Message> messages;

    /** 창의성 조절 (0.0=결정적, 1.0=창의적) */
    private Double temperature;

    /** 응답 최대 토큰 수 */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /**
     * 대화 메시지 단위
     *
     * role 종류:
     * - system: AI 페르소나/규칙 정의
     * - user: 사용자 발화
     * - assistant: AI 응답
     */
    @Getter
    @Builder
    public static class Message {
        private String role;
        private String content;
    }
}
