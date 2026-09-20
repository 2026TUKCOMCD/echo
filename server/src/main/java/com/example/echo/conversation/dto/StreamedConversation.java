package com.example.echo.conversation.dto;

import java.io.InputStream;

/**
 * 스트리밍 응답용 대화 결과. 텍스트는 확정되었고, 오디오는 첫 바이트가 도착한 스트림이다.
 *
 * @param audioStream       TTS 오디오 스트림 - 호출자가 반드시 close() 해야 한다
 * @param onStreamCompleted 스트림 전송을 끝까지 마쳤을 때 호출(지연 측정 기록용)
 */
public record StreamedConversation(
        String userMessage,
        String aiResponse,
        InputStream audioStream,
        Runnable onStreamCompleted
) {
}
