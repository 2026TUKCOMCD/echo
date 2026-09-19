package com.example.echo.conversation.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConversationStreamWriter - 프레임 스트림 작성")
class ConversationStreamWriterTest {

    private record Frame(int type, byte[] payload) {
    }

    /** 응답 본문 바이트를 프레임 목록으로 파싱 (클라이언트 파서와 같은 규칙) */
    private static List<Frame> parseFrames(byte[] body) {
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

    @Test
    @DisplayName("META → AUDIO → END 순서로 프레임을 쓰고 정상 종료(COMPLETED)를 반환한다")
    void write_success_metaThenAudioThenEnd() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] meta = "{\"userMessage\":\"안녕\",\"aiResponse\":\"반가워요\"}".getBytes(StandardCharsets.UTF_8);
        byte[] audio = "mp3-bytes".getBytes(StandardCharsets.UTF_8);

        ConversationStreamWriter.Result result =
                ConversationStreamWriter.write(out, meta, new ByteArrayInputStream(audio));

        assertThat(result).isEqualTo(ConversationStreamWriter.Result.COMPLETED);
        List<Frame> frames = parseFrames(out.toByteArray());
        assertThat(frames).hasSize(3);
        assertThat(frames.get(0).type()).isEqualTo(ConversationStreamWriter.TYPE_META);
        assertThat(frames.get(0).payload()).isEqualTo(meta);
        assertThat(frames.get(1).type()).isEqualTo(ConversationStreamWriter.TYPE_AUDIO);
        assertThat(frames.get(1).payload()).isEqualTo(audio);
        assertThat(frames.get(2).type()).isEqualTo(ConversationStreamWriter.TYPE_END);
        assertThat(frames.get(2).payload()).isEmpty();
    }

    @Test
    @DisplayName("길이는 4바이트 big-endian으로 기록된다")
    void writeFrame_lengthIsBigEndian() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] payload = new byte[0x0102];

        ConversationStreamWriter.writeFrame(out, ConversationStreamWriter.TYPE_AUDIO, payload, 0, payload.length);

        byte[] bytes = out.toByteArray();
        assertThat(bytes[0]).isEqualTo((byte) ConversationStreamWriter.TYPE_AUDIO);
        assertThat(bytes[1]).isEqualTo((byte) 0x00);
        assertThat(bytes[2]).isEqualTo((byte) 0x00);
        assertThat(bytes[3]).isEqualTo((byte) 0x01);
        assertThat(bytes[4]).isEqualTo((byte) 0x02);
        assertThat(bytes).hasSize(5 + 0x0102);
    }

    @Test
    @DisplayName("큰 오디오는 여러 AUDIO 프레임으로 나뉘고, 이어 붙이면 원본과 같다")
    void write_largeAudio_splitIntoMultipleFramesAndReassembles() {
        byte[] audio = new byte[20 * 1024];
        for (int i = 0; i < audio.length; i++) {
            audio[i] = (byte) (i % 251);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ConversationStreamWriter.write(out, "{}".getBytes(StandardCharsets.UTF_8), new ByteArrayInputStream(audio));

        List<Frame> audioFrames = parseFrames(out.toByteArray()).stream()
                .filter(f -> f.type() == ConversationStreamWriter.TYPE_AUDIO)
                .toList();
        assertThat(audioFrames.size()).isGreaterThan(1);
        ByteArrayOutputStream reassembled = new ByteArrayOutputStream();
        audioFrames.forEach(f -> reassembled.writeBytes(f.payload()));
        assertThat(reassembled.toByteArray()).isEqualTo(audio);
    }

    @Test
    @DisplayName("TTS 스트림 읽기가 중간에 실패하면 UPSTREAM_FAILED를 반환하고 END 프레임을 쓰지 않는다")
    void write_upstreamFailsMidway_noEndFrame() {
        InputStream failingAudio = new InputStream() {
            private int calls = 0;

            @Override
            public int read() throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (calls++ == 0) {
                    b[off] = 1;
                    b[off + 1] = 2;
                    return 2;
                }
                throw new IOException("upstream reset");
            }
        };
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ConversationStreamWriter.Result result =
                ConversationStreamWriter.write(out, "{}".getBytes(StandardCharsets.UTF_8), failingAudio);

        assertThat(result).isEqualTo(ConversationStreamWriter.Result.UPSTREAM_FAILED);
        List<Frame> frames = parseFrames(out.toByteArray());
        assertThat(frames).extracting(Frame::type)
                .containsExactly(ConversationStreamWriter.TYPE_META, ConversationStreamWriter.TYPE_AUDIO);
    }

    @Test
    @DisplayName("클라이언트 연결이 끊겨 쓰기가 실패하면 CLIENT_DISCONNECTED를 반환한다")
    void write_clientGone_returnsClientDisconnected() {
        OutputStream brokenOut = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("broken pipe");
            }
        };

        ConversationStreamWriter.Result result = ConversationStreamWriter.write(
                brokenOut, "{}".getBytes(StandardCharsets.UTF_8), new ByteArrayInputStream(new byte[]{1}));

        assertThat(result).isEqualTo(ConversationStreamWriter.Result.CLIENT_DISCONNECTED);
    }
}
