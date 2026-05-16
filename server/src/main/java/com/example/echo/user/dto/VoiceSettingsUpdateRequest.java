package com.example.echo.user.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoiceSettingsUpdateRequest {
    private Double voiceSpeed;

    @Size(max = 50)
    private String voiceTone;
}
