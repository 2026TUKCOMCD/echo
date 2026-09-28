package com.example.echo.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Schema(description = "TTS 재시도 응답")
@Getter
@Builder
public class TtsRetryResponse {

    /**
     * 다시 합성한 AI 응답 전체 텍스트. 스트리밍 응답이 중간에 끊기면 클라이언트는 받은 조각까지만 알고 있으므로,
     * 이 값으로 말풍선을 전체 문장으로 채운다.
     */
    @Schema(description = "다시 합성한 마지막 AI 응답 텍스트 (스트리밍이 끊겼을 때 말풍선을 채우는 용도)")
    private String aiResponse;

    @Schema(description = "TTS 음성 데이터 (Base64 인코딩 또는 바이너리)")
    private byte[] audioData;
}
