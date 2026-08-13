/*
 * AI 응답 생성 서비스
 *
 * 역할: OpenRouter API를 호출하여 AI 응답 생성
 * - generateGreeting(): 대화 시작 시 첫 인사 생성 (오늘의 로테이션 모델을 음성으로 안내하는 문장 포함)
 * - generateResponse(): 사용자 메시지에 대한 응답 생성
 *
 * 데이터 흐름:
 *   PromptService에서 조합된 프롬프트(String) 수신
 *   → OpenRouter Chat Completion API 호출 (모델은 대화 세션 시작 시 ModelRotationService가
 *     1회 순차 선택해 UserContext.sessionModel에 저장, 세션 내내 재사용)
 *   → 응답 텍스트 반환
 *
 * 설정값 (application.yaml):
 *   - openrouter.chat.models: 로테이션 후보 모델 목록
 *   - openrouter.chat.temperature: 창의성 (0.7)
 *   - openrouter.chat.max-tokens: 최대 토큰 (1024)
 */
package com.example.echo.ai.service;

import com.example.echo.ai.client.OpenRouterClient;
import com.example.echo.ai.config.OpenRouterChatProperties;
import com.example.echo.ai.dto.ChatCompletionRequest;
import com.example.echo.ai.dto.ChatCompletionResponse;
import com.example.echo.ai.exception.AIException;
import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private static final String MODEL_ANNOUNCEMENT_FORMAT = "이번에는 %s 모델과 함께 대화를 나눠요. ";

    private final OpenRouterClient openRouterClient;
    private final ModelRotationService modelRotationService;
    private final OpenRouterChatProperties chatProperties;

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

        String model = context.getSessionModel();

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
                .temperature(chatProperties.getTemperature())
                .maxTokens(chatProperties.getMaxTokens())
                .build();

        try {
            ChatCompletionResponse response = openRouterClient.createChatCompletion(request);
            String greeting = extractContent(response);
            String announcedGreeting = String.format(MODEL_ANNOUNCEMENT_FORMAT, modelRotationService.displayName(model)) + greeting;

            log.debug("Generated greeting - length: {}", announcedGreeting.length());
            return announcedGreeting;
        } catch (FeignException e) {
            log.error("OpenRouter API 호출 실패 - 상태코드: {}, 메시지: {}", e.status(), e.getMessage());
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
     * 대화 단계(1단계 안부 → 2단계 오늘 활동 → 3단계 장기기억 → 마무리)는 시스템 프롬프트에만
     * 정의되어 있고, 지금이 몇 단계인지는 모델이 여기 담긴 히스토리를 보고 스스로 판단한다.
     *
     * @param systemPrompt 시스템 프롬프트 (캐싱된 것 사용)
     * @param history 대화 히스토리 (ConversationTurn 리스트)
     * @param userMessage 현재 사용자 메시지
     * @param model 이번 세션에 확정된 모델 (UserContext.sessionModel)
     * @return AI가 생성한 응답 메시지
     * @throws AIException API 호출 실패 시
     */
    public String generateResponse(String systemPrompt, List<ConversationTurn> history, String userMessage, String model) {
        log.debug("Generating response - history size: {}, userMessage length: {}",
                history != null ? history.size() : 0, userMessage != null ? userMessage.length() : 0);

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
                .temperature(chatProperties.getTemperature())
                .maxTokens(chatProperties.getMaxTokens())
                .build();

        try {
            ChatCompletionResponse response = openRouterClient.createChatCompletion(request);
            String aiResponse = extractContent(response);

            log.debug("Generated response - length: {}", aiResponse.length());
            return aiResponse;
        } catch (FeignException e) {
            log.error("OpenRouter API 호출 실패 - 상태코드: {}, 메시지: {}", e.status(), e.getMessage());
            throw new AIException("AI 응답 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 일기 생성
     *
     * @param diaryPrompt PromptService.buildDiaryPrompt()로 조합된 일기 프롬프트
     * @param model 이번 세션에 확정된 모델 (UserContext.sessionModel)
     * @return AI가 생성한 일기 본문
     * @throws AIException API 호출 실패 또는 빈 응답 시
     */
    public String generateDiary(String diaryPrompt, String model) {
        log.debug("Generating diary - prompt length: {}", diaryPrompt != null ? diaryPrompt.length() : 0);

        List<ChatCompletionRequest.Message> messages = new ArrayList<>();

        messages.add(ChatCompletionRequest.Message.builder()
                .role("system")
                .content(diaryPrompt)
                .build());

        messages.add(ChatCompletionRequest.Message.builder()
                .role("user")
                .content("위 정보를 바탕으로 오늘의 일기를 작성해주세요.")
                .build());

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(chatProperties.getTemperature())
                .maxTokens(chatProperties.getMaxTokens())
                .build();

        try {
            ChatCompletionResponse response = openRouterClient.createChatCompletion(request);
            String diary = extractContent(response);

            // 빈 응답이 SUCCESS 일기로 저장되는 것 방지
            if (diary.isBlank()) {
                throw new AIException("일기 생성 결과가 비어있습니다");
            }

            log.debug("Generated diary - length: {}", diary.length());
            return diary.trim();
        } catch (FeignException e) {
            log.error("OpenRouter API 호출 실패 - 상태코드: {}, 메시지: {}", e.status(), e.getMessage());
            throw new AIException("AI 일기 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 장기기억 추출
     *
     * 대화 종료 시 [기존 기억 + 이번 대화]를 통합한 전체 기억 목록을 JSON 배열로 받는다.
     * 응답은 원문 그대로 반환하며, 파싱은 호출자(MemoryService)가 담당한다.
     *
     * @param memoryPrompt PromptService.buildMemoryPrompt()로 조합된 기억 추출 프롬프트
     * @param model 이번 세션에 확정된 모델 (UserContext.sessionModel)
     * @return AI가 생성한 JSON 배열 원문
     * @throws AIException API 호출 실패 또는 빈 응답 시
     */
    public String generateMemoryExtraction(String memoryPrompt, String model) {
        log.debug("Extracting memories - prompt length: {}", memoryPrompt != null ? memoryPrompt.length() : 0);

        List<ChatCompletionRequest.Message> messages = new ArrayList<>();

        messages.add(ChatCompletionRequest.Message.builder()
                .role("system")
                .content(memoryPrompt)
                .build());

        messages.add(ChatCompletionRequest.Message.builder()
                .role("user")
                .content("위 대화에서 어르신의 장기 기억을 JSON 배열로 추출해주세요.")
                .build());

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(model)
                .messages(messages)
                .temperature(chatProperties.getTemperature())
                .maxTokens(chatProperties.getMaxTokens())
                .build();

        try {
            ChatCompletionResponse response = openRouterClient.createChatCompletion(request);
            String extracted = extractContent(response);

            if (extracted.isBlank()) {
                throw new AIException("기억 추출 결과가 비어있습니다");
            }

            log.debug("Extracted memories - length: {}", extracted.length());
            return extracted.trim();
        } catch (FeignException e) {
            log.error("OpenRouter API 호출 실패 - 상태코드: {}, 메시지: {}", e.status(), e.getMessage());
            throw new AIException("AI 기억 추출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * API 응답에서 텍스트 추출
     * 응답 구조: response.choices[0].message.content
     */
    private String extractContent(ChatCompletionResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            log.warn("Empty response from OpenRouter API");
            return "";
        }

        ChatCompletionResponse.Choice choice = response.getChoices().get(0);
        if (choice.getMessage() == null || choice.getMessage().getContent() == null) {
            log.warn("Empty message content in OpenRouter response");
            return "";
        }

        return choice.getMessage().getContent();
    }
}
