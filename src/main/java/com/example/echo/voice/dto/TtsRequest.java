package com.example.echo.voice.dto;

import com.example.echo.user.dto.VoiceSettings;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TtsRequest {
    private String text;
    private VoiceSettings voiceSettings;
}
