package com.example.echo.voice.service;

import org.springframework.web.multipart.MultipartFile;
import com.example.echo.voice.dto.VoiceSettings;

/**
 * 음성 처리 서비스 인터페이스
 */
public interface VoiceService {
    String speechToText(MultipartFile audioFile);
    byte[] textToSpeech(String text, VoiceSettings voiceSettings);
}
