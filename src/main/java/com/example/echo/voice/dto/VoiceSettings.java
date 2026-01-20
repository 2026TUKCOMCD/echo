package com.example.echo.voice.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VoiceSettings {
    private Double speed;
    private String voiceName;
}
