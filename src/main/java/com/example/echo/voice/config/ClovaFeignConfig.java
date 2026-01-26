/*
 * Clova Voice API 인증 설정
 * - X-NCP-APIGW-API-KEY-ID, X-NCP-APIGW-API-KEY 헤더 자동 추가
 *
 * 설명:
 * OpenAIFeignConfig 처럼
 * Clova 요청을 보낼 때, APIkey를 자동으로 부착해주는 기능
 *
 * 추가한 시기:2026.01.26
 *
 * 관여 Task: T3.5-1: TTSClient 구현 (8시간) #56
 */
package com.example.echo.voice.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class ClovaFeignConfig {

    @Value("${clova.api.client-id}")
    private String clientId;

    @Value("${clova.api.client-secret}")
    private String clientSecret;

    @Bean
    public RequestInterceptor clovaRequestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("X-NCP-APIGW-API-KEY-ID", clientId);
            requestTemplate.header("X-NCP-APIGW-API-KEY", clientSecret);
        };
    }
}
