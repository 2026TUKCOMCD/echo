/*
 * OpenRouter 채팅 모델 매일 로테이션
 *
 * 선택 방식: 날짜 기반 stateless 선택 (LocalDate.toEpochDay() % 후보 개수)
 * - 인메모리 인덱스를 두지 않음 -> 서버가 낮에 재시작돼도 같은 날엔 항상 같은 모델
 * - @Scheduled 잡은 자정에 현재 모델을 로그로 남기는 관찰용 역할이며,
 *   실제 선택은 매 요청마다 재계산되므로 스케줄러가 못 돌아도 정확성에 영향 없음
 */
package com.example.echo.ai.service;

import com.example.echo.ai.config.OpenRouterChatProperties;
import com.example.echo.ai.exception.AIException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRotationService {

    private final OpenRouterChatProperties chatProperties;
    private final Clock clock;

    /**
     * 오늘 사용할 모델을 반환한다. (예: "anthropic/claude-sonnet-5")
     */
    public String currentModel() {
        List<String> models = chatProperties.getModels();
        if (models == null || models.isEmpty()) {
            throw new AIException("openrouter.chat.models 설정이 비어 있습니다.");
        }

        long day = LocalDate.now(clock).toEpochDay();
        int index = Math.floorMod(day, models.size());
        return models.get(index);
    }

    /**
     * 오늘의 모델을 사람이 읽기 좋은(음성으로 발화 가능한) 이름으로 반환한다.
     * 예: "anthropic/claude-sonnet-5" -> "Claude Sonnet 5", "openai/gpt-5.5" -> "GPT 5.5"
     */
    public String currentModelDisplayName() {
        return toDisplayName(currentModel());
    }

    private static String toDisplayName(String modelId) {
        String slug = modelId.contains("/") ? modelId.substring(modelId.indexOf('/') + 1) : modelId;
        String[] parts = slug.split("-");

        StringBuilder displayName = new StringBuilder();
        for (String part : parts) {
            if (displayName.length() > 0) {
                displayName.append(' ');
            }
            displayName.append("gpt".equalsIgnoreCase(part) ? "GPT" : capitalize(part));
        }
        return displayName.toString();
    }

    private static String capitalize(String word) {
        if (word.isEmpty()) {
            return word;
        }
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void logDailyRotation() {
        log.info("OpenRouter 오늘의 채팅 모델: {}", currentModel());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logOnStartup() {
        log.info("OpenRouter 오늘의 채팅 모델 (시작 시): {}", currentModel());
    }
}
