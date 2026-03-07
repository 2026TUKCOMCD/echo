package com.example.echo.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Schema(description = "대화 시작 응답")
@Getter
@Builder
public class ConversationStartResponse {

    @Schema(description = "AI 첫 인사 메시지", example = "안녕하세요, 홍길동님! 오늘 날씨가 맑네요.")
    private String message;

    @Schema(description = "TTS 음성 데이터 (Base64 인코딩 또는 바이너리)")
    private byte[] audioData;

    @Schema(description = "응답 시간", example = "2026-03-07T10:30:00")
    private LocalDateTime timestamp;
}
