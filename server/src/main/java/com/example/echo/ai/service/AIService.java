/*
 * AI 응답 생성 서비스
 *
 * 역할: OpenRouter API를 호출하여 AI 응답 생성
 * - generateGreeting(): 대화 시작 시 첫 인사 생성
 * - generateResponse(): 사용자 메시지에 대한 응답 생성
 * - openGreetingStream()/openResponseStream(): 위 둘의 스트리밍 버전 (SSE, 텍스트 조각 단위로 읽음)
 *
 * 데이터 흐름:
 *   PromptService에서 조합된 프롬프트(String) 수신
 *   → OpenRouter Chat Completion API 호출 (모델은 application.yaml에 고정된 단일 모델)
 *   → 응답 텍스트 반환
 *
 * 설정값 (application.yaml):
 *   - openrouter.chat.model: 사용할 모델 (단일 모델 고정)
 *   - openrouter.chat.temperature: 창의성 (0.7)
 *   - openrouter.chat.max-tokens: 최대 토큰 (1024)
 */
package com.example.echo.ai.service;

import com.example.echo.ai.client.OpenRouterClient;
import com.example.echo.ai.config.OpenRouterChatProperties;
import com.example.echo.ai.dto.ChatCompletionRequest;
import com.example.echo.ai.dto.ChatCompletionResponse;
import com.example.echo.ai.exception.AIException;
import com.example.echo.ai.stream.ChatCompletionStream;
import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIService {

    private final OpenRouterClient openRouterClient;
    private final OpenRouterChatProperties chatProperties;
    private final ObjectMapper objectMapper;

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

        ChatCompletionRequest request = buildRequest(greetingMessages(systemPrompt), null);

        try {
            ChatCompletionResponse response = openRouterClient.createChatCompletion(request);
            logCacheUsage(response);
            String greeting = extractContent(response);

            log.debug("Generated greeting - length: {}", greeting.length());
            return greeting;
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
     * @return AI가 생성한 응답 메시지
     * @throws AIException API 호출 실패 시
     */
    public String generateResponse(String systemPrompt, List<ConversationTurn> history, String userMessage) {
        log.debug("Generating response - history size: {}, userMessage length: {}",
                history != null ? history.size() : 0, userMessage != null ? userMessage.length() : 0);

        ChatCompletionRequest request = buildRequest(responseMessages(systemPrompt, history, userMessage), null);

        try {
            ChatCompletionResponse response = openRouterClient.createChatCompletion(request);
            logCacheUsage(response);
            String aiResponse = extractContent(response);

            log.debug("Generated response - length: {}", aiResponse.length());
            return aiResponse;
        } catch (FeignException e) {
            log.error("OpenRouter API 호출 실패 - 상태코드: {}, 메시지: {}", e.status(), e.getMessage());
            throw new AIException("AI 응답 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 대화 시작 인사 생성 - 스트리밍 버전.
     * 반환된 스트림에서 텍스트 조각을 읽고, 반드시 close() 해야 한다.
     *
     * @throws AIException 스트림을 열지 못한 경우(HTTP 오류, 네트워크 오류)
     */
    public ChatCompletionStream openGreetingStream(String systemPrompt, UserContext context) {
        log.debug("Opening greeting stream for user: {}", context.getUserId());
        return openStream(buildRequest(greetingMessages(systemPrompt), true), "AI 인사 생성 실패");
    }

    /**
     * 대화 응답 생성 - 스트리밍 버전. 메시지 구성은 {@link #generateResponse}와 같다.
     * 반환된 스트림에서 텍스트 조각을 읽고, 반드시 close() 해야 한다.
     *
     * @throws AIException 스트림을 열지 못한 경우(HTTP 오류, 네트워크 오류)
     */
    public ChatCompletionStream openResponseStream(String systemPrompt, List<ConversationTurn> history, String userMessage) {
        log.debug("Opening response stream - history size: {}, userMessage length: {}",
                history != null ? history.size() : 0, userMessage != null ? userMessage.length() : 0);
        return openStream(buildRequest(responseMessages(systemPrompt, history, userMessage), true), "AI 응답 생성 실패");
    }

    private ChatCompletionStream openStream(ChatCompletionRequest request, String failureMessage) {
        Response response;
        try {
            response = openRouterClient.createChatCompletionStream(request);
        } catch (FeignException e) {
            log.error("OpenRouter API 호출 실패 - 상태코드: {}, 메시지: {}", e.status(), e.getMessage());
            throw new AIException(failureMessage + ": " + e.getMessage(), e);
        }

        // 반환 타입이 Response라 HTTP 오류도 예외가 아닌 응답으로 돌아온다
        if (response.status() < 200 || response.status() >= 300 || response.body() == null) {
            log.error("OpenRouter API 스트리밍 호출 실패 - 상태코드: {}", response.status());
            response.close();
            throw new AIException(failureMessage + ": HTTP " + response.status());
        }

        try {
            InputStream body = response.body().asInputStream();
            return new ChatCompletionStream(body, response, objectMapper, this::logCacheUsage);
        } catch (IOException e) {
            response.close();
            throw new AIException(failureMessage + ": 응답 스트림을 열지 못했습니다", e);
        }
    }

    private ChatCompletionRequest buildRequest(List<ChatCompletionRequest.Message> messages, Boolean stream) {
        return ChatCompletionRequest.builder()
                .model(chatProperties.getModel())
                .messages(messages)
                .temperature(chatProperties.getTemperature())
                .maxTokens(chatProperties.getMaxTokens())
                .stream(stream)
                .build();
    }

    private List<ChatCompletionRequest.Message> greetingMessages(String systemPrompt) {
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
        return messages;
    }

    private List<ChatCompletionRequest.Message> responseMessages(String systemPrompt, List<ConversationTurn> history,
                                                                 String userMessage) {
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
        return messages;
    }

    /**
     * 일기 생성
     *
     * @param diaryPrompt PromptService.buildDiaryPrompt()로 조합된 일기 프롬프트
     * @return AI가 생성한 일기 본문
     * @throws AIException API 호출 실패 또는 빈 응답 시
     */
    public String generateDiary(String diaryPrompt) {
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
                .model(chatProperties.getModel())
                .messages(messages)
                .temperature(chatProperties.getTemperature())
                .maxTokens(chatProperties.getMaxTokens())
                .build();

        try {
            ChatCompletionResponse response = openRouterClient.createChatCompletion(request);
            logCacheUsage(response);
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
     * @return AI가 생성한 JSON 배열 원문
     * @throws AIException API 호출 실패 또는 빈 응답 시
     */
    public String generateMemoryExtraction(String memoryPrompt) {
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
                .model(chatProperties.getModel())
                .messages(messages)
                .temperature(chatProperties.getTemperature())
                .maxTokens(chatProperties.getMaxTokens())
                .build();

        try {
            ChatCompletionResponse response = openRouterClient.createChatCompletion(request);
            logCacheUsage(response);
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
     * 프롬프트 캐싱 적중 여부를 로그로 남긴다.
     *
     * OpenRouter를 통한 OpenAI 계열 모델 호출은 프롬프트가 1024토큰 이상이고 앞부분이
     * 이전 요청과 동일하면 별도 설정 없이 자동으로 캐싱된다. Echo는 매 턴마다
     * [고정 시스템 프롬프트 + 누적 대화 이력]을 그대로 앞에 두고 뒤에만 새 메시지를 추가하는
     * 구조라 이 조건을 충족하지만, 실제 적중 여부는 응답의 usage.cachedTokens로만 확인 가능하다.
     */
    private void logCacheUsage(ChatCompletionResponse response) {
        logCacheUsage(response != null ? response.getUsage() : null);
    }

    private void logCacheUsage(ChatCompletionResponse.Usage usage) {
        if (usage == null || usage.getPromptTokens() == null) {
            return;
        }

        Integer cached = usage.getCachedTokens();
        if (cached == null || cached == 0) {
            log.debug("프롬프트 캐싱 - 미적중 (prompt_tokens: {})", usage.getPromptTokens());
            return;
        }

        double hitRatio = usage.getPromptTokens() > 0
                ? (double) cached / usage.getPromptTokens() * 100
                : 0;
        log.info("프롬프트 캐싱 - 적중 (prompt_tokens: {}, cached_tokens: {}, 적중률: {}%)",
                usage.getPromptTokens(), cached, String.format("%.1f", hitRatio));
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

        return sanitizeGarbledText(choice.getMessage().getContent());
    }

    /**
     * 한글/영문/숫자/기본 문장부호가 아닌 문자가 섞인 "단어"(공백으로 구분된 토큰)를
     * 통째로 "거기"로 치환한다. 순수 영문 단어(예: Starbucks)는 그대로 두고, 그 안에
     * 정상 범위를 벗어난 문자(예: 아르메니아/IPA 확장 문자)가 하나라도 섞인 토큰만 치환 대상이다.
     *
     * AI가 장소명을 문장 안에서 두 번째로 다시 언급하려다 드물게 깨진 문자를 생성하는 케이스에 대한
     * 안전망(재호출 없이 즉시 처리 - 응답 지연 없음). PromptService의 프롬프트 지시(장소명은 한 번만
     * 말하고 재언급 시 "거기"를 쓰도록 강제)가 1차 방어이고, 이건 그래도 새어나온 경우의 2차 방어.
     */
    private static final Pattern WORD_PATTERN = Pattern.compile("\\S+");
    private static final Pattern SUSPICIOUS_CHAR_PATTERN = Pattern.compile(
            "[^\\uAC00-\\uD7A3\\u3131-\\u318E\\u0020-\\u007E\\u00B0\\u2010-\\u2015"
                    + "\\u2018\\u2019\\u201C\\u201D\\u2026\\u00B7\\n\\r\\t]");

    /**
     * 스트리밍 응답은 조각마다 이 메서드로 정제한다 - 조각은 공백/문장 끝에서 나뉘므로 단어 단위 치환 결과가
     * 전체를 한 번에 정제한 것과 같다.
     */
    public String sanitizeGarbledText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        Matcher matcher = WORD_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        boolean foundGarbled = false;
        int lastEnd = 0;
        while (matcher.find()) {
            result.append(text, lastEnd, matcher.start());
            String word = matcher.group();
            if (SUSPICIOUS_CHAR_PATTERN.matcher(word).find()) {
                result.append("거기");
                foundGarbled = true;
            } else {
                result.append(word);
            }
            lastEnd = matcher.end();
        }
        result.append(text, lastEnd, text.length());

        if (!foundGarbled) {
            return text;
        }
        String sanitized = result.toString();
        log.warn("AI 응답에서 비정상 유니코드 시퀀스를 감지해 치환했습니다 (원본 길이: {}, 치환 후 길이: {})",
                text.length(), sanitized.length());
        return sanitized;
    }
}
