package com.example.echo.context.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationTurn {
    private String userMessage;
    private String aiResponse;
    private LocalDateTime timestamp;
}
