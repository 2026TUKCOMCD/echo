package com.example.echo.conversation.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class ConversationEndResponse {
    private LocalDateTime endedAt;
}