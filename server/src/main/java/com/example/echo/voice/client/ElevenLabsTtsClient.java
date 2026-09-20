package com.example.echo.voice.client;

import com.example.echo.voice.config.ElevenLabsFeignConfig;
import com.example.echo.voice.dto.ElevenLabsSubscriptionInfo;
import com.example.echo.voice.dto.ElevenLabsTtsRequest;
import feign.Response;
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
     * 스트리밍 합성 - 오디오가 생성되는 대로 청크로 내려온다.
     * 반환된 Response의 body는 버퍼링되지 않으므로 호출자가 반드시 close() 해야 한다.
     * 공식 문서: https://elevenlabs.io/docs/api-reference/text-to-speech/stream
     */
    @PostMapping(
        value = "/text-to-speech/{voiceId}/stream",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = "audio/mpeg"
    )
    Response synthesizeStream(
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
