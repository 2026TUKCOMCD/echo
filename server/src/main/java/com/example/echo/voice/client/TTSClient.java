/*
 * Azure Cognitive Services TTS 클라이언트
 * - SSML(XML) 형식의 텍스트를 음성(MP3)으로 변환
 *
 * @FeignClient: 기본 URL 설정 (https://koreacentral.tts.speech.microsoft.com)
 * @PostMapping:
 *   - value: API 경로 → /cognitiveservices/v1
 *   - consumes: 요청 형식 (application/ssml+xml)
 *   - produces: 응답 형식 (audio/mpeg - MP3 바이너리)
 *
 * @RequestBody: SSML XML 문자열로 전송
 *   예: <speak version='1.0' xml:lang='ko-KR'><voice name='ko-KR-SunHiNeural'>안녕하세요</voice></speak>
 */
package com.example.echo.voice.client;

import com.example.echo.voice.config.AzureTtsFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "tts-client",
    url = "${azure.tts.endpoint}",
    configuration = AzureTtsFeignConfig.class
)
public interface TTSClient {

    @PostMapping(
        value = "/cognitiveservices/v1",
        consumes = "application/ssml+xml",
        produces = "audio/mpeg"
    )
    byte[] synthesize(@RequestBody String ssmlBody);
}
