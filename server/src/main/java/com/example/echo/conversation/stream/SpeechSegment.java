package com.example.echo.conversation.stream;

import java.io.InputStream;

/**
 * 스트리밍 응답의 한 조각 - 말풍선에 이어 붙일 텍스트와, 첫 바이트가 도착한 TTS 오디오 스트림.
 *
 * @param audio 받은 쪽이 반드시 close() 해야 한다
 */
public record SpeechSegment(String text, InputStream audio) {
}
