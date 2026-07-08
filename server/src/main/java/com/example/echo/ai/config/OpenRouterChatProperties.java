/*
 * OpenRouter 채팅 설정값 (application.yaml: openrouter.chat.*)
 *
 * models: 매일 로테이션할 후보 모델 목록 (provider/model 형식)
 */
package com.example.echo.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "openrouter.chat")
public class OpenRouterChatProperties {

    private List<String> models;
    private Double temperature;
    private Integer maxTokens;
}
