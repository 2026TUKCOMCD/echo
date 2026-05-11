package com.example.echo.voice.exception;

public class SupertoneInsufficientCreditException extends RuntimeException {
    public SupertoneInsufficientCreditException(String message) {
        super(message);
    }

    public SupertoneInsufficientCreditException(String message, Throwable cause) {
        super(message, cause);
    }
}
