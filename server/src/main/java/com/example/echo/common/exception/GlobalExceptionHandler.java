package com.example.echo.common.exception;

import com.example.echo.voice.exception.SupertoneInsufficientCreditException;
import com.example.echo.voice.exception.VoiceProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SupertoneInsufficientCreditException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientCredit(SupertoneInsufficientCreditException e) {
        log.error("Supertone 크레딧 부족: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        "SERVICE_UNAVAILABLE",
                        "TTS 서비스를 현재 이용할 수 없습니다. 관리자에게 문의해주세요."
                ));
    }

    @ExceptionHandler(VoiceProcessingException.class)
    public ResponseEntity<ErrorResponse> handleVoiceProcessing(VoiceProcessingException e) {
        log.error("음성 처리 오류 발생: {}", e.getMessage(), e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "VOICE_PROCESSING_ERROR",
                        e.getMessage()
                ));
    }
}
