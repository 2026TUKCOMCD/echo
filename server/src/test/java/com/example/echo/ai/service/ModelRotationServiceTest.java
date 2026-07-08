package com.example.echo.ai.service;

import com.example.echo.ai.config.OpenRouterChatProperties;
import com.example.echo.ai.exception.AIException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

    private ModelRotationService serviceAt(long epochDay) {
        OpenRouterChatProperties properties = new OpenRouterChatProperties();
        properties.setModels(MODELS);

        Instant instant = Instant.EPOCH.plusSeconds(epochDay * 24 * 60 * 60);
        Clock clock = Clock.fixed(instant, ZoneOffset.UTC);

        return new ModelRotationService(properties, clock);
    }

    @Test
    @DisplayName("day 0 -> 첫 번째 후보 모델")
    void currentModel_dayZero() {
        assertThat(serviceAt(0).currentModel()).isEqualTo(MODELS.get(0));
    }

    @Test
    @DisplayName("day = 후보 개수 -> 다시 첫 번째 후보로 순환")
    void currentModel_wrapsAroundAfterFullCycle() {
        assertThat(serviceAt(MODELS.size()).currentModel()).isEqualTo(MODELS.get(0));
    }

    @Test
    @DisplayName("day 2 -> 세 번째 후보 모델")
    void currentModel_midCycleDay() {
        assertThat(serviceAt(2).currentModel()).isEqualTo(MODELS.get(2));
    }

    @Test
    @DisplayName("후보 목록이 비어있으면 AIException")
    void currentModel_emptyModels_throws() {
        OpenRouterChatProperties properties = new OpenRouterChatProperties();
        properties.setModels(Collections.emptyList());
        ModelRotationService service = new ModelRotationService(properties, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        assertThatThrownBy(service::currentModel).isInstanceOf(AIException.class);
    }

    @Test
    @DisplayName("동일한 날짜면 여러 번 호출해도 같은 모델을 반환 (재시작에도 안전)")
    void currentModel_isStableForSameDate() {
        ModelRotationService service = serviceAt(5);
        String first = service.currentModel();
        String second = service.currentModel();

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("currentModelDisplayName - 후보 5개 모두 음성으로 자연스러운 표시 이름으로 변환")
    void currentModelDisplayName_forEachCandidate() {
        assertThat(serviceAt(0).currentModelDisplayName()).isEqualTo("GPT 5.5");           // openai/gpt-5.5
        assertThat(serviceAt(1).currentModelDisplayName()).isEqualTo("Claude Sonnet 5");   // anthropic/claude-sonnet-5
        assertThat(serviceAt(2).currentModelDisplayName()).isEqualTo("Gemini 3.1 Flash Lite"); // google/gemini-3.1-flash-lite
        assertThat(serviceAt(3).currentModelDisplayName()).isEqualTo("Claude Haiku 4.5");  // anthropic/claude-haiku-4.5
        assertThat(serviceAt(4).currentModelDisplayName()).isEqualTo("GPT 4o Mini");       // openai/gpt-4o-mini
    }
}
