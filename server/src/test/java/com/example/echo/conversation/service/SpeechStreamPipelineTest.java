package com.example.echo.conversation.service;

import com.example.echo.ai.exception.AIException;
import com.example.echo.ai.service.AIService;
import com.example.echo.ai.stream.ChatCompletionStream;
import com.example.echo.conversation.config.ConversationStreamProperties;
import com.example.echo.conversation.config.SpeechStreamExecutor;
import com.example.echo.conversation.stream.SpeechSegment;
import com.example.echo.conversation.stream.SpeechSegmentSource;
import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.voice.exception.VoiceProcessingException;
import com.example.echo.voice.service.VoiceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SpeechStreamPipeline - LLM 스트리밍 → 첫 조각/나머지 TTS")
class SpeechStreamPipelineTest {

    private static final String FIRST = "그러셨군요! 오늘 산책은 어디로 다녀오셨어요?";
    private static final String REST = "날씨가 좋아서 걷기 좋으셨겠어요.";
    private static final SpeechStreamPipeline.Stages STAGES = new SpeechStreamPipeline.Stages(
            "llm_first_token", "llm_first_chunk", "llm", "tts_first_byte", "tts_rest_first_byte");

    @Mock
    private VoiceService voiceService;

    @Mock
    private AIService aiService;

