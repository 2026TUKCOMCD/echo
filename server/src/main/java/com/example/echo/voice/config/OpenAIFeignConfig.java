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
        return requestTemplate -> requestTemplate.header("Authorization", "Bearer " + apiKey);
    }

    @Bean
    public feign.Request.Options openAIRequestOptions() {
        return new feign.Request.Options(10_000, 30_000); // connect 10s, read 30s
    }
}
