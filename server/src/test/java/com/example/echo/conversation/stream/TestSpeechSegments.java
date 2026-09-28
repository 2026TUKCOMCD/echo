package com.example.echo.conversation.stream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** 테스트용 SpeechSegmentSource - 정해진 조각을 차례로 내주고, 선택적으로 마지막에 실패한다. */
public final class TestSpeechSegments implements SpeechSegmentSource {

    private final Deque<SpeechSegment> segments;
    private final IOException failAtEnd;
    public boolean closed;

    private TestSpeechSegments(List<SpeechSegment> segments, IOException failAtEnd) {
        this.segments = new ArrayDeque<>(segments);
        this.failAtEnd = failAtEnd;
    }

    public static TestSpeechSegments of(SpeechSegment... segments) {
        return new TestSpeechSegments(List.of(segments), null);
    }

    /** 조각을 다 내준 뒤 다음 조각 요청에서 IOException(다음 조각 준비 실패) */
    public static TestSpeechSegments failingAfter(SpeechSegment... segments) {
        return new TestSpeechSegments(List.of(segments), new IOException("next segment failed"));
    }

    public static SpeechSegment segment(String text, String audio) {
        return new SpeechSegment(text, new ByteArrayInputStream(audio.getBytes(StandardCharsets.UTF_8)));
    }

    public static SpeechSegment segment(String text, byte[] audio) {
        return new SpeechSegment(text, new ByteArrayInputStream(audio));
    }

    @Override
    public SpeechSegment next() throws IOException {
        if (!segments.isEmpty()) {
            return segments.poll();
        }
        if (failAtEnd != null) {
            throw failAtEnd;
        }
        return null;
    }

    @Override
    public void close() {
        closed = true;
    }

    /** 응답 본문 바이트를 프레임 목록으로 파싱 (클라이언트 파서와 같은 규칙) */
    public static List<Frame> parseFrames(byte[] body) {
        List<Frame> frames = new ArrayList<>();
        int pos = 0;
        while (pos < body.length) {
            int type = body[pos] & 0xFF;
            int length = ((body[pos + 1] & 0xFF) << 24) | ((body[pos + 2] & 0xFF) << 16)
                    | ((body[pos + 3] & 0xFF) << 8) | (body[pos + 4] & 0xFF);
            byte[] payload = new byte[length];
            System.arraycopy(body, pos + 5, payload, 0, length);
            frames.add(new Frame(type, payload));
            pos += 5 + length;
        }
        return frames;
    }

    public record Frame(int type, byte[] payload) {
        public String text() {
            return new String(payload, StandardCharsets.UTF_8);
        }
    }
}
