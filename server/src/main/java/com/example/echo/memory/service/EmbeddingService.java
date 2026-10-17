package com.example.echo.memory.service;

import com.example.echo.memory.client.EmbeddingClient;
import com.example.echo.memory.config.EmbeddingProperties;
import com.example.echo.memory.dto.EmbeddingRequest;
import com.example.echo.memory.dto.EmbeddingResponse;
import feign.Request;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 문장 → 임베딩 벡터 변환 (OpenAI text-embedding-3-small)
 *
 * 실패·타임아웃 시 예외를 던지지 않고 빈 결과를 반환한다.
 * 임베딩은 기억을 "덧붙이는" 용도라, 실패해도 대화·저장 흐름을 막으면 안 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    /** OpenAI가 응답 헤더로 알려 주는 서버 처리 시간 - 느린 호출이 OpenAI 계산 때문인지 전달 구간 때문인지 가르는 근거 */
    private static final String PROCESSING_MS_HEADER = "openai-processing-ms";

    private final EmbeddingClient embeddingClient;
    private final EmbeddingProperties properties;

    /**
     * 단건 임베딩 - 대화 중 검색용이라 검색 로그에 남길 OpenAI 처리 시간을 함께 반환한다
     */
    public Optional<QueryEmbedding> embed(String text) {
        return request(List.of(text), Duration.ofMillis(properties.getTimeoutMs()))
                .map(batch -> new QueryEmbedding(batch.vectors().get(0), batch.openaiProcessingMs()));
    }

    /**
     * 배치 임베딩 - 호출 1회로 여러 문장. 타임아웃은 대화용 설정값(openai.embedding.timeout-ms)
     *
     * @return 입력과 같은 순서의 벡터 목록. 실패하면 빈 리스트 (일부만 성공한 결과는 반환하지 않음)
     */
    public List<float[]> embedAll(List<String> texts) {
        return embedAll(texts, Duration.ofMillis(properties.getTimeoutMs()));
    }

    /**
     * 타임아웃을 지정한 배치 임베딩 - 아무도 기다리지 않는 작업(부팅 백필)용
     */
    public List<float[]> embedAll(List<String> texts, Duration timeout) {
        return request(texts, timeout).map(Batch::vectors).orElse(List.of());
    }

    public String modelTag() {
        return properties.modelTag();
    }

    private Optional<Batch> request(List<String> texts, Duration timeout) {
        if (texts.isEmpty() || texts.stream().anyMatch(t -> t == null || t.isBlank())) {
            log.warn("임베딩 입력에 빈 문장이 있어 건너뜀 - 개수: {}", texts.size());
            return Optional.empty();
        }
        long start = System.currentTimeMillis();
        try {
            ResponseEntity<EmbeddingResponse> response = embeddingClient.createEmbeddings(EmbeddingRequest.builder()
                    .input(texts)
                    .model(properties.getModel())
                    .dimensions(properties.getDimensions())
                    .build(), new Request.Options(timeout, timeout, true));
            List<float[]> vectors = toOrderedVectors(response.getBody(), texts.size());
            if (vectors.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new Batch(vectors, parseProcessingMs(response.getHeaders().getFirst(PROCESSING_MS_HEADER))));
        } catch (Exception e) {
            log.warn("임베딩 실패 - 개수: {}, {}ms, 원인: {}", texts.size(), System.currentTimeMillis() - start, e.getMessage());
            return Optional.empty();
        }
    }

    private Integer parseProcessingMs(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<float[]> toOrderedVectors(EmbeddingResponse response, int expected) {
        List<EmbeddingResponse.Data> data = response == null ? null : response.getData();
        if (data == null || data.size() != expected) {
            log.warn("임베딩 응답 개수 불일치 - 요청: {}, 응답: {}", expected, data == null ? 0 : data.size());
            return List.of();
        }
        float[][] ordered = new float[expected][];
        for (EmbeddingResponse.Data d : data) {
            float[] vector = d.getEmbedding();
            if (vector == null || vector.length != properties.getDimensions()) {
                log.warn("임베딩 차원 불일치 - 기대: {}, 실제: {}", properties.getDimensions(), vector == null ? 0 : vector.length);
                return List.of();
            }
            ordered[d.getIndex()] = vector;
        }
        return List.of(ordered);
    }

    /**
     * 질의 임베딩 결과
     *
     * @param openaiProcessingMs OpenAI 서버 처리 시간(응답 헤더). 헤더가 없거나 숫자가 아니면 null
     */
    public record QueryEmbedding(float[] vector, Integer openaiProcessingMs) {
    }

    private record Batch(List<float[]> vectors, Integer openaiProcessingMs) {
    }
}
