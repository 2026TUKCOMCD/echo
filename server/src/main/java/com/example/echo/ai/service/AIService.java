/*
 * AI 응답 생성 서비스
 *
 * 역할: OpenAI API를 호출하여 AI 응답 생성
 * - generateGreeting(): 대화 시작 시 첫 인사 생성
 * - generateResponse(): 사용자 메시지에 대한 응답 생성
 *
 * 데이터 흐름:
 *   PromptService에서 조합된 프롬프트(String) 수신
 *   → OpenAI Chat Completion API 호출
 *   → 응답 텍스트 반환
 *
 * 설정값 (application.yaml):
 *   - openai.chat.model: 사용 모델 (gpt-4o-mini)
 *   - openai.chat.temperature: 창의성 (0.7)
 *   - openai.chat.max-tokens: 최대 토큰 (1024)
 */
package com.example.echo.ai.service;

import com.example.echo.ai.client.OpenAIClient;
import com.example.echo.ai.dto.ChatCompletionRequest;
import com.example.echo.ai.dto.ChatCompletionResponse;
import com.example.echo.ai.exception.AIException;
import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private final OpenAIClient openAIClient;

    @Value("${openai.chat.model}")
    private String model;

    @Value("${openai.chat.temperature}")
    private Double temperature;

    @Value("${openai.chat.max-tokens}")
    private Integer maxTokens;

    /**
     * 대화 시작 인사 생성
     *
     * @param systemPrompt PromptService에서 생성한 시스템 프롬프트
     * @param context 사용자 컨텍스트 (로깅용)
     * @return AI가 생성한 첫 인사 메시지
     * @throws AIException API 호출 실패 시
     */
    public String generateGreeting(String systemPrompt, UserContext context) {
        log.debug("Generating greeting for user: {}", context.getUserId());

        List<ChatCompletionRequest.Message> messages = new ArrayList<>();

        // 시스템 프롬프트 추가
        messages.add(ChatCompletionRequest.Message.builder()
                .role("system")
                .content(systemPrompt)
                .build());

        // 인사 생성을 위한 사용자 메시지 추가 -> 날씨 정보로 바꿔야 하는 부분
        messages.add(ChatCompletionRequest.Message.builder()
                .role("user")
                .content("대화를 시작해주세요.")
                .build());

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

        try {
            ChatCompletionResponse response = openAIClient.createChatCompletion(request);
            String greeting = extractContent(response);

            log.debug("Generated greeting: {}", greeting);
            return greeting;
        } catch (FeignException e) {
            log.error("OpenAI API 호출 실패 - 상태코드: {}, 메시지: {}", e.status(), e.getMessage());
            throw new AIException("AI 인사 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 대화 응답 생성
     *
     * OpenAI 권장 방식: messages 배열에 role별로 분리하여 전송
     * - system: 시스템 프롬프트 (AI 페르소나, 규칙)
     * - user/assistant: 대화 히스토리
     * - user: 현재 사용자 메시지
     *
     * @param systemPrompt 시스템 프롬프트 (캐싱된 것 사용)
     * @param history 대화 히스토리 (ConversationTurn 리스트)
     * @param userMessage 현재 사용자 메시지
     * @return AI가 생성한 응답 메시지
     * @throws AIException API 호출 실패 시
     */
    public String generateResponse(String systemPrompt, List<ConversationTurn> history, String userMessage) {
        log.debug("Generating response - history size: {}, userMessage: {}",
                history != null ? history.size() : 0, userMessage);

        List<ChatCompletionRequest.Message> messages = new ArrayList<>();

        // 1. 시스템 프롬프트
        messages.add(ChatCompletionRequest.Message.builder()
                .role("system")
                .content(systemPrompt)
                .build());

        // 2. 대화 히스토리 (user/assistant role로 분리)
        if (history != null) {
            for (ConversationTurn turn : history) {
                // 사용자 메시지가 있으면 추가 (첫 인사는 userMessage가 null일 수 있음)
                if (turn.getUserMessage() != null) {
                    messages.add(ChatCompletionRequest.Message.builder()
                            .role("user")
                            .content(turn.getUserMessage())
                            .build());
                }
                // AI 응답 추가
                messages.add(ChatCompletionRequest.Message.builder()
                        .role("assistant")
                        .content(turn.getAiResponse())
                        .build());
            }
        }

        // 3. 현재 사용자 메시지
        messages.add(ChatCompletionRequest.Message.builder()
                .role("user")
                .content(userMessage)
                .build());

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

        try {
            ChatCompletionResponse response = openAIClient.createChatCompletion(request);
            String aiResponse = extractContent(response);

            log.debug("Generated response: {}", aiResponse);
            return aiResponse;
        } catch (FeignException e) {
            log.error("OpenAI API 호출 실패 - 상태코드: {}, 메시지: {}", e.status(), e.getMessage());
            throw new AIException("AI 응답 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * API 응답에서 텍스트 추출
     * 응답 구조: response.choices[0].message.content
     */
    private String extractContent(ChatCompletionResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            log.warn("Empty response from OpenAI API");
            return "";
        }

        ChatCompletionResponse.Choice choice = response.getChoices().get(0);
        if (choice.getMessage() == null || choice.getMessage().getContent() == null) {
            log.warn("Empty message content in OpenAI response");
            return "";
        }

        return choice.getMessage().getContent();
    }
}
