package com.example.echo.voice.provider;

import com.example.echo.user.dto.VoiceSettings;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

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

    /**
     * 스트리밍 합성. 첫 오디오 바이트가 도착한 뒤에 반환하며, 호출자가 반드시 close() 해야 한다.
     * 기본 구현은 전체 합성 결과를 메모리 스트림으로 감싼다 - 스트리밍을 지원하지 않는 프로바이더도
     * 동작은 하지만 지연 단축 효과는 없다.
     */
    default InputStream synthesizeStream(String text, VoiceSettings voiceSettings) {
        return new ByteArrayInputStream(synthesize(text, voiceSettings));
    }
}
