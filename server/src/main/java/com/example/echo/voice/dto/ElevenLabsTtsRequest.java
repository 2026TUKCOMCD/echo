package com.example.echo.voice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ElevenLabsTtsRequest {
    private String text;

    @JsonProperty("model_id")
    private String modelId;

    @JsonProperty("language_code")
    private String languageCode;

    @JsonProperty("voice_settings")
    private VoiceParams voiceSettings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VoiceParams {
        private Double stability;

        @JsonProperty("similarity_boost")
        private Double similarityBoost;
    }
}
