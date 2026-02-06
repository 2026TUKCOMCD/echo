package com.example.echo.conversation.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 대화 메시지 응답 DTO
 */
@Getter
@Builder
public class ConversationResponse {
    private String userMessage;
    private String aiResponse;
    private byte[] audioData;
    private LocalDateTime timestamp;
}
