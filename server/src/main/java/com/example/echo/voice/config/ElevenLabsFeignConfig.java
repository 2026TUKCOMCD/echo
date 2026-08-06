package com.example.echo.voice.config;

import com.example.echo.voice.client.ElevenLabsErrorDecoder;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class ElevenLabsFeignConfig {

    @Value("${elevenlabs.api-key:}")
    private String apiKey;

    @Bean
    public RequestInterceptor elevenLabsRequestInterceptor() {
        return template -> template.header("xi-api-key", apiKey);
    }

    @Bean
    public feign.Request.Options elevenLabsRequestOptions() {
        return new feign.Request.Options(10_000, 30_000);
    }

    @Bean
    public ErrorDecoder elevenLabsErrorDecoder() {
        return new ElevenLabsErrorDecoder();
    }
}
