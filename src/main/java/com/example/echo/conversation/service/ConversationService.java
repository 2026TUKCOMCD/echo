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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;

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
                .build();
    }

    @Transactional
    public void endConversation(Long userId) {
        UserContext context = contextService.getContext(userId);

        // 일기 생성 (비동기)
        CompletableFuture.runAsync(() ->
                diaryService.generateAndSaveDiary(context)
        );

        // 컨텍스트 정리
        contextService.finalizeContext(userId);
    }
}
