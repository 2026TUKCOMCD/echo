package com.example.echo.conversation.dto;

/**
 * /api/conversations/message-stream 응답의 META 프레임(JSON) 내용.
 * 오디오보다 먼저 도착하므로 클라이언트가 말풍선을 먼저 표시할 수 있다.
 */
public record StreamMeta(String userMessage, String aiResponse) {
}
