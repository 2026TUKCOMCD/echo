package com.example.echo.conversation.dto;

/**
 * 스트리밍 응답의 META 프레임(JSON) 내용 - 스트림 맨 앞에 1번.
 * AI 응답 텍스트는 아직 생성 중이므로 여기 없고, 조각마다 TEXT 프레임({@link StreamText})으로 온다.
 */
public record StreamMeta(String userMessage) {
}
