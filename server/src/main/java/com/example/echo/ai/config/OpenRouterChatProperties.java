/*
 * OpenRouter 채팅 설정값 (application.yaml: openrouter.chat.*)
 *
 * model: 사용할 모델 (provider/model 형식, 단일 모델 고정)
 */
package com.example.echo.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "openrouter.chat")
public class OpenRouterChatProperties {

    private String model;
    private Double temperature;
    private Integer maxTokens;
}
