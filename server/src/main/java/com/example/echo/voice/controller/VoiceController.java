/*
 * "음성 파일 받는 접수창구"
 * 접수만 받고(controller), 실제 작업은 Service에게
 *
 * ========== DTO 사용 이유 ==========
 *
 * | 구분 | 요청                | 응답              |
 * |------|---------------------|-------------------|
 * | STT  | MultipartFile       | SttResponse (DTO) |
 * | TTS  | TtsRequest (DTO)    | byte[]            |
 *
 * - 필드가 여러 개면 → DTO로 묶음
 * - 단일 값이면 → 직접 사용
 *
 * STT 요청: 파일 1개만 받으면 됨 → DTO 불필요
 * STT 응답: JSON 형태로 감싸서 반환 → DTO 필요
 * TTS 요청: 여러 필드(text, voiceSettings)를 묶어야 함 → DTO 필요
 * TTS 응답: MP3 바이너리 그대로 반환 → DTO 불필요
 */
package com.example.echo.voice.controller;

import com.example.echo.voice.dto.SttResponse;
import com.example.echo.voice.dto.TtsRequest;
import com.example.echo.voice.service.VoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
public class VoiceController {

    private final VoiceService voiceService;

    @PostMapping("/stt")
    public ResponseEntity<SttResponse> speechToText(@RequestParam("file") MultipartFile audioFile) {
        String transcribedText = voiceService.speechToText(audioFile);
        return ResponseEntity.ok(new SttResponse(transcribedText));
    }

    /*
     * ========== TTS (텍스트 → 음성) ==========
     *
     * [요청]
     * POST /api/voice/tts
     * Content-Type: application/json
     * Body: { "text": "안녕하세요", "voiceSettings": { "voiceSpeed": 1.0, "voiceTone": "warm" } }
     *
     * [응답]
     * Content-Type: audio/mpeg
     * Body: MP3 바이너리 데이터
     */
    @PostMapping("/tts")
    public ResponseEntity<byte[]> textToSpeech(@RequestBody TtsRequest request) {
        byte[] audioData = voiceService.textToSpeech(request.getText(), request.getVoiceSettings());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"speech.mp3\"")
                .body(audioData);
    }
}
