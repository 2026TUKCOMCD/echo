package com.example.echo.voice.realtime;

/** 실시간 전사 실패 (연결 실패, OpenAI 오류 이벤트, 전사 실패/시간 초과, 연결 끊김) */
public class RealtimeTranscriptionException extends RuntimeException {

    public RealtimeTranscriptionException(String message) {
        super(message);
    }

    public RealtimeTranscriptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
