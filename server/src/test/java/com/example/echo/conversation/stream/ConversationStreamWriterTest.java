package com.example.echo.conversation.stream;

import com.example.echo.conversation.stream.TestSpeechSegments.Frame;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;

import static com.example.echo.conversation.stream.TestSpeechSegments.parseFrames;
import static com.example.echo.conversation.stream.TestSpeechSegments.segment;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConversationStreamWriter - 프레임 스트림 작성")
class ConversationStreamWriterTest {

    private static final byte[] META = "{\"userMessage\":\"안녕\"}".getBytes(StandardCharsets.UTF_8);
    private static final Function<String, byte[]> TEXT = text -> ("{\"text\":\"" + text + "\"}").getBytes(StandardCharsets.UTF_8);

    @Test
    @DisplayName("META → TEXT → AUDIO → TEXT → AUDIO → END 순서로 조각마다 텍스트를 오디오 앞에 쓴다")
    void write_success_textBeforeEachSegmentAudio() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TestSpeechSegments segments = TestSpeechSegments.of(
                segment("그러셨군요! 산책은 즐거우셨어요?", "mp3-1"),
                segment("어디로 다녀오셨는지 궁금해요.", "mp3-2"));

        ConversationStreamWriter.Result result = ConversationStreamWriter.write(out, META, segments, TEXT);

