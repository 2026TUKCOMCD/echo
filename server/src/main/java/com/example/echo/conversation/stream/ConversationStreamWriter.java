package com.example.echo.conversation.stream;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Function;

/**
 * /api/conversations/message-stream, /start-stream 응답 본문(프레임 스트림) 작성기.
 *
 * <pre>
 * 프레임 = [1바이트 type][4바이트 big-endian payload 길이][payload]
 *   META(0x01):  UTF-8 JSON {"userMessage": "..."}  - 스트림 맨 앞에 정확히 1개 (첫 인사는 userMessage=null)
 *   TEXT(0x03):  UTF-8 JSON {"text": "..."}         - AI 응답 조각의 텍스트. 그 조각의 AUDIO들 바로 앞에 온다
 *   AUDIO(0x02): mp3 바이트 조각                     - 0개 이상
 *   END(0x00):   길이 0                              - 정상 종료 표시(마지막에 1개)
 *
 * 예) META → TEXT(첫 조각) → AUDIO... → TEXT(나머지) → AUDIO... → END
 * </pre>
 *
 * AI 응답은 LLM이 생성하는 대로 조각 단위로 TTS를 시작하므로, 첫 소리가 나갈 때는 전체 텍스트를 모른다.
 * 그래서 텍스트도 조각마다 TEXT 프레임으로 보낸다.
 *
 * END 프레임을 두는 이유: nginx가 upstream과 HTTP/1.0으로 통신하면 "정상 종료"와 "TTS 중간 끊김"이
 * 클라이언트에서 구분되지 않는다. END 프레임이 없이 스트림이 끝나면 클라이언트는 잘린 것으로 판단해
 * tts-retry로 폴백한다. 대화 내용을 HTTP 헤더에 싣지 않기 위해 텍스트도 본문으로 전달한다.
 */
@Slf4j
public final class ConversationStreamWriter {

    public static final String CONTENT_TYPE = "application/x-echo-stream";

    public static final int TYPE_END = 0x00;
    public static final int TYPE_META = 0x01;
    public static final int TYPE_AUDIO = 0x02;
    public static final int TYPE_TEXT = 0x03;

    private static final int CHUNK_BYTES = 8 * 1024;
    private static final byte[] EMPTY = new byte[0];

    public enum Result {
        /** END 프레임까지 모두 전송함 */
        COMPLETED,
        /** 다음 조각 준비 또는 TTS 업스트림 읽기 실패 - END 없이 종료(클라이언트가 잘림으로 판단) */
        UPSTREAM_FAILED,
        /** 클라이언트가 연결을 끊음 */
        CLIENT_DISCONNECTED
    }

    private ConversationStreamWriter() {
    }

    /**
     * META → (TEXT → AUDIO...)* → END 순으로 프레임을 쓰고, 프레임마다 flush 한다.
     * 받은 조각의 오디오는 여기서 닫는다(원천 자체는 호출자가 닫는다).
     * 어떤 실패도 예외로 던지지 않고 결과로 알린다(응답이 이미 시작된 뒤라 오류 본문을 쓸 수 없으므로).
     *
     * @param textEncoder 조각 텍스트 → TEXT 프레임 payload(JSON)
     */
    public static Result write(OutputStream out, byte[] metaJson, SpeechSegmentSource segments,
                               Function<String, byte[]> textEncoder) {
        try {
            writeFrame(out, TYPE_META, metaJson, 0, metaJson.length);
        } catch (IOException e) {
            return Result.CLIENT_DISCONNECTED;
        }

        byte[] buffer = new byte[CHUNK_BYTES];
        while (true) {
            SpeechSegment segment;
            try {
                segment = segments.next();
            } catch (IOException e) {
                log.error("다음 응답 조각 준비 실패", e);
                return Result.UPSTREAM_FAILED;
            }
            if (segment == null) {
                break;
            }
            try (InputStream audio = segment.audio()) {
                Result result = writeSegment(out, segment.text(), audio, buffer, textEncoder);
                if (result != null) {
                    return result;
                }
            } catch (IOException ignored) {
                // 오디오 스트림 close 실패 - 이미 끝까지 읽었거나 실패 경로라 무시
            }
        }

        try {
            writeFrame(out, TYPE_END, EMPTY, 0, 0);
        } catch (IOException e) {
            return Result.CLIENT_DISCONNECTED;
        }
        return Result.COMPLETED;
    }

    /** @return 실패하면 그 결과, 조각을 끝까지 보냈으면 null */
    private static Result writeSegment(OutputStream out, String text, InputStream audio, byte[] buffer,
                                       Function<String, byte[]> textEncoder) {
        byte[] textJson = textEncoder.apply(text);
        try {
            writeFrame(out, TYPE_TEXT, textJson, 0, textJson.length);
        } catch (IOException e) {
            return Result.CLIENT_DISCONNECTED;
        }

        while (true) {
            int read;
            try {
                read = audio.read(buffer);
            } catch (IOException e) {
                log.error("TTS 스트림 읽기 실패", e);
                return Result.UPSTREAM_FAILED;
            }
            if (read == -1) {
                return null;
            }
            try {
                writeFrame(out, TYPE_AUDIO, buffer, 0, read);
            } catch (IOException e) {
                return Result.CLIENT_DISCONNECTED;
            }
        }
    }

    static void writeFrame(OutputStream out, int type, byte[] payload, int offset, int length) throws IOException {
        byte[] header = {
                (byte) type,
                (byte) (length >>> 24),
                (byte) (length >>> 16),
                (byte) (length >>> 8),
                (byte) length
        };
        out.write(header);
        if (length > 0) {
            out.write(payload, offset, length);
        }
        out.flush();
    }
}
