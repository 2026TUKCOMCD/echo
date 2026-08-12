/*
 * OpenRouter 채팅 모델 세션별 로테이션
 *
 * 선택 방식: 대화 세션 시작 시 1회 무작위 선택
 * - ConversationService.startConversation()에서 pickModelForSession()을 호출해
 *   UserContext.sessionModel에 저장하고, 그 세션의 모든 AI 호출(인사·응답·일기·기억 추출)이
 *   같은 모델을 재사용한다 (systemPrompt·recallGuide와 동일한 "세션당 1회 확정" 패턴)
 * - 세션이 이미 UserContext로 상태를 들고 있으므로(ContextService), 이 서비스 자체는
 *   무상태로 유지한다 (호출마다 새로 무작위 선택만 하면 됨)
 */
package com.example.echo.ai.service;

import com.example.echo.ai.config.OpenRouterChatProperties;
import com.example.echo.ai.exception.AIException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

@Slf4j
@Service
public class ModelRotationService {

    private final OpenRouterChatProperties chatProperties;
    private final Random random;

    @Autowired
    public ModelRotationService(OpenRouterChatProperties chatProperties) {
        this(chatProperties, new SecureRandom());
    }

    /**
     * 테스트에서 선택 결과를 고정하기 위한 생성자 (패키지 전용)
     */
    ModelRotationService(OpenRouterChatProperties chatProperties, Random random) {
        this.chatProperties = chatProperties;
        this.random = random;
    }

    /**
     * 이번 대화 세션에서 사용할 모델을 무작위로 선택한다.
     * (예: "anthropic/claude-sonnet-5")
     */
    public String pickModelForSession() {
        List<String> models = chatProperties.getModels();
        if (models == null || models.isEmpty()) {
            throw new AIException("openrouter.chat.models 설정이 비어 있습니다.");
        }

        String picked = models.get(random.nextInt(models.size()));
        log.info("OpenRouter 이번 세션 모델: {}", picked);
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
