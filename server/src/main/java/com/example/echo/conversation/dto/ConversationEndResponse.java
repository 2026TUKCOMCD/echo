package com.example.echo.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Schema(description = "대화 종료 응답")
@Getter
@Builder
public class ConversationEndResponse {

    @Schema(description = "대화 종료 시간", example = "2026-03-07T11:00:00")
    private LocalDateTime endedAt;
}
