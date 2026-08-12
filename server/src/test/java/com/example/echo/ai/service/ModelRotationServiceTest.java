package com.example.echo.ai.service;

import com.example.echo.ai.config.OpenRouterChatProperties;
import com.example.echo.ai.exception.AIException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelRotationServiceTest {

    private static final List<String> MODELS = List.of(
            "openai/gpt-5.5",
            "anthropic/claude-sonnet-5",
            "google/gemini-3.1-flash-lite",
            "anthropic/claude-haiku-4.5",
            "openai/gpt-4o-mini"
    );

    private ModelRotationService serviceWithFixedIndex(List<String> models, int fixedIndex) {
        OpenRouterChatProperties properties = new OpenRouterChatProperties();
        properties.setModels(models);

        Random fixedRandom = new Random() {
            @Override
            public int nextInt(int bound) {
                return fixedIndex;
            }
        };

        return new ModelRotationService(properties, fixedRandom);
    }

    @Test
    @DisplayName("pickModelForSession - 무작위 선택 결과가 후보 인덱스 0에 대응하는 모델을 반환")
    void pickModelForSession_firstCandidate() {
        assertThat(serviceWithFixedIndex(MODELS, 0).pickModelForSession()).isEqualTo(MODELS.get(0));
    }

    @Test
    @DisplayName("pickModelForSession - 무작위 선택 결과가 마지막 후보 인덱스에 대응하는 모델을 반환")
    void pickModelForSession_lastCandidate() {
        assertThat(serviceWithFixedIndex(MODELS, MODELS.size() - 1).pickModelForSession())
                .isEqualTo(MODELS.get(MODELS.size() - 1));
    }

    @Test
    @DisplayName("pickModelForSession - 후보 목록이 비어있으면 AIException")
    void pickModelForSession_emptyModels_throws() {
        OpenRouterChatProperties properties = new OpenRouterChatProperties();
        properties.setModels(Collections.emptyList());
        ModelRotationService service = new ModelRotationService(properties, new Random());

        assertThatThrownBy(service::pickModelForSession).isInstanceOf(AIException.class);
    }

    @Test
    @DisplayName("pickModelForSession - 여러 세션에 걸쳐 서로 다른 모델이 선택될 수 있다 (세션마다 재선택)")
    void pickModelForSession_variesAcrossSessions() {
        OpenRouterChatProperties properties = new OpenRouterChatProperties();
        properties.setModels(MODELS);
        ModelRotationService service = new ModelRotationService(properties, new Random());

        // 실제 무작위 선택기로 충분히 반복하면 5개 후보 중 2개 이상은 나와야 한다 (통계적 검증)
        long distinctCount = java.util.stream.IntStream.range(0, 50)
                .mapToObj(i -> service.pickModelForSession())
                .distinct()
                .count();

        assertThat(distinctCount).isGreaterThan(1);
    }

    @Test
    @DisplayName("displayName - 후보 5개 모두 음성으로 자연스러운 표시 이름으로 변환")
    void displayName_forEachCandidate() {
        ModelRotationService service = serviceWithFixedIndex(MODELS, 0);

        assertThat(service.displayName("openai/gpt-5.5")).isEqualTo("GPT 5.5");
        assertThat(service.displayName("anthropic/claude-sonnet-5")).isEqualTo("Claude Sonnet 5");
        assertThat(service.displayName("google/gemini-3.1-flash-lite")).isEqualTo("Gemini 3.1 Flash Lite");
        assertThat(service.displayName("anthropic/claude-haiku-4.5")).isEqualTo("Claude Haiku 4.5");
        assertThat(service.displayName("openai/gpt-4o-mini")).isEqualTo("GPT 4o Mini");
    }
}