    private SpeechStreamExecutor executor;
    private SpeechStreamPipeline pipeline;
    private final VoiceSettings voiceSettings = VoiceSettings.builder().voiceSpeed(1.0).build();
    private final List<String> savedResponses = new CopyOnWriteArrayList<>();
    private final Map<String, Long> stages = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        executor = new SpeechStreamExecutor();
        ConversationStreamProperties properties = new ConversationStreamProperties();
        properties.setFirstChunkMinChars(15);
        properties.setFirstChunkMaxChars(80);
        properties.setLlmTimeoutSeconds(5);
        pipeline = new SpeechStreamPipeline(voiceService, aiService, executor, properties);
        lenient().when(aiService.sanitizeGarbledText(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        executor.destroy();
    }

    /** 테스트가 조각 전달 시점을 조절할 수 있는 가짜 LLM 스트림 */
    private static final class FakeLlm extends ChatCompletionStream {
        private static final String END = "\u0000END";
        private static final String FAIL = "\u0000FAIL";
        private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        final AtomicBoolean closed = new AtomicBoolean(false);

        FakeLlm() {
            super(new ByteArrayInputStream(new byte[0]), () -> {
            }, new ObjectMapper(), null);
        }

        FakeLlm send(String... deltas) {
            queue.addAll(List.of(deltas));
            return this;
        }

        FakeLlm end() {
            queue.add(END);
            return this;
        }

        FakeLlm fail() {
            queue.add(FAIL);
            return this;
        }

        @Override
        public String nextDelta() throws IOException {
            try {
                String next = queue.poll(5, TimeUnit.SECONDS);
                if (closed.get()) {
                    throw new IOException("closed");
                }
                if (next == null || FAIL.equals(next)) {
                    throw new AIException("LLM 스트림 오류");
                }
                return END.equals(next) ? null : next;
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }

        @Override
        public void close() {
            closed.set(true);
            queue.add(END);
        }
    }

    private SpeechStreamPipeline.Started start(FakeLlm llm) {
        return pipeline.start(() -> llm, voiceSettings, STAGES, stages::put, savedResponses::add);
    }

    private static String readText(SpeechSegment segment) throws IOException {
        try (var audio = segment.audio()) {
            return new String(audio.readAllBytes());
        }
    }

    private void ttsReturns(String text, String audio) {
        given(voiceService.textToSpeechStream(eq(text), any())).willReturn(new ByteArrayInputStream(audio.getBytes()));
    }

    @Test
    @DisplayName("성공: 첫 조각 TTS가 열리면 바로 반환하고, LLM이 끝나면 전체를 저장한 뒤 나머지 조각을 준다")
    void success_firstThenRest_savesFullResponse() throws IOException {
        ttsReturns(FIRST, "mp3-1");
        ttsReturns(REST, "mp3-2");
        FakeLlm llm = new FakeLlm().send("그러셨군요! ", "오늘 산책은 어디로 ", "다녀오셨어요? ", "날씨가 좋아서 ");

        SpeechStreamPipeline.Started started = start(llm);
        assertThat(started.firstText()).isEqualTo(FIRST);
        assertThat(savedResponses).as("LLM이 끝나기 전에는 저장하지 않음").isEmpty();

        try (SpeechSegmentSource segments = started.segments()) {
            SpeechSegment first = segments.next();
            assertThat(first.text()).isEqualTo(FIRST);
            assertThat(readText(first)).isEqualTo("mp3-1");

            llm.send("걷기 좋으셨겠어요.").end();
            SpeechSegment rest = segments.next();
            assertThat(rest.text()).isEqualTo(REST);
            assertThat(readText(rest)).isEqualTo("mp3-2");
            assertThat(segments.next()).isNull();
        }

        assertThat(savedResponses).containsExactly(FIRST + " " + REST);
        assertThat(stages).containsKeys("llm_first_token", "llm_first_chunk", "llm", "tts_first_byte", "tts_rest_first_byte");
    }

    @Test
    @DisplayName("첫 조각 기준을 못 채운 짧은 응답은 조각 하나로 끝난다 (TTS 1번)")
    void shortResponse_singleSegment() throws IOException {
        ttsReturns("네, 좋아요!", "mp3");
        FakeLlm llm = new FakeLlm().send("네, ", "좋아요!").end();

        SpeechStreamPipeline.Started started = start(llm);

        try (SpeechSegmentSource segments = started.segments()) {
            assertThat(segments.next().text()).isEqualTo("네, 좋아요!");
            assertThat(segments.next()).isNull();
        }
        verify(voiceService, timeout(2000)).textToSpeechStream(eq("네, 좋아요!"), any());
        assertThat(savedResponses).containsExactly("네, 좋아요!");
    }

    @Test
    @DisplayName("규칙 1: 첫 조각 전에 LLM이 실패하면 예외, TTS도 저장도 하지 않는다")
    void llmFailsBeforeFirstChunk_throws_nothingSaved() {
        FakeLlm llm = new FakeLlm().send("그러셨").fail();

        assertThatThrownBy(() -> start(llm)).isInstanceOf(AIException.class);
        verify(voiceService, never()).textToSpeechStream(anyString(), any());
        assertThat(savedResponses).isEmpty();
    }

    @Test
    @DisplayName("규칙 1: 첫 조각 TTS를 열지 못하면 예외, LLM 연결을 끊고 저장하지 않는다")
    void firstTtsFails_throws_cancelsLlm_nothingSaved() throws InterruptedException {
        given(voiceService.textToSpeechStream(eq(FIRST), any())).willThrow(new VoiceProcessingException("TTS 실패"));
        FakeLlm llm = new FakeLlm().send(FIRST + " ", "날씨가 ");

        assertThatThrownBy(() -> start(llm)).isInstanceOf(VoiceProcessingException.class);

        assertThat(llm.closed).as("LLM 생성/과금을 멈추도록 연결을 끊음").isTrue();
        Thread.sleep(200);
        assertThat(savedResponses).isEmpty();
    }

    @Test
    @DisplayName("규칙 2: 첫 조각 이후 LLM이 실패하면 첫 조각만 저장하고 나머지 없이 정상 종료")
    void llmFailsAfterFirstChunk_savesFirstOnly_endsNormally() throws IOException {
        ttsReturns(FIRST, "mp3-1");
        FakeLlm llm = new FakeLlm().send(FIRST + " ", "날씨가 ");

        SpeechStreamPipeline.Started started = start(llm);
        try (SpeechSegmentSource segments = started.segments()) {
            readText(segments.next());
            llm.fail();
            assertThat(segments.next()).as("나머지 조각 없이 END").isNull();
        }

        assertThat(savedResponses).containsExactly(FIRST);
        verify(voiceService, never()).textToSpeechStream(eq("날씨가"), any());
    }

    @Test
    @DisplayName("규칙 3: 나머지 조각 TTS가 실패하면 전체는 저장된 채로 다음 조각 요청이 IOException (END 없이 → tts-retry)")
    void restTtsFails_fullSaved_nextThrows() throws IOException {
        ttsReturns(FIRST, "mp3-1");
        given(voiceService.textToSpeechStream(eq(REST), any())).willThrow(new VoiceProcessingException("TTS 실패"));
        FakeLlm llm = new FakeLlm().send(FIRST + " ", REST).end();

        SpeechStreamPipeline.Started started = start(llm);
        try (SpeechSegmentSource segments = started.segments()) {
            readText(segments.next());
            assertThatThrownBy(segments::next).isInstanceOf(IOException.class);
        }

        assertThat(savedResponses).containsExactly(FIRST + " " + REST);
    }

    @Test
    @DisplayName("규칙 4: 클라이언트가 끊겨도 LLM은 끝까지 읽어 전체를 저장하고, 나머지 TTS는 하지 않는다")
    void clientGone_stillSavesFull_skipsRestTts() throws InterruptedException {
        ttsReturns(FIRST, "mp3-1");
        FakeLlm llm = new FakeLlm().send(FIRST + " ");

        SpeechStreamPipeline.Started started = start(llm);
        started.segments().close();          // 클라이언트 연결 끊김 → 컨트롤러가 원천을 닫음
        llm.send(REST).end();

        awaitSaved();
        assertThat(savedResponses).containsExactly(FIRST + " " + REST);
        verify(voiceService, never()).textToSpeechStream(eq(REST), any());
    }

    /** 히스토리 저장은 풀 스레드에서 일어나므로 잠시 기다린다 */
    private void awaitSaved() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (savedResponses.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
    }

    @Test
    @DisplayName("조각을 잇는 규칙: 나머지가 없으면 첫 조각 그대로, 있으면 공백 하나로 잇는다")
    void joinSegments() {
        assertThat(SpeechStreamPipeline.joinSegments("안녕하세요.", "")).isEqualTo("안녕하세요.");
        assertThat(SpeechStreamPipeline.joinSegments("안녕하세요.", "반가워요.")).isEqualTo("안녕하세요. 반가워요.");
    }
}
