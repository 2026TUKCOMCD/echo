package com.example.echo.common.exception;

import com.example.echo.conversation.exception.StreamAbortedException;
import com.example.echo.voice.exception.VoiceProcessingException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.example.echo.common.exception.BaseException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException(BaseException e) {
        log.warn("Business exception: {} {}", e.getStatus(), e.getMessage());
        return ResponseEntity
                .status(e.getStatus())
                .body(ErrorResponse.of(e.getStatus().value(), e.getStatus().name(), e.getMessage()));
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        FieldError firstError = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .orElse(null);
        String message = firstError != null
                ? firstError.getField() + ": " + firstError.getDefaultMessage()
                : "잘못된 요청입니다.";
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST", message));
    }

    /**
     * 스트리밍 응답이 이미 시작된 뒤의 실패. 오디오 스트림 뒤에 JSON 오류 본문을 덧붙이면 스트림이 오염되므로
     * 아무것도 쓰지 않고 종료한다(END 프레임이 없어 클라이언트가 잘린 스트림으로 판단해 폴백한다).
     * HttpServletResponse 인자는 사용하지 않지만, 이 인자가 있어야 Spring이 응답을 처리 완료로 보고
     * 뷰 렌더링을 시도하지 않는다.
     */
    @ExceptionHandler(StreamAbortedException.class)
    public void handleStreamAborted(StreamAbortedException e, HttpServletResponse response) {
        log.error("스트리밍 응답 도중 실패 - 오류 본문 없이 종료: {}", e.getMessage(), e);
    }

    /**
     * 존재하지 않는 경로. Spring Boot 3.2+는 매핑되지 않은 요청에 NoResourceFoundException을 던지는데,
     * 이 핸들러가 없으면 아래 handleUnexpected(Exception)가 잡아 500으로 바뀐다.
     * 클라이언트가 "엔드포인트 없음(404)"과 "서버 오류(500)"를 구분할 수 있어야 하므로 404로 응답한다.
     * (예: 앱이 신규 엔드포인트가 없는 서버를 만나면 구 엔드포인트로 폴백)
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
        log.warn("존재하지 않는 경로 요청: {} /{}", e.getHttpMethod(), e.getResourcePath());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(
                        HttpStatus.NOT_FOUND.value(),
                        "NOT_FOUND",
                        "요청한 경로를 찾을 수 없습니다."
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "INTERNAL_SERVER_ERROR",
                        "서버 오류가 발생했습니다."
                ));
    }
}
