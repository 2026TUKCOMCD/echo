package com.example.echo.voice.provider;

import com.example.echo.user.dto.VoiceSettings;

/**
 * TTS 프로바이더 공통 인터페이스 (전략 패턴).
 * 새 프로바이더 추가 시 이 인터페이스를 구현하는 @Component 하나만 등록하면
 * VoiceServiceImpl 수정 없이 자동으로 선택 가능해진다.
 */
public interface TtsProvider {

    /**
     * tts.provider 설정값과 매칭되는 프로바이더 이름 (예: "azure", "elevenlabs")
     */
    String getName();

    byte[] synthesize(String text, VoiceSettings voiceSettings);
}
