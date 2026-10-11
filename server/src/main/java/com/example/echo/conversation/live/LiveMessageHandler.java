package com.example.echo.conversation.live;

import com.example.echo.conversation.config.ConversationStreamProperties;
import com.example.echo.conversation.config.SpeechStreamExecutor;
import com.example.echo.conversation.dto.StreamMeta;
import com.example.echo.conversation.dto.StreamText;
import com.example.echo.conversation.dto.StreamedConversation;
import com.example.echo.conversation.service.ConversationService;
import com.example.echo.conversation.stream.ConversationStreamWriter;
import com.example.echo.conversation.stream.SpeechSegmentSource;
import com.example.echo.voice.realtime.LiveTranscript;
import com.example.echo.voice.realtime.Pcm16Resampler;
import com.example.echo.voice.realtime.RealtimeTranscriptionClient;
import com.example.echo.voice.realtime.RealtimeTranscriptionException;
import com.example.echo.voice.realtime.TranscriptionUpstream;
import com.example.echo.voice.service.VoiceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.adapter.NativeWebSocketSession;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * 실시간 음성 메시지 (/api/conversations/message-live) - 한 연결 = 한 발화(턴).
 *
 * <pre>
 * 앱 → 서버
 *   텍스트 {"type":"start","sampleRate":16000,"encoding":"pcm16le","channels":1}  연결 직후 1번
 *   바이너리 16kHz 16-bit 모노 PCM 조각                                         말하는 동안 계속
 *   텍스트 {"type":"commit"}                                                   말 끝
 * 서버 → 앱
 *   바이너리 /message-stream과 같은 프레임(META → TEXT → AUDIO... → END), 프레임 1개 = 메시지 1개. END 뒤 1000으로 닫음
 *   텍스트 {"type":"error","code":"...","message":"..."} 뒤 닫음 - 첫 프레임 전 실패만 (그 뒤 실패는 END 없이 닫음)
 * </pre>
 *
 * 오류 code - 앱은 이 값으로 폴백 여부를 정한다:
 *   UPSTREAM_UNAVAILABLE commit 전 OpenAI 실패 → 아직 아무것도 처리하지 않았으므로 앱이 WAV로 /message-stream 폴백
 *   STT_FAILED           commit 후 전사 실패/시간 초과 → 재전송 없이 오류 안내
 *   PROCESSING_FAILED    LLM/TTS 시작 실패 등 → 재전송 없이 오류 안내
 *   BAD_REQUEST          규격 위반 (1008로 닫음)
 * message에는 개발용 짧은 설명만 넣는다 - 발화 내용·서버 내부 원문을 싣지 않는다.
 *
 * 말하는 동안 소리는 24kHz로 리샘플해 OpenAI로 바로 중계하고, 무음 판정용으로 원본 PCM도 모은다.
 * commit 뒤의 처리(전사 대기 → LLM → TTS → 프레임 전송)는 WebSocket 스레드를 막지 않도록 스트리밍 전용 풀에서 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveMessageHandler extends AbstractWebSocketHandler {

    static final String ATTR_USER_ID = "userId";
    private static final String ATTR_TURN = "liveTurn";

    static final int SAMPLE_RATE = 16_000;
    private static final String ENCODING = "pcm16le";
    /** 앱 최대 녹음 60초 + 여유. 넘으면 규격 위반으로 닫는다 */
    private static final long MAX_AUDIO_BYTES = 70L * SAMPLE_RATE * 2;
    private static final int MAX_BINARY_MESSAGE_BYTES = 64 * 1024;
    /** 주고받는 메시지 없이 이만큼 지나면 컨테이너가 연결을 닫는다 (commit 없이 붙어 있는 연결 정리) */
    private static final long IDLE_TIMEOUT_MS = 60_000;
    private static final int SEND_TIME_LIMIT_MS = 10_000;
    private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;

    static final String ERROR_UPSTREAM_UNAVAILABLE = "UPSTREAM_UNAVAILABLE";
    static final String ERROR_STT_FAILED = "STT_FAILED";
    static final String ERROR_PROCESSING_FAILED = "PROCESSING_FAILED";
    static final String ERROR_BAD_REQUEST = "BAD_REQUEST";

    private final ConversationService conversationService;
    private final VoiceService voiceService;
    private final RealtimeTranscriptionClient transcriptionClient;
    private final ObjectMapper objectMapper;
    private final ConversationStreamProperties streamProperties;
    private final SpeechStreamExecutor speechStreamExecutor;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.setBinaryMessageSizeLimit(MAX_BINARY_MESSAGE_BYTES);
        if (session instanceof NativeWebSocketSession nativeSession) {
            jakarta.websocket.Session container = nativeSession.getNativeSession(jakarta.websocket.Session.class);
            if (container != null) {
                container.setMaxIdleTimeout(IDLE_TIMEOUT_MS);
            }
        }
        Long userId = (Long) session.getAttributes().get(ATTR_USER_ID);
        // 응답 프레임(풀 스레드)과 오류 알림(OpenAI 콜백 스레드)이 동시에 보낼 수 있어 전송을 직렬화한다
        WebSocketSession sender = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_LIMIT_BYTES);
        session.getAttributes().put(ATTR_TURN, new LiveTurn(sender, userId));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        LiveTurn turn = turnOf(session);
        JsonNode json;
        try {
            json = objectMapper.readTree(message.getPayload());
        } catch (JsonProcessingException e) {
            turn.fail(ERROR_BAD_REQUEST, "JSON이 아닌 텍스트 메시지", CloseStatus.POLICY_VIOLATION);
            return;
        }
        switch (json.path("type").asText()) {
            case "start" -> onStart(turn, json);
            case "commit" -> onCommit(turn);
            default -> turn.fail(ERROR_BAD_REQUEST, "알 수 없는 메시지 type", CloseStatus.POLICY_VIOLATION);
        }
    }

    private void onStart(LiveTurn turn, JsonNode json) {
        boolean validFormat = json.path("sampleRate").asInt() == SAMPLE_RATE
                && ENCODING.equals(json.path("encoding").asText())
                && json.path("channels").asInt() == 1;
        if (!validFormat) {
            turn.fail(ERROR_BAD_REQUEST, "지원하지 않는 오디오 형식 (16000Hz pcm16le mono만 지원)", CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (!turn.start(() -> transcriptionClient.open(turn::onUpstreamFailure))) {
            turn.fail(ERROR_BAD_REQUEST, "start가 두 번 옴", CloseStatus.POLICY_VIOLATION);
        }
    }

    private void onCommit(LiveTurn turn) {
        if (!turn.markCommitted()) {
            turn.fail(ERROR_BAD_REQUEST, "start 전이거나 이미 commit됨", CloseStatus.POLICY_VIOLATION);
            return;
        }
        try {
            speechStreamExecutor.executor().execute(() -> finishTurn(turn));
        } catch (RejectedExecutionException e) {
            log.error("스트리밍 풀이 가득 차 실시간 메시지를 처리하지 못함 - userId: {}", turn.userId);
            turn.fail(ERROR_PROCESSING_FAILED, "서버가 바쁨", CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        LiveTurn turn = turnOf(session);
        byte[] pcm = new byte[message.getPayload().remaining()];
        message.getPayload().get(pcm);
        String violation = turn.append(pcm);
        if (violation != null) {
            turn.fail(ERROR_BAD_REQUEST, violation, CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.info("실시간 메시지 연결 오류 - {}", exception.getClass().getSimpleName());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        LiveTurn turn = (LiveTurn) session.getAttributes().get(ATTR_TURN);
        if (turn != null) {
            turn.dispose();
        }
    }

    private static LiveTurn turnOf(WebSocketSession session) {
        return (LiveTurn) session.getAttributes().get(ATTR_TURN);
    }

    /** commit 뒤 처리 (스트리밍 풀 스레드) */
    private void finishTurn(LiveTurn turn) {
        byte[] pcm = turn.pcm();
        boolean silent = voiceService.isEffectivelySilent(pcm, SAMPLE_RATE);

        StreamedConversation result;
        try {
            result = conversationService.processLiveMessageStream(turn.userId, () -> awaitTranscript(turn, silent));
        } catch (RealtimeTranscriptionException e) {
            log.warn("실시간 전사 실패 - userId: {}, {}", turn.userId, e.getMessage());
            turn.fail(ERROR_STT_FAILED, "전사 실패", CloseStatus.SERVER_ERROR);
            return;
        } catch (RuntimeException e) {
            log.error("실시간 메시지 처리 실패 - userId: {}", turn.userId, e);
            turn.fail(ERROR_PROCESSING_FAILED, "응답 준비 실패", CloseStatus.SERVER_ERROR);
            return;
        }
        writeFrames(turn, result);
    }

    private String awaitTranscript(LiveTurn turn, boolean silent) {
        TranscriptionUpstream upstream = turn.upstream();
        try {
            if (silent) {
                log.info("오디오 길이/에너지 기준 미달로 실시간 전사 결과를 기다리지 않음 - {} bytes", turn.pcmSize());
                return "";
            }
            LiveTranscript transcript = upstream.commit().join();
            // 신뢰도는 기록만 한다(필터 미사용) - 기준값은 실기기 데이터를 모은 뒤 정한다
            log.info("[STT 신뢰도] userId={}, avgLogprob={}, chars={}",
                    turn.userId, transcript.avgLogprob(), transcript.text().length());
            return voiceService.filterLiveTranscript(transcript.text());
        } catch (CompletionException e) {
            if (e.getCause() instanceof RealtimeTranscriptionException cause) {
                throw cause;
            }
            if (e.getCause() instanceof TimeoutException) {
                throw new RealtimeTranscriptionException("최종 전사 대기 시간 초과", e.getCause());
            }
            throw new RealtimeTranscriptionException("최종 전사 대기 실패", e.getCause());
        } finally {
            upstream.close();
        }
    }

    /** 응답 프레임 전송 - /message-stream의 ConversationController.writeStream과 같은 규칙 */
    private void writeFrames(LiveTurn turn, StreamedConversation result) {
        try (SpeechSegmentSource segments = result.segments()) {
            byte[] meta = objectMapper.writeValueAsBytes(new StreamMeta(result.userMessage()));
            ConversationStreamWriter.Result outcome = ConversationStreamWriter.write(
                    new WebSocketFrameOutputStream(turn.sender), meta, segments, this::encodeText,
                    streamProperties.getSegmentGapMs());
            switch (outcome) {
                case COMPLETED -> {
                    result.onStreamCompleted().run();
                    turn.close(CloseStatus.NORMAL);
                }
                case UPSTREAM_FAILED -> {
                    // END 없이 닫는다 - 앱은 잘린 스트림으로 보고 /tts-retry로 폴백한다
                    log.error("응답 조각 준비/TTS 스트림이 중간에 실패해 END 없이 종료 - userId: {}", turn.userId);
                    turn.close(CloseStatus.SERVER_ERROR);
                }
                case CLIENT_DISCONNECTED -> log.info("클라이언트가 연결을 끊어 스트리밍을 중단 - userId: {}", turn.userId);
            }
        } catch (JsonProcessingException | RuntimeException e) {
            log.error("실시간 메시지 응답 전송 중 예상치 못한 실패 - userId: {}", turn.userId, e);
            turn.close(CloseStatus.SERVER_ERROR);
        }
    }

    private byte[] encodeText(String text) {
        try {
            return objectMapper.writeValueAsBytes(new StreamText(text));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("TEXT 프레임 직렬화 실패", e);
        }
    }

    private String errorJson(String code, String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("type", "error", "code", code, "message", message));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("오류 메시지 직렬화 실패", e);
        }
    }

    /** 한 연결(발화)의 상태 */
    final class LiveTurn {

        final WebSocketSession sender;
        final Long userId;
        private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        private final Pcm16Resampler resampler = new Pcm16Resampler(SAMPLE_RATE, RealtimeTranscriptionClient.SAMPLE_RATE);
        private TranscriptionUpstream upstream;
        private boolean committed;
        private boolean finished;

        LiveTurn(WebSocketSession sender, Long userId) {
            this.sender = sender;
            this.userId = userId;
        }

        synchronized boolean start(java.util.function.Supplier<TranscriptionUpstream> open) {
            if (upstream != null || finished) {
                return false;
            }
            upstream = open.get();
            return true;
        }

        /** @return 규격 위반이면 사유, 아니면 null */
        synchronized String append(byte[] chunk) {
            if (finished) {
                return null;
            }
            if (upstream == null || committed) {
                return "start 전이거나 commit 뒤에 온 소리";
            }
            if (pcm.size() + chunk.length > MAX_AUDIO_BYTES) {
                return "발화가 너무 김";
            }
            pcm.write(chunk, 0, chunk.length);
            upstream.append(resampler.process(chunk));
            return null;
        }

        synchronized boolean markCommitted() {
            if (upstream == null || committed || finished) {
                return false;
            }
            committed = true;
            return true;
        }

        synchronized byte[] pcm() {
            return pcm.toByteArray();
        }

        synchronized int pcmSize() {
            return pcm.size();
        }

        synchronized TranscriptionUpstream upstream() {
            return upstream;
        }

        /** commit 전에 OpenAI 세션이 실패함 - 앱이 폴백하도록 알린다. commit 뒤라면 commit 결과로 처리되므로 무시 */
        void onUpstreamFailure(Throwable cause) {
            synchronized (this) {
                if (committed || finished) {
                    return;
                }
            }
            log.warn("commit 전 실시간 전사 실패 - 앱에 폴백 요청 - userId: {}, {}", userId, cause.getMessage());
            fail(ERROR_UPSTREAM_UNAVAILABLE, "실시간 전사를 쓸 수 없음", CloseStatus.SERVER_ERROR);
        }

        /** 오류 메시지를 보내고 닫는다 (한 번만) */
        void fail(String code, String message, CloseStatus status) {
            if (!finish()) {
                return;
            }
            try {
                sender.sendMessage(new TextMessage(errorJson(code, message)));
            } catch (IOException | RuntimeException e) {
                log.info("오류 메시지 전송 실패(이미 끊긴 연결) - userId: {}", userId);
            }
            closeQuietly(status);
        }

        /** 정상/잘림 종료 (한 번만) */
        void close(CloseStatus status) {
            if (finish()) {
                closeQuietly(status);
            }
        }

        /** 연결이 닫힘 - OpenAI 세션을 정리한다 */
        void dispose() {
            finish();
            TranscriptionUpstream current = upstream();
            if (current != null) {
                current.close();
            }
        }

        private synchronized boolean finish() {
            if (finished) {
                return false;
            }
            finished = true;
            return true;
        }

        private void closeQuietly(CloseStatus status) {
            try {
                sender.close(status);
            } catch (IOException | RuntimeException e) {
                log.debug("WebSocket 닫기 실패(이미 닫힘) - userId: {}", userId);
            }
        }
    }
}
