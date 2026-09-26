package com.example.echo.conversation.dto;

import com.example.echo.conversation.stream.SpeechSegmentSource;

/**
 * 스트리밍 응답용 대화 결과. 사용자 발화는 확정되었고, AI 응답은 조각(텍스트 + TTS 오디오) 단위로 준비되는 대로 나온다.
 *
 * @param userMessage       STT 결과 (첫 인사는 null)
 * @param segments          AI 응답 조각 원천 - 첫 조각은 TTS 첫 바이트가 도착한 상태. 호출자가 반드시 close() 해야 한다
 * @param onStreamCompleted 스트림 전송을 끝까지 마쳤을 때 호출(지연 측정 기록용)
 */
public record StreamedConversation(
        String userMessage,
        SpeechSegmentSource segments,
        Runnable onStreamCompleted
) {
}
