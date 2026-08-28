/*
 * OpenAI Chat Completion API 응답 DTO
 *
 * 실제 AI 응답 텍스트: choices[0].message.content
 * API 문서: https://platform.openai.com/docs/api-reference/chat/object
 */
package com.example.echo.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ChatCompletionResponse {

    /** 응답 고유 ID */
    private String id;

    /** 객체 타입 (항상 "chat.completion") */
    private String object;

    /** 생성 시간 (Unix timestamp) */
    private Long created;

    /** 사용된 모델 */
    private String model;

    /** 생성된 응답 목록 (보통 1개) */
    private List<Choice> choices;

    /** 토큰 사용량 */
    private Usage usage;

    /** 생성된 응답 */
    @Getter
    @NoArgsConstructor
    public static class Choice {
        private Integer index;
        private Message message;

        @JsonProperty("finish_reason")
        private String finishReason;  // "stop", "length" 등
    }

    /** AI 응답 메시지 */
    @Getter
    @NoArgsConstructor
    public static class Message {
        private String role;     // 항상 "assistant"
        private String content;  // AI 생성 텍스트
    }

    /** 토큰 사용량 (비용 추적 및 프롬프트 캐싱 적중 여부 확인용) */
    @Getter
    @NoArgsConstructor
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;      // 입력 토큰

        @JsonProperty("completion_tokens")
        private Integer completionTokens;  // 출력 토큰

        @JsonProperty("total_tokens")
        private Integer totalTokens;       // 총 토큰

        /**
         * 프롬프트 캐싱으로 재사용된 입력 토큰 수.
         * 0보다 크면 캐시 적중 - promptTokens 대비 이 값의 비율이 캐싱 절감 효과.
         */
        @JsonProperty("cached_tokens")
        private Integer cachedTokens;

        /** 이번 요청에서 새로 캐시에 기록된 토큰 수 (보통 세션의 첫 요청에서만 관측됨) */
        @JsonProperty("cache_write_tokens")
        private Integer cacheWriteTokens;
    }
}
