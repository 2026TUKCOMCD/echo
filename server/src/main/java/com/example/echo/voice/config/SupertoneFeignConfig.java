package com.example.echo.voice.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class SupertoneFeignConfig {

    @Value("${supertone.api-key:}")
    private String apiKey;

    @Bean
    public RequestInterceptor supertoneRequestInterceptor() {
        return template -> template.header("x-sup-api-key", apiKey);
    }

    @Bean
    public feign.Request.Options supertoneRequestOptions() {
        return new feign.Request.Options(10_000, 30_000);
    }
}
