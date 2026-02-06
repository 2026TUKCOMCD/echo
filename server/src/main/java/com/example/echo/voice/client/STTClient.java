/*
 인터페이스 (주문서 양식 코드)
 실제 실행할 때, 즉 VoiceService에서는 값을 넣어준다.
*/

/*
 * OpenAI Whisper STT 클라이언트
 *
 * @FeignClient: 기본 URL 설정 (https://api.openai.com/v1)
 * @PostMapping:
 *   - value: API 경로 → 기본 URL + "/audio/transcriptions"
 *   - consumes: 요청 형식 (MULTIPART_FORM_DATA - 파일 업로드용)
 *
 * @RequestPart: 각 필드를 개별 파트로 분리 전송 (파일 첨부 필요)
 *   - file: 음성 파일
 *   - model: 사용할 모델명
 */
package com.example.echo.voice.client;

import com.example.echo.voice.config.OpenAIFeignConfig;
import com.example.echo.voice.dto.WhisperTranscriptionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(
        name = "stt-client",
        url = "${openai.api.url}",
        configuration = OpenAIFeignConfig.class
)
public interface STTClient {

    @PostMapping(value = "/audio/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    WhisperTranscriptionResponse transcribe(
            @RequestPart("file") MultipartFile file,
            @RequestPart("model") String model,
            @RequestPart("language") String language,
            @RequestPart(value = "response_format", required = false) String responseFormat
    );
}
