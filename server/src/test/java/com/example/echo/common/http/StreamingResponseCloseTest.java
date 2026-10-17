package com.example.echo.common.http;

import com.example.echo.ai.stream.ChatCompletionStream;
import com.example.echo.voice.client.ElevenLabsTtsClient;
import com.example.echo.voice.exception.RetryableVoiceException;
import com.example.echo.voice.exception.VoiceProcessingException;
import com.example.echo.voice.provider.ElevenLabsTtsProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.Response;
import feign.hc5.ApacheHttp5Client;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 스트림을 다 읽기 전에 닫으면 HTTP 연결을 바로 끊어야 한다 - 운영과 같은 feign-hc5 클라이언트로 확인한다.
 *
 * feign-hc5의 응답 close()는 연결을 재사용하려고 남은 본문을 끝까지 읽는다(drain). LLM·TTS 스트림에서는
 * 상대가 생성을 마칠 때까지 close()가 돌아오지 않고, 상대는 끊긴 줄 모르고 끝까지 생성(과금)한다.
 */
@DisplayName("스트리밍 응답 - 다 읽기 전에 닫으면 남은 본문을 기다리지 않고 연결을 끊는다")
class StreamingResponseCloseTest {

    /** 가짜 서버가 응답을 계속 보내는 시간 - 닫기가 이만큼 기다리면 drain한 것 */
    private static final long SERVER_STREAM_MS = 3000;
    private static final long CLOSE_LIMIT_MS = 1000;

    interface StreamApi {
        @RequestLine("POST /sse")
        @Headers("Content-Type: application/json")
        Response sse(String body);

        @RequestLine("POST /audio")
        @Headers("Content-Type: application/json")
        Response audio(String body);

        @RequestLine("POST /sse-short")
        @Headers("Content-Type: application/json")
        Response sseShort(String body);

        @RequestLine("POST /audio-short")
        @Headers("Content-Type: application/json")
        Response audioShort(String body);

        @RequestLine("GET /quick")
        String quick();
    }

    private HttpServer server;
    private ExecutorService serverThreads;
    private CountDownLatch serverSawDisconnect;
    /** 경로별 마지막 요청의 클라이언트 포트 - 같으면 같은 연결을 재사용한 것 */
    private final Map<String, Integer> clientPortByPath = new ConcurrentHashMap<>();
    private StreamApi api;

