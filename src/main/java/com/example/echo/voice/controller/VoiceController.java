/*
"음성 파일 받는 접수창구"
접수만 받고(controller), 실제 작업은 Service에게
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
