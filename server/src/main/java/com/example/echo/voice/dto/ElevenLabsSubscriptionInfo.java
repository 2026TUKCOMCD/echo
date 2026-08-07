package com.example.echo.voice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ElevenLabs 구독/쿼터 조회 API 응답 DTO.
 * GET /v1/user/subscription — 로그 출력 전용, 앱에 노출되지 않음.
 * 공식 문서: https://elevenlabs.io/docs/api-reference/user/subscription
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ElevenLabsSubscriptionInfo {

    @JsonProperty("character_count")
    private Long characterCount;

    @JsonProperty("character_limit")
    private Long characterLimit;

    @Override
    public String toString() {
        return "ElevenLabsSubscriptionInfo{characterCount=" + characterCount
                + ", characterLimit=" + characterLimit + '}';
    }
}
