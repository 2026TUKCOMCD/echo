package com.example.echo.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 비즈니스 예외 공통 추상 클래스.
 * 하위 클래스가 상태 코드와 메시지를 정의하면 GlobalExceptionHandler가 일관 매핑한다.
 */
@Getter
public abstract class BaseException extends RuntimeException {

    private final HttpStatus status;

    protected BaseException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
