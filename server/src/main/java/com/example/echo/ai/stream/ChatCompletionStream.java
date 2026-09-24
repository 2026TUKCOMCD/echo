package com.example.echo.ai.stream;

import com.example.echo.ai.dto.ChatCompletionResponse;
import com.example.echo.ai.exception.AIException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * OpenRouter Chat Completions 스트리밍 응답(SSE)을 텍스트 조각 단위로 읽는다.
 *
 * <pre>
 * data: {"choices":[{"delta":{"content":"안녕"},"finish_reason":null}]}
 * : OPENROUTER PROCESSING          ← 주석(keep-alive) - 무시
 * data: {"choices":[...],"usage":{...}}
 * data: [DONE]
 * </pre>
 *
 * 공식 문서(https://openrouter.ai/docs/api-reference/streaming) 기준으로, 응답이 시작된 뒤의 오류는 HTTP 200인 채로
 * 최상위 {@code error}와 {@code finish_reason: "error"}가 담긴 이벤트로 온다 - 이 경우 {@link AIException}을 던진다.
 *
 * {@link #close()}는 다른 스레드에서 호출해도 된다 - 읽기 중인 스레드는 IOException으로 깨어난다.
 * 연결을 끊으면 OpenAI 계열 프로바이더는 생성과 과금을 멈춘다.
 */
@Slf4j
public class ChatCompletionStream implements Closeable {

    private static final String DATA_PREFIX = "data:";
    private static final String DONE = "[DONE]";

    private final BufferedReader reader;
    private final Closeable connection;
    private final ObjectMapper objectMapper;
    private final Consumer<ChatCompletionResponse.Usage> usageListener;
    private volatile boolean finished;

    /**
     * @param body          SSE 응답 본문
     * @param connection    닫을 때 함께 닫을 연결(HTTP 응답). body와 같아도 된다.
     * @param usageListener 마지막 usage 이벤트를 받으면 호출(캐시 적중 로그용). null 가능.
     */
    public ChatCompletionStream(InputStream body, Closeable connection, ObjectMapper objectMapper,
                                Consumer<ChatCompletionResponse.Usage> usageListener) {
        this.reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
        this.connection = connection;
        this.objectMapper = objectMapper;
        this.usageListener = usageListener;
    }

    /**
     * 다음 텍스트 조각을 읽는다(도착할 때까지 대기).
     *
     * @return 텍스트 조각. 스트림이 정상적으로 끝났으면 null.
     * @throws AIException 프로바이더가 스트림 중간에 오류를 보냈거나, [DONE] 없이 연결이 끊긴 경우
     * @throws IOException 네트워크 오류(또는 다른 스레드에서 close())
     */
    public String nextDelta() throws IOException {
        if (finished) {
            return null;
        }
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.startsWith(":") || !line.startsWith(DATA_PREFIX)) {
                // 빈 줄(이벤트 구분), 주석(keep-alive), event:/id: 필드는 쓰지 않는다
                continue;
            }
            String data = line.substring(DATA_PREFIX.length()).trim();
            if (DONE.equals(data)) {
                finished = true;
                return null;
            }
            String content = parseEvent(data);
            if (content != null && !content.isEmpty()) {
                return content;
            }
        }
        finished = true;
        throw new AIException("AI 응답 스트림이 [DONE] 없이 끊겼습니다");
    }

    private String parseEvent(String data) throws IOException {
        JsonNode event = objectMapper.readTree(data);

        JsonNode error = event.get("error");
        if (error != null && !error.isNull()) {
            finished = true;
            throw new AIException("AI 응답 스트림 도중 오류 - code: " + error.path("code").asText()
                    + ", message: " + error.path("message").asText());
        }

        JsonNode usage = event.get("usage");
        if (usage != null && !usage.isNull() && usageListener != null) {
            notifyUsage(usage);
        }

        JsonNode choice = event.path("choices").path(0);
        String finishReason = choice.path("finish_reason").asText(null);
        if ("error".equals(finishReason)) {
            finished = true;
            throw new AIException("AI 응답 스트림이 오류로 종료됨 (finish_reason=error)");
        }
        if ("length".equals(finishReason)) {
            log.warn("AI 응답이 max_tokens에 걸려 잘렸습니다 (finish_reason=length)");
        }

        JsonNode content = choice.path("delta").get("content");
        return content != null && content.isTextual() ? content.asText() : null;
    }

    /** usage는 로그용 부가 정보라 형식이 달라도 응답 처리를 막지 않는다 */
    private void notifyUsage(JsonNode usage) {
        try {
            // 프로바이더마다 usage 필드가 달라(cost, prompt_tokens_details 등) 모르는 필드는 무시한다
            usageListener.accept(objectMapper.readerFor(ChatCompletionResponse.Usage.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(usage));
        } catch (Exception e) {
            log.debug("AI 응답 스트림의 usage 파싱 실패 - 무시", e);
        }
    }

    @Override
    public void close() {
        finished = true;
        try {
            connection.close();
        } catch (IOException ignored) {
            // 정리 중이므로 무시
        }
        try {
            reader.close();
        } catch (IOException ignored) {
            // 정리 중이므로 무시
        }
    }
}
