/*
 * OpenAI 임베딩 설정값 (application.yaml: openai.embedding.*)
 *
 * model/dimensions를 바꾸면 저장된 벡터와 비교할 수 없게 되므로,
 * 벡터마다 modelTag()를 함께 저장해 두고 백필이 달라진 것을 감지해 다시 만든다.
 */
package com.example.echo.memory.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "openai.embedding")
public class EmbeddingProperties {

    private String model;
    private Integer dimensions;
    private Integer timeoutMs;

    /** memories.embedding_model에 저장되는 값 (예: text-embedding-3-small@512) */
    public String modelTag() {
        return model + "@" + dimensions;
    }
}
