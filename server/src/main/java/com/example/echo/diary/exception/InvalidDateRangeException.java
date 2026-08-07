package com.example.echo.diary.exception;

import com.example.echo.common.exception.BaseException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;

public class InvalidDateRangeException extends BaseException {

    public InvalidDateRangeException(LocalDate startDate, LocalDate endDate) {
        super(HttpStatus.BAD_REQUEST,
                "endDate는 startDate보다 이전일 수 없습니다 - startDate: " + startDate + ", endDate: " + endDate);
    }
}
