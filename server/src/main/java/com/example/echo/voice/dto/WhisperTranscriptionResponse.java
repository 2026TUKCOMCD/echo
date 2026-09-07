/*
WhisperTranscriptionResponse: OpenAI 답장 받는 그릇
*/
package com.example.echo.voice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class WhisperTranscriptionResponse {
    private String text;

    // response_format=verbose_json일 때만 채워짐 (신뢰도 기반 환각 필터링에 사용)
    private List<Segment> segments;

    @Getter
    @NoArgsConstructor
    public static class Segment {
        @JsonProperty("no_speech_prob")
        private double noSpeechProb;

        @JsonProperty("avg_logprob")
        private double avgLogprob;

        @JsonProperty("compression_ratio")
        private double compressionRatio;
    }
}
