/*
 * OpenRouter 채팅 모델 세션별 순차 로테이션
 *
 * 선택 방식: 대화 세션 시작 시 1회 순차 선택 (카운터 % 후보 개수)
 * - ConversationService.startConversation()에서 pickModelForSession()을 호출해
 *   UserContext.sessionModel에 저장하고, 그 세션의 모든 AI 호출(인사·응답·일기·기억 추출)이
 *   같은 모델을 재사용한다 (systemPrompt·recallGuide와 동일한 "세션당 1회 확정" 패턴)
 * - 세션마다 다음 후보로 순서대로 넘어가며, 마지막 후보 다음엔 다시 처음으로 돌아온다
 * - 카운터는 서버 프로세스 생존 기간에 한정된 인메모리 상태다(재시작 시 처음부터 다시 순환).
 *   세션 자체도 인메모리(ContextService)로 관리되므로 서버 재시작에 특별한 내구성이
 *   필요하지 않다 - 여러 워커/인스턴스로 수평 확장하면 인스턴스별로 별도 순환됨에 유의
 */
package com.example.echo.ai.service;

import com.example.echo.ai.config.OpenRouterChatProperties;
import com.example.echo.ai.exception.AIException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRotationService {

    private final OpenRouterChatProperties chatProperties;
    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * 이번 대화 세션에서 사용할 모델을 순차적으로 선택한다.
     * (예: "anthropic/claude-sonnet-5")
     */
    public String pickModelForSession() {
        List<String> models = chatProperties.getModels();
        if (models == null || models.isEmpty()) {
            throw new AIException("openrouter.chat.models 설정이 비어 있습니다.");
        }

        int index = Math.floorMod(counter.getAndIncrement(), models.size());
        String picked = models.get(index);
        log.info("OpenRouter 이번 세션 모델(순차 로테이션): {}", picked);
        return picked;
    }

    /**
     * 모델 ID를 사람이 읽기 좋은(음성으로 발화 가능한) 이름으로 반환한다.
     * 예: "anthropic/claude-sonnet-5" -> "Claude Sonnet 5", "openai/gpt-5.5" -> "GPT 5.5"
     */
    public String displayName(String modelId) {
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
}
