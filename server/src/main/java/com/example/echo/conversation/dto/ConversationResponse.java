package com.example.echo.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Schema(description = "대화 메시지 응답")
@Getter
@Builder
public class ConversationResponse {

    @Schema(description = "STT로 변환된 사용자 메시지", example = "오늘 날씨가 좋네요")
    private String userMessage;

    @Schema(description = "AI 응답 메시지", example = "네, 정말 좋은 날씨예요. 산책하기 딱 좋겠어요!")
    private String aiResponse;

    @Schema(description = "TTS 음성 데이터 (Base64 인코딩 또는 바이너리)")
    private byte[] audioData;

    @Schema(description = "응답 시간", example = "2026-03-07T10:31:00")
    private LocalDateTime timestamp;
}
