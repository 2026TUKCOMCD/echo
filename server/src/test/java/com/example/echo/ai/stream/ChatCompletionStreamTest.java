package com.example.echo.ai.stream;

import com.example.echo.ai.dto.ChatCompletionResponse;
import com.example.echo.ai.exception.AIException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ChatCompletionStream - OpenRouter SSE 파싱")
class ChatCompletionStreamTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static String delta(String content) {
        return "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"" + content + "\"},\"finish_reason\":null}]}\n\n";
    }

    private ChatCompletionStream stream(String sse, AtomicBoolean closed,
                                        AtomicReference<ChatCompletionResponse.Usage> usage) {
        ByteArrayInputStream body = new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8));
        return new ChatCompletionStream(body, () -> closed.set(true), objectMapper, usage::set);
    }

    private static List<String> readAll(ChatCompletionStream stream) throws IOException {
        List<String> deltas = new ArrayList<>();
        String d;
        while ((d = stream.nextDelta()) != null) {
            deltas.add(d);
        }
        return deltas;
    }

    @Test
    @DisplayName("data 줄의 delta.content를 순서대로 돌려주고 [DONE]에서 끝난다")
    void readsDeltasUntilDone() throws IOException {
        String sse = delta("안녕") + delta("하세요") + delta("!") + "data: [DONE]\n\n";
        ChatCompletionStream stream = stream(sse, new AtomicBoolean(), new AtomicReference<>());

        assertThat(readAll(stream)).containsExactly("안녕", "하세요", "!");
        assertThat(stream.nextDelta()).as("끝난 뒤에도 null").isNull();
    }

    @Test
    @DisplayName("주석(: OPENROUTER PROCESSING), 빈 content, role만 있는 delta는 건너뛴다")
    void skipsCommentsAndEmptyDeltas() throws IOException {
        String sse = ": OPENROUTER PROCESSING\n\n"
                + "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n"
                + ": OPENROUTER PROCESSING\n\n"
                + delta("네")
                + "data: [DONE]\n\n";

        assertThat(readAll(stream(sse, new AtomicBoolean(), new AtomicReference<>()))).containsExactly("네");
    }

    @Test
    @DisplayName("마지막 usage 이벤트를 리스너로 넘긴다(캐시 적중 로그용), 모르는 필드가 있어도 된다")
    void passesUsageToListener() throws IOException {
        String sse = delta("네")
                + "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":1200,\"completion_tokens\":30,\"total_tokens\":1230,\"cost\":0.001}}\n\n"
                + "data: [DONE]\n\n";
        AtomicReference<ChatCompletionResponse.Usage> usage = new AtomicReference<>();

        readAll(stream(sse, new AtomicBoolean(), usage));

        assertThat(usage.get()).isNotNull();
        assertThat(usage.get().getPromptTokens()).isEqualTo(1200);
    }

    @Test
    @DisplayName("스트림 중간 오류 이벤트(error + finish_reason=error)는 AIException")
    void midStreamError_throws() throws IOException {
        String sse = delta("안녕")
                + "data: {\"error\":{\"code\":\"server_error\",\"message\":\"Provider disconnected\"},"
                + "\"choices\":[{\"delta\":{\"content\":\"\"},\"finish_reason\":\"error\"}]}\n\n";
        ChatCompletionStream stream = stream(sse, new AtomicBoolean(), new AtomicReference<>());

        assertThat(stream.nextDelta()).isEqualTo("안녕");
        assertThatThrownBy(stream::nextDelta)
                .isInstanceOf(AIException.class)
                .hasMessageContaining("Provider disconnected");
    }

    @Test
    @DisplayName("[DONE] 없이 연결이 끝나면 잘린 것으로 보고 AIException")
    void endsWithoutDone_throws() throws IOException {
        ChatCompletionStream stream = stream(delta("안녕"), new AtomicBoolean(), new AtomicReference<>());

        assertThat(stream.nextDelta()).isEqualTo("안녕");
        assertThatThrownBy(stream::nextDelta).isInstanceOf(AIException.class);
    }

    @Test
    @DisplayName("close()는 연결을 닫는다")
    void closeClosesConnection() {
        AtomicBoolean closed = new AtomicBoolean();
        ChatCompletionStream stream = stream("data: [DONE]\n\n", closed, new AtomicReference<>());

        stream.close();

        assertThat(closed).isTrue();
    }
}
