package com.example.echo.conversation.live;

import com.example.echo.conversation.config.ConversationStreamProperties;
import com.example.echo.conversation.config.SpeechStreamExecutor;
import com.example.echo.conversation.dto.StreamedConversation;
import com.example.echo.conversation.service.ConversationService;
import com.example.echo.conversation.stream.ConversationStreamWriter;
import com.example.echo.conversation.stream.SpeechSegment;
import com.example.echo.conversation.stream.SpeechSegmentSource;
import com.example.echo.voice.realtime.LiveTranscript;
import com.example.echo.voice.realtime.RealtimeTranscriptionClient;
import com.example.echo.voice.realtime.RealtimeTranscriptionException;
import com.example.echo.voice.realtime.TranscriptionUpstream;
import com.example.echo.voice.service.VoiceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class LiveMessageHandlerTest {

    private static final Long USER_ID = 7L;
    private static final byte[] MP3 = {1, 2, 3, 4};

    @Mock
    private ConversationService conversationService;
    @Mock
    private VoiceService voiceService;
    @Mock
    private RealtimeTranscriptionClient transcriptionClient;
    @Mock
    private SpeechStreamExecutor speechStreamExecutor;
    @Mock
    private TranscriptionUpstream upstream;
    @Mock
    private WebSocketSession session;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LiveMessageHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        ConversationStreamProperties streamProperties = new ConversationStreamProperties();
        streamProperties.setSegmentGapMs(0);
        handler = new LiveMessageHandler(conversationService, voiceService, transcriptionClient, objectMapper,
                streamProperties, speechStreamExecutor);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(LiveMessageHandler.ATTR_USER_ID, USER_ID);
        lenient().when(session.getAttributes()).thenReturn(attributes);
        lenient().when(session.isOpen()).thenReturn(true);
        lenient().when(speechStreamExecutor.executor()).thenReturn(Runnable::run);
        lenient().when(transcriptionClient.open(any())).thenReturn(upstream);
        lenient().when(voiceService.filterLiveTranscript(any())).thenAnswer(inv -> inv.getArgument(0));

        handler.afterConnectionEstablished(session);
    }

    private void text(String json) throws Exception {
        handler.handleMessage(session, new TextMessage(json));
    }

    private void start() throws Exception {
        text("{\"type\":\"start\",\"sampleRate\":16000,\"encoding\":\"pcm16le\",\"channels\":1}");
    }

    private void audio(int samples) throws Exception {
        handler.handleMessage(session, new BinaryMessage(ByteBuffer.wrap(new byte[samples * 2])));
    }

    /** 서비스가 받은 전사 대기 함수를 실제로 불러, 그 결과를 사용자 발화로 한 조각짜리 응답을 돌려준다 */
    @SuppressWarnings("unchecked")
    private void serviceRunsTranscript() {
        given(conversationService.processLiveMessageStream(eq(USER_ID), any())).willAnswer(inv -> {
            String userMessage = ((Supplier<String>) inv.getArgument(1)).get();
            return new StreamedConversation(userMessage,
                    SpeechSegmentSource.single(new SpeechSegment("반가워요", new ByteArrayInputStream(MP3))), () -> {
            });
        });
    }

    private List<WebSocketMessage<?>> sentMessages() throws Exception {
        ArgumentCaptor<WebSocketMessage<?>> captor = ArgumentCaptor.forClass(WebSocketMessage.class);
        then(session).should(atLeastOnce()).sendMessage(captor.capture());
        return captor.getAllValues();
    }

    private JsonNode singleError() throws Exception {
        List<WebSocketMessage<?>> messages = sentMessages();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0)).isInstanceOf(TextMessage.class);
        return objectMapper.readTree(((TextMessage) messages.get(0)).getPayload());
    }

    @Test
    @DisplayName("정상: 소리를 24kHz로 중계하고, commit 뒤 전사 결과로 응답 프레임(META→TEXT→AUDIO→END)을 보낸 뒤 정상 종료한다")
    void success() throws Exception {
        given(upstream.commit()).willReturn(CompletableFuture.completedFuture(new LiveTranscript("안녕하세요", -0.2)));
        serviceRunsTranscript();

        start();
        audio(320);
        text("{\"type\":\"commit\"}");

        ArgumentCaptor<byte[]> appended = ArgumentCaptor.forClass(byte[].class);
        then(upstream).should().append(appended.capture());
        assertThat(appended.getValue().length).as("16kHz 320샘플 → 24kHz 약 480샘플").isBetween(476 * 2, 480 * 2);

        List<WebSocketMessage<?>> messages = sentMessages();
        assertThat(messages).allMatch(m -> m instanceof BinaryMessage);
        byte[] meta = ((BinaryMessage) messages.get(0)).getPayload().array();
        assertThat(meta[0]).isEqualTo((byte) ConversationStreamWriter.TYPE_META);
        assertThat(new String(meta, 5, meta.length - 5, StandardCharsets.UTF_8)).contains("안녕하세요");
        byte[] last = ((BinaryMessage) messages.get(messages.size() - 1)).getPayload().array();
        assertThat(last[0]).isEqualTo((byte) ConversationStreamWriter.TYPE_END);

        then(session).should().close(CloseStatus.NORMAL);
        then(upstream).should(atLeastOnce()).close();
    }

    @Test
    @DisplayName("무음: 모은 PCM이 사실상 빈 오디오면 전사를 기다리지 않고 빈 발화로 처리한다")
    void silent_skipsTranscription() throws Exception {
        given(voiceService.isEffectivelySilent(any(), eq(16000))).willReturn(true);
        serviceRunsTranscript();

        start();
        audio(100);
        text("{\"type\":\"commit\"}");

        then(upstream).should(never()).commit();
        ArgumentCaptor<Supplier<String>> captor = captureTranscriptSupplier();
        assertThat(captor.getValue()).isNotNull();
        then(session).should().close(CloseStatus.NORMAL);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Supplier<String>> captureTranscriptSupplier() {
        ArgumentCaptor<Supplier<String>> captor = ArgumentCaptor.forClass(Supplier.class);
        then(conversationService).should().processLiveMessageStream(eq(USER_ID), captor.capture());
        return captor;
    }

    @Test
    @DisplayName("commit 전 OpenAI 실패: UPSTREAM_UNAVAILABLE을 알리고 닫는다 - 앱이 WAV로 폴백")
    @SuppressWarnings("unchecked")
    void upstreamFailureBeforeCommit_requestsFallback() throws Exception {
        start();
        ArgumentCaptor<Consumer<Throwable>> onFailure = ArgumentCaptor.forClass(Consumer.class);
        then(transcriptionClient).should().open(onFailure.capture());

        onFailure.getValue().accept(new RealtimeTranscriptionException("연결 실패"));

        assertThat(singleError().path("code").asText()).isEqualTo("UPSTREAM_UNAVAILABLE");
        then(session).should().close(CloseStatus.SERVER_ERROR);
        then(conversationService).should(never()).processLiveMessageStream(any(), any());
    }

    @Test
    @DisplayName("commit 뒤 전사 실패: STT_FAILED를 알리고 닫는다 - 앱은 재전송하지 않는다")
    void transcriptionFailureAfterCommit() throws Exception {
        given(upstream.commit()).willReturn(CompletableFuture.failedFuture(new RealtimeTranscriptionException("전사 실패")));
        serviceRunsTranscript();

        start();
        audio(320);
        text("{\"type\":\"commit\"}");

        assertThat(singleError().path("code").asText()).isEqualTo("STT_FAILED");
        then(session).should().close(CloseStatus.SERVER_ERROR);
    }

    @Test
    @DisplayName("commit 뒤 응답 준비 실패: PROCESSING_FAILED를 알린다")
    void processingFailure() throws Exception {
        given(conversationService.processLiveMessageStream(eq(USER_ID), any()))
                .willThrow(new IllegalStateException("LLM 실패"));

        start();
        audio(320);
        text("{\"type\":\"commit\"}");

        assertThat(singleError().path("code").asText()).isEqualTo("PROCESSING_FAILED");
    }

    @Test
    @DisplayName("형식 위반: 16kHz가 아니면 OpenAI에 연결하지 않고 BAD_REQUEST(1008)로 닫는다")
    void unsupportedFormat() throws Exception {
        text("{\"type\":\"start\",\"sampleRate\":44100,\"encoding\":\"pcm16le\",\"channels\":1}");

        assertThat(singleError().path("code").asText()).isEqualTo("BAD_REQUEST");
        then(session).should().close(CloseStatus.POLICY_VIOLATION);
        then(transcriptionClient).should(never()).open(any());
    }

    @Test
    @DisplayName("형식 위반: start 전에 소리가 오면 BAD_REQUEST로 닫는다")
    void audioBeforeStart() throws Exception {
        audio(320);

        assertThat(singleError().path("code").asText()).isEqualTo("BAD_REQUEST");
        then(session).should().close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    @DisplayName("연결이 닫히면 OpenAI 세션도 정리한다")
    void closeDisposesUpstream() throws Exception {
        start();

        handler.afterConnectionClosed(session, CloseStatus.GOING_AWAY);

        then(upstream).should().close();
    }
}
