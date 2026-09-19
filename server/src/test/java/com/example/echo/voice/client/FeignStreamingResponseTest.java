package com.example.echo.voice.client;

import com.sun.net.httpserver.HttpServer;
import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ElevenLabsTtsClient.synthesizeStream이 feign.Response를 반환하도록 한 설계의 전제 검증:
 * 청크(chunked) 응답이 Feign을 거치며 통째로 버퍼링되지 않고, 오는 대로 읽을 수 있어야 한다.
 * (첫 바이트가 서버의 전체 응답 완료보다 먼저 도착해야 스트리밍 지연 단축 효과가 있다)
 */
@DisplayName("Feign feign.Response 스트리밍 - 청크 응답이 버퍼링되지 않는다")
class FeignStreamingResponseTest {

    private static final long SERVER_DELAY_MS = 1200;

    interface StreamApi {
        @RequestLine("POST /stream")
        @Headers("Content-Type: application/json")
        Response stream(String body);
    }

    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stream", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "audio/mpeg");
            exchange.sendResponseHeaders(200, 0); // 길이 0 = chunked 전송
            try (OutputStream out = exchange.getResponseBody()) {
                out.write("first-".getBytes(StandardCharsets.UTF_8));
                out.flush();
                try {
                    Thread.sleep(SERVER_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                out.write("second".getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("첫 청크는 서버 응답이 끝나기 전에 도착하고, 이어서 나머지가 그대로 읽힌다")
    void firstChunkArrivesBeforeServerFinishes() throws IOException {
        StreamApi api = Feign.builder()
                .target(StreamApi.class, "http://127.0.0.1:" + server.getAddress().getPort());

        long start = System.nanoTime();
        try (Response response = api.stream("{}")) {
            InputStream in = response.body().asInputStream();
            byte[] head = in.readNBytes("first-".length());
            long firstChunkMs = (System.nanoTime() - start) / 1_000_000;

            byte[] rest = in.readAllBytes();
            long totalMs = (System.nanoTime() - start) / 1_000_000;

            assertThat(new String(head, StandardCharsets.UTF_8)).isEqualTo("first-");
            assertThat(new String(rest, StandardCharsets.UTF_8)).isEqualTo("second");
            assertThat(firstChunkMs)
                    .as("첫 청크가 서버의 지연(%dms)보다 먼저 도착해야 함 - 버퍼링되면 이 값이 지연 이상이 된다", SERVER_DELAY_MS)
                    .isLessThan(SERVER_DELAY_MS - 300);
            assertThat(totalMs).isGreaterThanOrEqualTo(SERVER_DELAY_MS - 100);
        }
    }
}
