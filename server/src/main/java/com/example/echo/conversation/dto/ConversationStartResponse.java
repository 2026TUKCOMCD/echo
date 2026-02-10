package com.example.echo.conversation.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class ConversationStartResponse {
    private String message;
    private byte[] audioData;
    private LocalDateTime timestamp;
}