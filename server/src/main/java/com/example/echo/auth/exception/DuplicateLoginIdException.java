package com.example.echo.auth.exception;

import com.example.echo.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class DuplicateLoginIdException extends BaseException {
    public DuplicateLoginIdException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
