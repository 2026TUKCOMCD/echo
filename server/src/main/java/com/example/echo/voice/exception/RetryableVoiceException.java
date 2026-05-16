package com.example.echo.voice.exception;

public class RetryableVoiceException extends RuntimeException {
    public RetryableVoiceException(String message) {
        super(message);
    }

    public RetryableVoiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
