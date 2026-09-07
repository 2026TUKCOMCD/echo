package com.example.echo.voice.client;

import com.example.echo.voice.config.ElevenLabsFeignConfig;
import com.example.echo.voice.dto.ElevenLabsSubscriptionInfo;
import com.example.echo.voice.dto.ElevenLabsTtsRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "elevenlabs-tts-client",
    url = "${elevenlabs.base-url}",
    configuration = ElevenLabsFeignConfig.class
)
public interface ElevenLabsTtsClient {

    @PostMapping(
        value = "/text-to-speech/{voiceId}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = "audio/mpeg"
    )
    byte[] synthesize(
        @PathVariable("voiceId") String voiceId,
        @RequestBody ElevenLabsTtsRequest request
    );

    /**
     * 잔여 쿼터(문자 수) 조회 — 오류 발생 시 로그 목적으로만 사용.
     * 공식 문서: https://elevenlabs.io/docs/api-reference/user/subscription
     */
    @GetMapping(value = "/user/subscription", produces = MediaType.APPLICATION_JSON_VALUE)
    ElevenLabsSubscriptionInfo getSubscriptionInfo();
}
