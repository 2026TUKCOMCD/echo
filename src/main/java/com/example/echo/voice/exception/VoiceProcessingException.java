package com.example.echo.voice.exception;

public class VoiceProcessingException extends RuntimeException {
    public VoiceProcessingException(String message) {
        super(message);
    }

    public VoiceProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
