package com.example.echo.diary.exception;

import com.example.echo.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class DiaryNotFoundException extends BaseException {

    public DiaryNotFoundException(Long diaryId) {
        super(HttpStatus.NOT_FOUND, "일기를 찾을 수 없습니다 - diaryId: " + diaryId);
    }
}
