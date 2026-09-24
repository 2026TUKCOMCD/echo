package com.example.echo.memory.service;

import com.example.echo.memory.client.EmbeddingClient;
import com.example.echo.memory.config.EmbeddingProperties;
import com.example.echo.memory.dto.EmbeddingRequest;
import com.example.echo.memory.dto.EmbeddingResponse;
import feign.Request;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final EmbeddingClient embeddingClient;
    private final EmbeddingProperties properties;

    /**
     * 단건 임베딩
     */
    public Optional<float[]> embed(String text) {
        List<float[]> vectors = embedAll(List.of(text));
        return vectors.isEmpty() ? Optional.empty() : Optional.of(vectors.get(0));
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
        if (texts.isEmpty() || texts.stream().anyMatch(t -> t == null || t.isBlank())) {
            log.warn("임베딩 입력에 빈 문장이 있어 건너뜀 - 개수: {}", texts.size());
            return List.of();
        }
        try {
            EmbeddingResponse response = embeddingClient.createEmbeddings(EmbeddingRequest.builder()
                    .input(texts)
                    .model(properties.getModel())
                    .dimensions(properties.getDimensions())
                    .build(), new Request.Options(timeout, timeout, true));
            return toOrderedVectors(response, texts.size());
        } catch (Exception e) {
            log.warn("임베딩 실패 - 개수: {}, 원인: {}", texts.size(), e.getMessage());
            return List.of();
        }
    }

    public String modelTag() {
        return properties.modelTag();
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
}
