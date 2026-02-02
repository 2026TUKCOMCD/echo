package com.example.echo.conversation.service;

import com.example.echo.ai.service.AIService;
import com.example.echo.context.domain.UserContext;
import com.example.echo.context.service.ContextService;
import com.example.echo.conversation.dto.ConversationResponse;
import com.example.echo.conversation.dto.ConversationStartResponse;
import com.example.echo.diary.service.DiaryService;
import com.example.echo.prompt.service.PromptService;
import com.example.echo.voice.service.VoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final VoiceService voiceService;
    private final PromptService promptService;
    private final AIService aiService;
    private final ContextService contextService;
    private final DiaryService diaryService;

    @Transactional
    public ConversationStartResponse startConversation(Long userId) {
        // 1. 컨텍스트 초기화
        UserContext context = contextService.initializeContext(userId);

        // 2. 첫 인사 생성
        String systemPrompt = promptService.buildSystemPrompt(context);
        String firstMessage = aiService.generateGreeting(systemPrompt, context);

        // 3. TTS 변환
        byte[] audioData = voiceService.textToSpeech(firstMessage, context.getPreferences().getVoiceSettings());

        // 4. 히스토리 추가 (비동기)
        CompletableFuture.runAsync(() ->
                contextService.addConversationTurn(userId, null, firstMessage)
        );

        return ConversationStartResponse.builder()
                .message(firstMessage)
                .audioData(audioData)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Transactional
    public ConversationResponse processUserMessage(Long userId, MultipartFile audioFile) {
        // 1. 컨텍스트 조회
        UserContext context = contextService.getContext(userId);

        // 2. STT 변환
        String userMessage = voiceService.speechToText(audioFile);

        // 3. 프롬프트 생성
        String conversationPrompt = promptService.buildConversationPrompt(context, userMessage);

        // 4. AI 응답 생성
        String aiResponse = aiService.generateResponse(conversationPrompt);

        // 5. TTS 변환
        byte[] audioData = voiceService.textToSpeech(aiResponse, context.getPreferences().getVoiceSettings());

        // 6. 히스토리 업데이트 (비동기)
        CompletableFuture.runAsync(() ->
                contextService.addConversationTurn(userId, userMessage, aiResponse)
        );

        return ConversationResponse.builder()
                .userMessage(userMessage)
                .aiResponse(aiResponse)
                .audioData(audioData)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Transactional
    public void endConversation(Long userId) {
        log.info("대화 종료 시작 - userId: {}", userId);

        //1. 컨텍스트 조회
        UserContext context = contextService.getContext(userId);
        log.info("컨텍스트 조회 완료 - 대화 턴 수: {}", context.getConversationHistory().size());

        // 일기 생성 (비동기) -> MVP는 동기로 가도 됨. 비동기 트랜잭션 문제 발생 -> 해결 추진 예정
        CompletableFuture.runAsync(() ->{ //중괄호 표시 ( 여러 줄 )
                log.info("일기 생성 시작 (비동기) - userId: {}", userId);
                try {
                         diaryService.generateAndSaveDiary(context);
                         log.info("일기 생성 완료 (비동기) - userId: {}", userId);
                } catch (Exception e) {
                         log.error("일기 생성 실패 - userId: {}", userId, e);
                }
             });

        // 3. 컨텍스트 정리
        contextService.finalizeContext(userId);
        log.info("=== 대화 종료 완료 - userId: {} ===", userId);
    }
}
