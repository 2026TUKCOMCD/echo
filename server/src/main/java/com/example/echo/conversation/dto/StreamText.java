package com.example.echo.conversation.dto;

/**
 * 스트리밍 응답의 TEXT 프레임(JSON) 내용 - AI 응답 조각의 텍스트. 해당 조각의 AUDIO 프레임들 바로 앞에 온다.
 * 클라이언트는 받은 순서대로 말풍선에 이어 붙인다(조각 사이는 공백 하나).
 */
public record StreamText(String text) {
}
