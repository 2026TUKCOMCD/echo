package com.example.echo.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceSettings {
    @Builder.Default
    private Double voiceSpeed = 1.0;

    @Builder.Default
    private String voiceTone = "warm";
}