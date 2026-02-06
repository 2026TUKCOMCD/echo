/*
 * AI 서비스 예외
 *
 * OpenAI API 호출 실패 시 발생
 * - 네트워크 오류
 * - API 키 인증 실패 (401)
 * - Rate Limit 초과 (429)
 * - 서버 오류 (500)
 */
package com.example.echo.ai.exception;

public class AIException extends RuntimeException {

    public AIException(String message) {
        super(message);
    }

    public AIException(String message, Throwable cause) {
        super(message, cause);
    }
}
