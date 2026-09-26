package com.example.echo.conversation.exception;

/**
 * 스트리밍 응답이 이미 시작(커밋)된 뒤에 발생한 예상치 못한 실패.
 * GlobalExceptionHandler가 이 예외를 받으면 오류 본문을 쓰지 않고 응답을 종료한다
 * (오디오 스트림 뒤에 JSON을 덧붙이면 스트림이 오염되기 때문).
 */
public class StreamAbortedException extends RuntimeException {

    public StreamAbortedException(String message, Throwable cause) {
        super(message, cause);
    }
}