        assertThat(result).isEqualTo(ConversationStreamWriter.Result.COMPLETED);
        List<Frame> frames = parseFrames(out.toByteArray());
        assertThat(frames).extracting(Frame::type).containsExactly(
                ConversationStreamWriter.TYPE_META,
                ConversationStreamWriter.TYPE_TEXT,
                ConversationStreamWriter.TYPE_AUDIO,
                ConversationStreamWriter.TYPE_TEXT,
                ConversationStreamWriter.TYPE_AUDIO,
                ConversationStreamWriter.TYPE_END);
        assertThat(frames.get(0).payload()).isEqualTo(META);
        assertThat(frames.get(1).text()).isEqualTo("{\"text\":\"그러셨군요! 산책은 즐거우셨어요?\"}");
        assertThat(frames.get(2).text()).isEqualTo("mp3-1");
        assertThat(frames.get(3).text()).isEqualTo("{\"text\":\"어디로 다녀오셨는지 궁금해요.\"}");
        assertThat(frames.get(4).text()).isEqualTo("mp3-2");
        assertThat(frames.get(5).payload()).isEmpty();
    }

    @Test
    @DisplayName("조각이 하나뿐이어도 TEXT → AUDIO → END로 끝난다")
    void write_singleSegment() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ConversationStreamWriter.write(out, META, TestSpeechSegments.of(segment("네, 좋아요.", "mp3")), TEXT);

        assertThat(parseFrames(out.toByteArray())).extracting(Frame::type).containsExactly(
                ConversationStreamWriter.TYPE_META,
                ConversationStreamWriter.TYPE_TEXT,
                ConversationStreamWriter.TYPE_AUDIO,
                ConversationStreamWriter.TYPE_END);
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

        ConversationStreamWriter.write(out, META, TestSpeechSegments.of(segment("긴 응답", audio)), TEXT);

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

        ConversationStreamWriter.Result result = ConversationStreamWriter.write(
                out, META, TestSpeechSegments.of(new SpeechSegment("안녕", failingAudio)), TEXT);

        assertThat(result).isEqualTo(ConversationStreamWriter.Result.UPSTREAM_FAILED);
        assertThat(parseFrames(out.toByteArray())).extracting(Frame::type).containsExactly(
                ConversationStreamWriter.TYPE_META, ConversationStreamWriter.TYPE_TEXT, ConversationStreamWriter.TYPE_AUDIO);
    }

    @Test
    @DisplayName("다음 조각을 준비하지 못하면(나머지 TTS 실패) 이미 보낸 조각 뒤에 END 없이 UPSTREAM_FAILED")
    void write_nextSegmentFails_noEndFrame() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ConversationStreamWriter.Result result = ConversationStreamWriter.write(
                out, META, TestSpeechSegments.failingAfter(segment("첫 조각입니다.", "mp3-1")), TEXT);

        assertThat(result).isEqualTo(ConversationStreamWriter.Result.UPSTREAM_FAILED);
        assertThat(parseFrames(out.toByteArray())).extracting(Frame::type).containsExactly(
                ConversationStreamWriter.TYPE_META, ConversationStreamWriter.TYPE_TEXT, ConversationStreamWriter.TYPE_AUDIO);
    }

    @Test
    @DisplayName("보낸 조각의 오디오 스트림은 다 쓰면 닫는다")
    void write_closesSegmentAudio() {
        class TrackingStream extends ByteArrayInputStream {
            boolean closed;

            TrackingStream() {
                super(new byte[]{1, 2});
            }

            @Override
            public void close() throws IOException {
                closed = true;
                super.close();
            }
        }
        TrackingStream audio = new TrackingStream();

        ConversationStreamWriter.write(new ByteArrayOutputStream(), META,
                TestSpeechSegments.of(new SpeechSegment("안녕", audio)), TEXT);

        assertThat(audio.closed).isTrue();
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
                brokenOut, META, TestSpeechSegments.of(segment("안녕", "a")), TEXT);

        assertThat(result).isEqualTo(ConversationStreamWriter.Result.CLIENT_DISCONNECTED);
    }

    @Test
    @DisplayName("두 번째 조각부터 TEXT 뒤, 오디오 앞에 앞 조각 형식의 무음 프레임을 넣는다")
    void write_withGap_insertsSilenceBeforeLaterSegments() {
        byte[] first = Mp3SilenceTest.frames(Mp3SilenceTest.MPEG1_HEADER, 417, 3, false);
        byte[] rest = Mp3SilenceTest.frames(Mp3SilenceTest.MPEG1_HEADER, 417, 2, false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ConversationStreamWriter.Result result = ConversationStreamWriter.write(out, META,
                TestSpeechSegments.of(segment("강남 다녀오셨군요, 좋으셨겠어요.", first), segment("뭐가 제일 좋으셨어요?", rest)),
                TEXT, 350);

        assertThat(result).isEqualTo(ConversationStreamWriter.Result.COMPLETED);
        List<Frame> frames = parseFrames(out.toByteArray());
        assertThat(frames).extracting(Frame::type).containsExactly(
                ConversationStreamWriter.TYPE_META,
                ConversationStreamWriter.TYPE_TEXT,
                ConversationStreamWriter.TYPE_AUDIO,
                ConversationStreamWriter.TYPE_TEXT,
                ConversationStreamWriter.TYPE_AUDIO,
                ConversationStreamWriter.TYPE_AUDIO,
                ConversationStreamWriter.TYPE_END);
        assertThat(frames.get(2).payload()).isEqualTo(first);
        byte[] gap = frames.get(4).payload();
        assertThat(gap).hasSize(14 * 417);
        assertThat(gap).startsWith(Mp3SilenceTest.MPEG1_HEADER);
        assertThat(frames.get(5).payload()).isEqualTo(rest);
    }

    @Test
    @DisplayName("조각이 하나뿐이면 무음을 넣지 않는다")
    void write_withGap_singleSegment_noSilence() {
        byte[] audio = Mp3SilenceTest.frames(Mp3SilenceTest.MPEG1_HEADER, 417, 3, false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ConversationStreamWriter.write(out, META, TestSpeechSegments.of(segment("네, 좋아요.", audio)), TEXT, 350);

        assertThat(parseFrames(out.toByteArray())).extracting(Frame::type).containsExactly(
                ConversationStreamWriter.TYPE_META,
                ConversationStreamWriter.TYPE_TEXT,
                ConversationStreamWriter.TYPE_AUDIO,
                ConversationStreamWriter.TYPE_END);
    }

    @Test
    @DisplayName("앞 조각에서 MP3 헤더를 찾지 못하면 무음 없이 그대로 잇는다")
    void write_withGap_notMp3_noSilence() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ConversationStreamWriter.write(out, META,
                TestSpeechSegments.of(segment("첫 조각입니다.", "mp3-1"), segment("나머지.", "mp3-2")), TEXT, 350);

        List<Frame> frames = parseFrames(out.toByteArray());
        assertThat(frames).extracting(Frame::type).containsExactly(
                ConversationStreamWriter.TYPE_META,
                ConversationStreamWriter.TYPE_TEXT,
                ConversationStreamWriter.TYPE_AUDIO,
                ConversationStreamWriter.TYPE_TEXT,
                ConversationStreamWriter.TYPE_AUDIO,
                ConversationStreamWriter.TYPE_END);
        assertThat(frames.get(4).text()).isEqualTo("mp3-2");
    }

    @Test
    @DisplayName("무음 길이가 0이면 MP3여도 넣지 않는다")
    void write_gapZero_noSilence() {
        byte[] audio = Mp3SilenceTest.frames(Mp3SilenceTest.MPEG1_HEADER, 417, 3, false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ConversationStreamWriter.write(out, META,
                TestSpeechSegments.of(segment("첫 조각입니다.", audio), segment("나머지.", audio.clone())), TEXT, 0);

        assertThat(parseFrames(out.toByteArray())).filteredOn(f -> f.type() == ConversationStreamWriter.TYPE_AUDIO)
                .hasSize(2);
    }
}
