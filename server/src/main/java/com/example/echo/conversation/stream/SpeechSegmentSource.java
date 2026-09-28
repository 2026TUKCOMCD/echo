package com.example.echo.conversation.stream;

import java.io.Closeable;
import java.io.IOException;

/**
 * 스트리밍 응답 조각을 순서대로 내주는 원천. 다음 조각이 준비될 때까지 {@link #next()}가 기다린다.
 *
 * {@link #next()}로 받은 조각의 오디오는 받은 쪽이 닫고, 아직 내주지 않은(또는 나중에 준비되는) 조각은
 * {@link #close()}가 닫는다.
 */
public interface SpeechSegmentSource extends Closeable {

    /**
     * @return 다음 조각. 더 없으면(정상 종료) null.
     * @throws IOException 다음 조각을 준비하지 못함 - 응답을 END 없이 끝내 클라이언트가 잘림으로 처리하게 한다
     */
    SpeechSegment next() throws IOException;

    /** 남은 자원 정리. 여러 번 호출해도 된다. 예외를 던지지 않는다. */
    @Override
    void close();

    /** 조각이 하나뿐인 원천 (LLM을 거치지 않는 고정 문구 등) */
    static SpeechSegmentSource single(SpeechSegment segment) {
        return new SpeechSegmentSource() {
            private SpeechSegment pending = segment;

            @Override
            public synchronized SpeechSegment next() {
                SpeechSegment result = pending;
                pending = null;
                return result;
            }

            @Override
            public synchronized void close() {
                if (pending != null) {
                    closeQuietly(pending);
                    pending = null;
                }
            }
        };
    }

    static void closeQuietly(SpeechSegment segment) {
        try {
            segment.audio().close();
        } catch (IOException ignored) {
            // 정리 중이므로 무시
        }
    }
}
