package com.example.echo.common.config;

import com.sun.net.httpserver.HttpServer;
import feign.Client;
import feign.Request;
import feign.Response;
import feign.hc5.ApacheHttp5Client;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Feign HTTP 클라이언트 자동 설정 검증 (시크릿/DB 없이 Feign 자동 설정만 띄움).
 *
 * PR #445는 httpclient5만 추가했는데, Spring Cloud OpenFeign의 풀링 클라이언트는
 * @ConditionalOnClass(feign.hc5.ApacheHttp5Client) 조건이라 feign-hc5가 없으면 적용되지 않았다.
 * 또 hc5 클라이언트의 기본 소켓 타임아웃은 5초라서, 클라이언트별 readTimeout(30초)이 이를 덮어쓰지
 * 않으면 5초 넘게 걸리는 LLM/TTS 호출이 실패한다 — 이 두 가지를 함께 고정한다.
 */
@DisplayName("Feign HTTP 클라이언트 - hc5 커넥션 풀링 적용")
class FeignHttpClientConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FeignAutoConfiguration.class));

    private HttpServer server;
    private final List<Integer> clientPorts = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/ok", exchange -> {
            clientPorts.add(exchange.getRemoteAddress().getPort());
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/slow", exchange -> {
            sleep(6_500);  // hc5 기본 소켓 타임아웃(5초)보다 길게
            byte[] body = "slow".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/stream", exchange -> {
            exchange.sendResponseHeaders(200, 0);  // chunked
            try (OutputStream out = exchange.getResponseBody()) {
                out.write("first-".getBytes(StandardCharsets.UTF_8));
                out.flush();
                sleep(1_200);
                out.write("second".getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static Request get(String url) {
        return Request.create(Request.HttpMethod.GET, url, Map.of(), null, StandardCharsets.UTF_8, null);
    }

    /** ElevenLabs/OpenAI/OpenRouter Feign 설정과 같은 값 (connect 10초, read 30초) */
    private static final Request.Options APP_OPTIONS =
            new Request.Options(10, TimeUnit.SECONDS, 30, TimeUnit.SECONDS, true);

    @Test
    @DisplayName("feign.Client 빈이 풀링 기반 ApacheHttp5Client로 자동 설정된다")
    void feignClientBean_isApacheHttp5Client() {
        contextRunner.run(context ->
                assertThat(context.getBean(Client.class)).isInstanceOf(ApacheHttp5Client.class));
    }

    /**
     * 기본 클라이언트(JDK HttpURLConnection)도 연속 요청에서는 keep-alive로 연결을 재사용하지만,
     * 서버가 Keep-Alive 시간을 알려주지 않으면 유휴 연결을 약 5초 뒤 버린다(같은 조건에서 포트가 바뀜을 확인함).
     * 대화 턴 사이(사용자 발화 중)에는 10초 이상 비는 경우가 많아 매 턴 새 TLS 연결을 맺게 된다.
     * hc5 풀은 유휴 5초를 넘겨도 연결을 유지한다 - 이 차이가 이번 수정의 실제 효과다.
     */
    @Test
    @DisplayName("요청 사이가 6초 비어도(대화 턴 간격) 같은 TCP 연결을 재사용한다")
    void requestsAfterIdleGap_reuseConnection() {
        contextRunner.run(context -> {
            Client client = context.getBean(Client.class);
            for (int i = 0; i < 2; i++) {
                if (i > 0) sleep(6_000);
                try (Response response = client.execute(get(url("/ok")), APP_OPTIONS)) {
                    response.body().asInputStream().readAllBytes();
                }
            }
            assertThat(clientPorts).hasSize(2);
            assertThat(clientPorts).as("새 연결을 맺으면 클라이언트 포트가 달라진다").containsOnly(clientPorts.get(0));
        });
    }

    @Test
    @DisplayName("클라이언트별 readTimeout(30초)이 hc5 기본 소켓 타임아웃(5초)을 덮어써서 6.5초 응답도 성공한다")
    void readTimeoutFromOptions_overridesHc5DefaultSocketTimeout() {
        contextRunner.run(context -> {
            Client client = context.getBean(Client.class);
            try (Response response = client.execute(get(url("/slow")), APP_OPTIONS)) {
                assertThat(response.status()).isEqualTo(200);
                assertThat(new String(response.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8))
                        .isEqualTo("slow");
            }
        });
    }

    @Test
    @DisplayName("반대로 readTimeout을 짧게 주면 그 값이 적용된다 (Options가 실제로 반영되는지 확인)")
    void shortReadTimeout_isApplied() {
        contextRunner.run(context -> {
            Client client = context.getBean(Client.class);
            Request.Options shortRead = new Request.Options(10, TimeUnit.SECONDS, 1, TimeUnit.SECONDS, true);
            assertThatThrownBy(() -> client.execute(get(url("/slow")), shortRead))
                    .isInstanceOf(SocketTimeoutException.class);
        });
    }

    @Test
    @DisplayName("청크 응답을 버퍼링하지 않아 첫 청크가 서버 응답 완료 전에 도착한다 (TTS 스트리밍 전제)")
    void chunkedResponse_isNotBuffered() {
        contextRunner.run(context -> {
            Client client = context.getBean(Client.class);
            long start = System.nanoTime();
            try (Response response = client.execute(get(url("/stream")), APP_OPTIONS)) {
                InputStream in = response.body().asInputStream();
                byte[] head = in.readNBytes("first-".length());
                long firstChunkMs = (System.nanoTime() - start) / 1_000_000;
                byte[] rest = in.readAllBytes();

                assertThat(new String(head, StandardCharsets.UTF_8)).isEqualTo("first-");
                assertThat(new String(rest, StandardCharsets.UTF_8)).isEqualTo("second");
                assertThat(firstChunkMs).isLessThan(900);
            }
        });
    }
}
