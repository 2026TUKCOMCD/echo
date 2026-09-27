/*
 * OpenRouter 채팅 API 인증 설정
 * - Authorization: Bearer 헤더 자동 추가
 * - Request.Options: connect/read 타임아웃 명시 (기존엔 미설정 - Feign 기본값에 암묵적으로 의존했음)
 */
package com.example.echo.ai.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class OpenRouterFeignConfig {

    @Value("${openrouter.api.key}")
    private String apiKey;

    @Bean
    public RequestInterceptor openRouterRequestInterceptor() {
        return requestTemplate -> requestTemplate.header("Authorization", "Bearer " + apiKey);
    }

    /**
     * connect 10s(다른 Feign 클라이언트와 동일), read 95s.
     *
     * 이 클라이언트는 블로킹 호출(createChatCompletion - /start 폴백, /message 폴백, 일기·기억 추출)과
     * 스트리밍 호출(createChatCompletionStream)이 공유한다. 스트리밍 경로는 이미 애플리케이션 레벨
     * 타임아웃(conversation.stream.llm-timeout-seconds=90, SpeechStreamPipeline.await())으로 한 번
     * 더 보호되므로, 여기 read 타임아웃이 그보다 먼저 끊어버리지 않도록 90s보다 여유 있게 95s로 잡는다.
     * 블로킹 호출은 이 값이 유일한 방어선이 된다.
     */
    @Bean
    public feign.Request.Options openRouterRequestOptions() {
        return new feign.Request.Options(10_000, 95_000);
    }
}
