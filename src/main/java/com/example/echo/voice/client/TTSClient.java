/*
 * Clova Voice API TTS 클라이언트
 * - 텍스트를 음성(MP3)으로 변환
 */

/*
 * Clova Voice API TTS 클라이언트
 *
 * @FeignClient: 기본 URL 설정 (https://naveropenapi.apigw.ntruss.com)
 * @PostMapping:
 *   - value: API 경로 → 기본 URL + "/tts-premium/v1/tts"
 *   - consumes: 요청 형식 (APPLICATION_FORM_URLENCODED - 텍스트 폼 데이터)
 *   - produces: 응답 형식 (audio/mpeg - MP3 바이너리)
 *
 * @RequestBody: 폼 데이터를 하나의 문자열로 전송
 *   예: "speaker=nara&speed=0&text=안녕하세요"
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
