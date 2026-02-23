package com.example.echo.voice.service;

import org.springframework.web.multipart.MultipartFile;
// [2024-01 merge] voice.dto.VoiceSettings → user.dto.VoiceSettings로 통일
// 이유: user/dto에 더 완성도 높은 VoiceSettings가 있어 중복 제거
import com.example.echo.user.dto.VoiceSettings;

/**
 * 음성 처리 서비스 인터페이스
 */
public interface VoiceService {
    String speechToText(MultipartFile audioFile);
    byte[] textToSpeech(String text, VoiceSettings voiceSettings);
}
