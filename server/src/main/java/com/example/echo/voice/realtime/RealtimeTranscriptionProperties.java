/*
 * OpenAI Realtime 전사 설정값 (application.yaml: openai.realtime.*)
 *
 * url:                        전사 전용 세션 주소 (?intent=transcription)
 * model / language:           전사 모델과 언어 (한국어 품질 비교는 gpt-live-transcribe + ko로 검증됨)
 * connect-timeout-seconds:    OpenAI WebSocket 연결 최대 대기
 * transcript-timeout-seconds: 말 끝(commit) → 최종 전사 최대 대기
 * include-logprobs:           전사 logprobs 요청 여부 - 신뢰도 기록용(필터에는 쓰지 않음)
 * noise-reduction:            빈 값이면 끔, near_field / far_field
 * keywords:                   인식 힌트 단어 - 비어 있으면 보내지 않음
 */
package com.example.echo.voice.realtime;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "openai.realtime")
public class RealtimeTranscriptionProperties {

    private String url = "wss://api.openai.com/v1/realtime?intent=transcription";
    private String model = "gpt-live-transcribe";
    private String language = "ko";
    private long connectTimeoutSeconds = 10;
    private long transcriptTimeoutSeconds = 15;
    private boolean includeLogprobs = true;
    private String noiseReduction = "";
    private List<String> keywords = new ArrayList<>();
}
