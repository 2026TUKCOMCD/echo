/*
 * Clova Voice API 인증 설정
 * - X-NCP-APIGW-API-KEY-ID, X-NCP-APIGW-API-KEY 헤더 자동 추가
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
