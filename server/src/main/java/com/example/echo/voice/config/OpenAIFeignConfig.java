/*
"출입증 자동으로 달아주는 기계"
OpenAi 요청을 보낼 때, APIkey를 자동으로 부착해주는 기능
*/
package com.example.echo.voice.config;

import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

@Slf4j
public class OpenAIFeignConfig {

    @Value("${openai.api.key}")
    private String apiKey;

    @Bean
    public RequestInterceptor openAIRequestInterceptor() {
        System.out.println("=== OpenAIFeignConfig initialized, API Key (last 4): " + (apiKey != null && apiKey.length() > 4 ? "..." + apiKey.substring(apiKey.length() - 4) : "NULL or SHORT") + " ===");
        return requestTemplate -> {
            System.out.println("=== Interceptor called, API Key (last 4): " + (apiKey != null && apiKey.length() > 4 ? "..." + apiKey.substring(apiKey.length() - 4) : "NULL or SHORT") + " ===");
            requestTemplate.header("Authorization", "Bearer " + apiKey);
        };
    }
}
