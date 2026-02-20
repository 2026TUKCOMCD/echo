package com.example.echo.conversation.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TtsRetryResponse {
    private byte[] audioData;
}
