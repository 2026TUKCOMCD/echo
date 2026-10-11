/*
 * OpenAI Realtime 전사 클라이언트 (WebSocket, JDK java.net.http)
 *
 * 흐름: 연결 → session.update(전사 전용, 24kHz PCM, turn_detection 없음) → input_audio_buffer.append(base64 PCM)...
 *      → input_audio_buffer.commit → conversation.item.input_audio_transcription.completed
 *
 * turn_detection을 끄는 이유: 말 끝은 앱의 VAD가 판단해 commit으로 알려 준다(서버 VAD와 이중으로 끊기지 않도록).
 */
package com.example.echo.voice.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Component
public class RealtimeTranscriptionClient {

    public static final int SAMPLE_RATE = 24_000;

    private final RealtimeTranscriptionProperties properties;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final HttpClient httpClient;

    public RealtimeTranscriptionClient(RealtimeTranscriptionProperties properties, ObjectMapper objectMapper,
                                       @Value("${openai.api.key}") String apiKey) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
    }

    /**
     * 한 발화짜리 전사 세션을 연다. 연결은 비동기로 진행되고, 그 사이 보낸 소리는 연결 뒤 순서대로 전송된다.
     *
     * @param onFailureBeforeCommit commit 전에 세션이 실패하면 한 번 호출된다(연결 실패, OpenAI 오류, 끊김).
     *                              commit 뒤의 실패는 commit()의 결과로만 알린다
     */
    public TranscriptionUpstream open(Consumer<Throwable> onFailureBeforeCommit) {
        Session session = new Session(onFailureBeforeCommit);
        CompletableFuture<WebSocket> connecting = httpClient.newWebSocketBuilder()
                .header("Authorization", "Bearer " + apiKey)
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .buildAsync(URI.create(properties.getUrl()), session)
                .toCompletableFuture();
        session.start(connecting, sessionUpdate());
        return session;
    }

    private String sessionUpdate() {
        Map<String, Object> transcription = new LinkedHashMap<>();
        transcription.put("model", properties.getModel());
        transcription.put("languages", List.of(properties.getLanguage()));
        if (!properties.getKeywords().isEmpty()) {
            transcription.put("keywords", properties.getKeywords());
        }

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("format", Map.of("type", "audio/pcm", "rate", SAMPLE_RATE));
        input.put("transcription", transcription);
        input.put("turn_detection", null);
        if (properties.getNoiseReduction() != null && !properties.getNoiseReduction().isBlank()) {
            input.put("noise_reduction", Map.of("type", properties.getNoiseReduction()));
        }

        Map<String, Object> session = new LinkedHashMap<>();
        session.put("type", "transcription");
        session.put("audio", Map.of("input", input));
        if (properties.isIncludeLogprobs()) {
            session.put("include", List.of("item.input_audio_transcription.logprobs"));
        }
        return toJson(Map.of("type", "session.update", "session", session));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            // Map/문자열만 담아 사실상 발생하지 않는다
            throw new IllegalStateException("Realtime 이벤트 직렬화 실패", e);
        }
    }

    /** 세션 하나 = 발화 하나. java.net.http.WebSocket은 이전 전송이 끝나야 다음을 보낼 수 있어 전송을 사슬로 잇는다 */
    private final class Session implements TranscriptionUpstream, WebSocket.Listener {

        private final Consumer<Throwable> onFailureBeforeCommit;
        private final CompletableFuture<LiveTranscript> transcript = new CompletableFuture<>();
        private final StringBuilder textBuffer = new StringBuilder();
        private CompletableFuture<WebSocket> sendChain;
        private boolean committed;
        private boolean failed;
        private boolean closed;

        Session(Consumer<Throwable> onFailureBeforeCommit) {
            this.onFailureBeforeCommit = onFailureBeforeCommit;
        }

        synchronized void start(CompletableFuture<WebSocket> connecting, String sessionUpdate) {
            sendChain = connecting;
            connecting.whenComplete((ws, e) -> {
                if (e != null) {
                    fail(new RealtimeTranscriptionException("OpenAI Realtime 연결 실패", e));
                }
            });
            send(sessionUpdate);
        }

        @Override
        public synchronized void append(byte[] pcm24k) {
            if (pcm24k.length == 0 || failed || closed) {
                return;
            }
            send(toJson(Map.of("type", "input_audio_buffer.append",
                    "audio", Base64.getEncoder().encodeToString(pcm24k))));
        }

        @Override
        public synchronized CompletableFuture<LiveTranscript> commit() {
            committed = true;
            if (!failed && !closed) {
                send(toJson(Map.of("type", "input_audio_buffer.commit")));
            }
            return transcript.orTimeout(properties.getTranscriptTimeoutSeconds(), TimeUnit.SECONDS);
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            transcript.completeExceptionally(new RealtimeTranscriptionException("전사 세션이 닫힘"));
            sendChain.whenComplete((ws, e) -> {
                if (ws != null && !ws.isOutputClosed()) {
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "").exceptionally(ignored -> null);
                }
            });
        }

        private void send(String json) {
            sendChain = sendChain.thenCompose(ws -> ws.sendText(json, true));
            sendChain.whenComplete((ws, e) -> {
                if (e != null) {
                    fail(new RealtimeTranscriptionException("OpenAI Realtime 전송 실패", e));
                }
            });
        }

        private void fail(Throwable cause) {
            boolean notify;
            synchronized (this) {
                if (failed || closed) {
                    return;
                }
                failed = true;
                notify = !committed;
            }
            transcript.completeExceptionally(cause);
            if (notify) {
                onFailureBeforeCommit.accept(cause);
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                String message = textBuffer.toString();
                textBuffer.setLength(0);
                handleEvent(message);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            fail(new RealtimeTranscriptionException("OpenAI Realtime 연결 종료 - status: " + statusCode));
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            fail(new RealtimeTranscriptionException("OpenAI Realtime 연결 오류", error));
        }

        private void handleEvent(String message) {
            JsonNode event;
            try {
                event = objectMapper.readTree(message);
            } catch (Exception e) {
                log.warn("OpenAI Realtime 이벤트 파싱 실패 - 무시");
                return;
            }
            switch (event.path("type").asText()) {
                case "conversation.item.input_audio_transcription.completed" ->
                        transcript.complete(new LiveTranscript(
                                event.path("transcript").asText("").strip(), averageLogprob(event.path("logprobs"))));
                case "conversation.item.input_audio_transcription.failed" ->
                        fail(new RealtimeTranscriptionException("전사 실패 - " + errorSummary(event)));
                case "error" -> fail(new RealtimeTranscriptionException("OpenAI Realtime 오류 - " + errorSummary(event)));
                default -> {
                    // session.created/updated, delta 등은 쓰지 않는다
                }
            }
        }
    }

    /** 오류의 종류만 남긴다 - message에 사용자 발화가 섞일 수 있어 로그/예외로 옮기지 않는다 */
    private static String errorSummary(JsonNode event) {
        JsonNode error = event.path("error");
        return "type=" + error.path("type").asText("?") + ", code=" + error.path("code").asText("?");
    }

    private static Double averageLogprob(JsonNode logprobs) {
        if (!logprobs.isArray() || logprobs.isEmpty()) {
            return null;
        }
        List<Double> values = new ArrayList<>();
        logprobs.forEach(token -> {
            if (token.path("logprob").isNumber()) {
                values.add(token.path("logprob").asDouble());
            }
        });
        return values.isEmpty() ? null : values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
