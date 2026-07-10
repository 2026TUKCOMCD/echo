/*
 * OpenRouter 채팅 API 인증 설정
 * - Authorization: Bearer 헤더 자동 추가
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
}
