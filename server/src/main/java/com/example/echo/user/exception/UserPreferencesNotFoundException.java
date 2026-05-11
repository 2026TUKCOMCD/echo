package com.example.echo.user.exception;

import com.example.echo.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class UserPreferencesNotFoundException extends BaseException {

    public UserPreferencesNotFoundException(Long userId) {
        super(HttpStatus.NOT_FOUND, "사용자 설정을 찾을 수 없습니다. userId: " + userId);
    }
}
