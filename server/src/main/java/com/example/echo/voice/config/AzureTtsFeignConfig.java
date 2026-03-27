/*
 * Azure Cognitive Services TTS 인증 설정
 * - Ocp-Apim-Subscription-Key 헤더 자동 추가
 * - 출력 형식: audio-16khz-128kbitrate-mono-mp3
 */
package com.example.echo.voice.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class AzureTtsFeignConfig {

    @Value("${azure.tts.api-key}")
    private String apiKey;

    @Bean
    public RequestInterceptor azureTtsRequestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("Ocp-Apim-Subscription-Key", apiKey);
            requestTemplate.header("X-Microsoft-OutputFormat", "audio-16khz-128kbitrate-mono-mp3");
        };
    }

    @Bean
    public feign.Request.Options requestOptions() {
        return new feign.Request.Options(10_000, 30_000); // connect 10s, read 30s
    }
}
