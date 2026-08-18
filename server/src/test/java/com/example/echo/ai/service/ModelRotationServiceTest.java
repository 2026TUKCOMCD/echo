package com.example.echo.ai.service;

import com.example.echo.ai.config.OpenRouterChatProperties;
import com.example.echo.ai.exception.AIException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

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

    private ModelRotationService service(List<String> models) {
        OpenRouterChatProperties properties = new OpenRouterChatProperties();
        properties.setModels(models);
        return new ModelRotationService(properties);
    }

    @Test
    @DisplayName("pickModelForSession - 첫 호출은 첫 번째 후보 모델을 반환")
    void pickModelForSession_firstCallReturnsFirstCandidate() {
        assertThat(service(MODELS).pickModelForSession()).isEqualTo(MODELS.get(0));
    }

    @Test
    @DisplayName("pickModelForSession - 세션(호출)마다 후보를 순서대로 순환")
    void pickModelForSession_cyclesThroughCandidatesInOrder() {
        ModelRotationService service = service(MODELS);

        for (String expected : MODELS) {
            assertThat(service.pickModelForSession()).isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("pickModelForSession - 후보 개수만큼 호출 후 다시 처음 후보로 순환")
    void pickModelForSession_wrapsAroundAfterFullCycle() {
        ModelRotationService service = service(MODELS);

        for (int i = 0; i < MODELS.size(); i++) {
            service.pickModelForSession();
        }

        assertThat(service.pickModelForSession()).isEqualTo(MODELS.get(0));
    }

    @Test
    @DisplayName("pickModelForSession - 후보 목록이 비어있으면 AIException")
    void pickModelForSession_emptyModels_throws() {
        ModelRotationService service = service(Collections.emptyList());

        assertThatThrownBy(service::pickModelForSession).isInstanceOf(AIException.class);
    }

    @Test
    @DisplayName("pickModelForSession - 새 인스턴스는 항상 첫 번째 후보부터 다시 시작 (서버 재시작 시 처음부터 순환)")
    void pickModelForSession_freshInstanceRestartsFromFirst() {
        ModelRotationService first = service(MODELS);
        first.pickModelForSession();
        first.pickModelForSession();

        ModelRotationService fresh = service(MODELS);
        assertThat(fresh.pickModelForSession()).isEqualTo(MODELS.get(0));
    }
}
