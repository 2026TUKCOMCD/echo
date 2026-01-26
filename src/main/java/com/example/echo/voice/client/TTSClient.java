/*
 * Clova Voice API TTS 클라이언트
 * - 텍스트를 음성(MP3)으로 변환
 */
package com.example.echo.voice.client;

import com.example.echo.voice.config.ClovaFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "tts-client",
    url = "${clova.api.url}",
    configuration = ClovaFeignConfig.class
)
public interface TTSClient {

    @PostMapping(
        value = "/tts-premium/v1/tts",
        consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
        produces = "audio/mpeg"
    )
    byte[] synthesize(@RequestBody String formData);
}
