package com.example.echo.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "TTS 재시도 응답")
@Getter
@Builder
public class TtsRetryResponse {

    @Schema(description = "TTS 음성 데이터 (Base64 인코딩 또는 바이너리)")
    private byte[] audioData;
}
