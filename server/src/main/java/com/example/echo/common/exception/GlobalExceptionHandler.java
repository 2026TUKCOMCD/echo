package com.example.echo.common.exception;

import com.example.echo.conversation.exception.StreamAbortedException;
import com.example.echo.voice.exception.VoiceProcessingException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
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

    /**
     * 요청 형식 오류 - 본문 JSON을 읽을 수 없음, 파라미터 누락·타입 불일치, multipart 파트 누락·형식 오류.
     * 이 핸들러가 없으면 handleUnexpected(Exception)가 잡아 500이 되고(앱은 "서버 문제"로 안내) ERROR 로그가 쌓인다.
     * 예외 메시지에 요청 본문 일부(비밀번호 등)가 들어갈 수 있어 응답·로그에는 예외 종류와 경로만 남긴다.
     */
    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            ServletRequestBindingException.class,
            MissingServletRequestPartException.class,
            MultipartException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e, HttpServletRequest request) {
        log.warn("잘못된 요청 - {} {}: {}", request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST", "잘못된 요청입니다."));
    }

    /** 지원하지 않는 HTTP 메서드(405)·Content-Type(415) - 클라이언트 오류라 500으로 뭉개지 않는다 */
    @ExceptionHandler({HttpRequestMethodNotSupportedException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ErrorResponse> handleUnsupportedRequest(Exception e, HttpServletRequest request) {
        HttpStatus status = e instanceof HttpRequestMethodNotSupportedException
                ? HttpStatus.METHOD_NOT_ALLOWED
                : HttpStatus.UNSUPPORTED_MEDIA_TYPE;
        log.warn("지원하지 않는 요청 - {} {}: {}", request.getMethod(), request.getRequestURI(), e.getClass().getSimpleName());
        return ResponseEntity
                .status(status)
                .body(ErrorResponse.of(status.value(), status.name(), "지원하지 않는 요청입니다."));
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
