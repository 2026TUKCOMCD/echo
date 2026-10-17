package com.example.echo.voice.exception;

/**
 * 클라이언트가 보낸 오디오 자체가 잘못된 경우 (비어 있음, 지원하지 않는 형식, 크기 초과) - 400으로 응답한다.
 *
 * 외부 API(Whisper·TTS) 실패와 구분하기 위한 예외. VoiceProcessingException을 상속하므로
 * 그 예외를 잡아 처리하던 기존 코드는 그대로 동작한다.
 */
public class InvalidAudioException extends VoiceProcessingException {
    public InvalidAudioException(String message) {
        super(message);
    }
}
