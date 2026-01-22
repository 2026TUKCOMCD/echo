package com.example.echo.conversation.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class ConversationResponse {
    private String userMessage;
    private String aiResponse;
    private byte[] audioData;
    private LocalDateTime timestamp;
}