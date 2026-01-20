package com.example.echo.voice.controller;

import com.example.echo.voice.dto.SttResponse;
import com.example.echo.voice.service.VoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
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
}
