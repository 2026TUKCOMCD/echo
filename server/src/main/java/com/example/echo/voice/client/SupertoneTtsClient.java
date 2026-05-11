package com.example.echo.voice.client;

import com.example.echo.voice.config.SupertoneFeignConfig;
import com.example.echo.voice.dto.SupertoneCreditBalance;
import com.example.echo.voice.dto.SupertoneTtsRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "supertone-tts-client",
    url = "${supertone.base-url}",
    configuration = SupertoneFeignConfig.class
)
public interface SupertoneTtsClient {

    @PostMapping(
        value = "/v1/text-to-speech/{voiceId}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = "audio/wav"
    )
    byte[] synthesize(
        @PathVariable("voiceId") String voiceId,
        @RequestBody SupertoneTtsRequest request
    );

    /**
     * 크레딧 잔액 조회 — 402 발생 시 로그 목적으로만 사용.
     * 공식 문서: https://docs.supertoneapi.com
     */
    @GetMapping(value = "/v1/credits", produces = MediaType.APPLICATION_JSON_VALUE)
    SupertoneCreditBalance getCreditBalance();
}
