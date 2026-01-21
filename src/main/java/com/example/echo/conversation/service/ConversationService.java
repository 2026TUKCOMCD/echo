package com.example.echo.conversation.service;

import com.example.echo.conversation.dto.ConversationStartResponse;
import com.example.echo.conversation.dto.ConversationResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

@Service
public class ConversationService {

    public ConversationStartResponse startConversation(Long userId) {
        // TODO: 실제 구현
        return ConversationStartResponse.builder()
                .message("Hello! Let's practice English.")
                .timestamp(LocalDateTime.now())
                .build();
    }

    public ConversationResponse processUserMessage(Long userId, MultipartFile audioFile) {
        // TODO: 실제 구현
        return ConversationResponse.builder()
                .userMessage("(음성 변환 예정)")
                .aiResponse("(AI 응답 예정)")
                .timestamp(LocalDateTime.now())
                .build();
    }

    public void endConversation(Long userId) {
        // TODO: 세션 정리 등
    }
}