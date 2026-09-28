package com.example.echo.voice.realtime;

import java.util.concurrent.CompletableFuture;

/**
 * 한 발화(턴)짜리 실시간 전사 세션. 연결이 끝나기 전에 보낸 소리는 순서대로 쌓였다가 연결되면 전송된다.
 */
public interface TranscriptionUpstream extends AutoCloseable {

    /** 24kHz 16-bit 모노 PCM 조각을 보낸다. 실패는 여기서 던지지 않고 세션의 실패 콜백/commit 결과로 알린다 */
    void append(byte[] pcm24k);

    /** 말 끝 - 지금까지 보낸 소리의 최종 전사를 기다린다. 실패하면 {@link RealtimeTranscriptionException}으로 완료된다 */
    CompletableFuture<LiveTranscript> commit();

    /** 연결 정리. 여러 번 호출해도 되고 예외를 던지지 않는다 */
    @Override
    void close();
}
