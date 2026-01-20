/*
"커스텀 에러"
커스텀 에러가 필요한 이유
1. 에러 종류를 명확히 구분
2. 디버깅과 로깅이 쉬워짐
3. 일관된 에러 처리 기능 (일관된 형식의 에러가 나온다)
*/
package com.example.echo.voice.exception;

public class VoiceProcessingException extends RuntimeException {
    public VoiceProcessingException(String message) {
        super(message);
    }
    
    public VoiceProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
