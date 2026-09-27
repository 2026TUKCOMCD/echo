package com.example.echo.ai.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatCompletionResponseTest {

    // Feign 디코더가 쓰는 스프링 기본 설정처럼 모르는 필드는 무시
    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    @DisplayName("캐시 토큰은 OpenRouter 실제 응답처럼 usage.prompt_tokens_details 안에서 읽는다")
    void readsCachedTokensFromPromptTokensDetails() throws Exception {
        // 2026-09-27 OpenRouter(openai/gpt-5.6-terra) 실제 응답의 usage 부분
        String json = """
                {"choices": [{"message": {"role": "assistant", "content": "안녕하세요"}}],
                 "usage": {"prompt_tokens": 5724, "completion_tokens": 15, "total_tokens": 5739, "cost": 0.0013302,
                           "prompt_tokens_details": {"cached_tokens": 5721, "cache_write_tokens": 0, "audio_tokens": 0}}}
                """;

        ChatCompletionResponse.Usage usage = objectMapper.readValue(json, ChatCompletionResponse.class).getUsage();

        assertThat(usage.getPromptTokens()).isEqualTo(5724);
        assertThat(usage.getCachedTokens()).isEqualTo(5721);
        assertThat(usage.getCacheWriteTokens()).isZero();
    }

    @Test
    @DisplayName("prompt_tokens_details가 없으면 캐시 토큰은 null (미적중으로 처리)")
    void cachedTokensNullWithoutDetails() throws Exception {
        String json = """
                {"usage": {"prompt_tokens": 100, "completion_tokens": 5, "total_tokens": 105}}
                """;

        ChatCompletionResponse.Usage usage = objectMapper.readValue(json, ChatCompletionResponse.class).getUsage();

        assertThat(usage.getCachedTokens()).isNull();
    }
}
