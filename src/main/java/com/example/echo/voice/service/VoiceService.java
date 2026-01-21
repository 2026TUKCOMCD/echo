package com.example.echo.voice.service;

import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.voice.client.STTClient;
import com.example.echo.voice.client.TTSClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class VoiceService {

    private final STTClient sttClient;
    private final TTSClient ttsClient;

    public String speechToText(MultipartFile audioFile) {
        // TODO: STTClient 호출 구현
        return null;
    }

    public byte[] textToSpeech(String text, VoiceSettings settings) {
        // TODO: TTSClient 호출 구현
        return null;
    }
}