    @BeforeEach
    void setUp() throws IOException {
        serverSawDisconnect = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sse", exchange -> slowStream(exchange, "text/event-stream",
                "data: {\"choices\":[{\"delta\":{\"content\":\"안녕\"},\"finish_reason\":null}]}\n\n",
                ": OPENROUTER PROCESSING\n\n", "data: [DONE]\n\n"));
        server.createContext("/audio", exchange -> slowStream(exchange, "audio/mpeg",
                "ID3-first-chunk", "mp3-chunk", ""));
        server.createContext("/sse-short", exchange -> shortStream(exchange, "text/event-stream",
                "data: {\"choices\":[{\"delta\":{\"content\":\"안녕\"},\"finish_reason\":null}]}\n\ndata: [DONE]\n\n"));
        server.createContext("/audio-short", exchange -> shortStream(exchange, "audio/mpeg", "ID3-short-audio"));
        server.createContext("/quick", exchange -> {
            clientPortByPath.put("/quick", exchange.getRemoteAddress().getPort());
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        serverThreads = Executors.newCachedThreadPool();
        server.setExecutor(serverThreads);
        server.start();

        // 운영과 같은 클라이언트 (커넥션 풀링 ApacheHttp5Client)
        api = Feign.builder()
                .client(new ApacheHttp5Client())
                .target(StreamApi.class, "http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        serverThreads.shutdownNow();
    }

    private void shortStream(HttpExchange exchange, String contentType, String body) throws IOException {
        clientPortByPath.put(exchange.getRequestURI().getPath(), exchange.getRemoteAddress().getPort());
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, 0); // chunked
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** 첫 조각을 바로 보낸 뒤 SERVER_STREAM_MS 동안 100ms마다 조각을 더 보낸다. 클라이언트가 끊으면 기록한다 */
    private void slowStream(HttpExchange exchange, String contentType, String first, String repeat, String last)
            throws IOException {
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, 0); // chunked
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(first.getBytes(StandardCharsets.UTF_8));
            out.flush();
            long end = System.currentTimeMillis() + SERVER_STREAM_MS;
            while (System.currentTimeMillis() < end) {
                Thread.sleep(100);
                out.write(repeat.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            out.write(last.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            serverSawDisconnect.countDown();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("LLM 스트림(ChatCompletionStream): 첫 조각만 읽고 닫으면 바로 돌아오고, 상대 서버도 연결이 끊긴 것을 안다")
    void llmStream_closeEarly_abortsWithoutDrain() throws Exception {
        Response response = api.sse("{}");
        ChatCompletionStream stream = new ChatCompletionStream(
                response.body().asInputStream(), response, new ObjectMapper(), null);
        assertThat(stream.nextDelta()).isEqualTo("안녕");

        long closeMs = timeMs(stream::close);

        assertThat(closeMs).as("닫기가 상대의 응답 완료(%dms)를 기다리면 drain한 것", SERVER_STREAM_MS)
                .isLessThan(CLOSE_LIMIT_MS);
        assertThat(serverSawDisconnect.await(2, TimeUnit.SECONDS)).as("상대가 끊긴 걸 알아야 생성을 멈춘다").isTrue();
        assertThat(api.quick()).as("끊은 뒤에도 커넥션 풀은 정상").isEqualTo("ok");
    }

    @Test
    @DisplayName("TTS 스트림(ElevenLabsTtsProvider): 일부만 읽고 닫으면 바로 돌아오고, 상대 서버도 연결이 끊긴 것을 안다")
    void ttsStream_closeEarly_abortsWithoutDrain() throws Exception {
        ElevenLabsTtsProvider provider = providerReturning(() -> api.audio("{}"));

        InputStream audio = provider.synthesizeStream("안녕하세요", null);
        assertThat(audio.read()).isNotEqualTo(-1);

        long closeMs = timeMs(audio::close);

        assertThat(closeMs).as("닫기가 상대의 응답 완료(%dms)를 기다리면 drain한 것", SERVER_STREAM_MS)
                .isLessThan(CLOSE_LIMIT_MS);
        assertThat(serverSawDisconnect.await(2, TimeUnit.SECONDS)).as("상대가 끊긴 걸 알아야 합성을 멈춘다").isTrue();
        assertThat(api.quick()).as("끊은 뒤에도 커넥션 풀은 정상").isEqualTo("ok");
    }

    @Test
    @DisplayName("LLM 스트림을 [DONE]까지 받고 닫으면 연결을 끊지 않고 재사용한다 (대화 턴마다 새 연결을 맺지 않음)")
    void llmStream_readToDone_keepsConnectionForReuse() throws Exception {
        Response response = api.sseShort("{}");
        ChatCompletionStream stream = new ChatCompletionStream(
                response.body().asInputStream(), response, new ObjectMapper(), null);
        assertThat(stream.nextDelta()).isEqualTo("안녕");
        assertThat(stream.nextDelta()).isNull();
        stream.close();

        assertThat(api.quick()).isEqualTo("ok");
        assertThat(clientPortByPath.get("/quick")).isEqualTo(clientPortByPath.get("/sse-short"));
    }

    @Test
    @DisplayName("TTS 스트림을 끝까지 읽고 닫으면 연결을 끊지 않고 재사용한다")
    void ttsStream_readToEnd_keepsConnectionForReuse() throws Exception {
        ElevenLabsTtsProvider provider = providerReturning(() -> api.audioShort("{}"));

        try (InputStream audio = provider.synthesizeStream("안녕하세요", null)) {
            assertThat(new String(audio.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("ID3-short-audio");
        }

        assertThat(api.quick()).isEqualTo("ok");
        assertThat(clientPortByPath.get("/quick")).isEqualTo(clientPortByPath.get("/audio-short"));
    }

    private static ElevenLabsTtsProvider providerReturning(java.util.function.Supplier<Response> response) {
        ElevenLabsTtsClient client = mock(ElevenLabsTtsClient.class);
        when(client.synthesizeStream(any(), any())).thenAnswer(invocation -> response.get());
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(1,
                Map.of(RetryableVoiceException.class, true, VoiceProcessingException.class, false), true));
        ElevenLabsTtsProvider provider = new ElevenLabsTtsProvider(client, retryTemplate);
        ReflectionTestUtils.setField(provider, "voiceId", "voice");
        ReflectionTestUtils.setField(provider, "model", "model");
        return provider;
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }

    private static long timeMs(IoAction action) throws IOException {
        long start = System.nanoTime();
        action.run();
        return (System.nanoTime() - start) / 1_000_000;
    }
}
