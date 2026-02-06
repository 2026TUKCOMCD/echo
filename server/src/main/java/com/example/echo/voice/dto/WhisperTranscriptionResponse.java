/*
WhisperTranscriptionResponse: OpenAI 답장 받는 그릇
*/
package com.example.echo.voice.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class WhisperTranscriptionResponse {
    private String text;
}
