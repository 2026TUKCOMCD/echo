package com.example.echo.memory.service;

import com.example.echo.memory.client.EmbeddingClient;
import com.example.echo.memory.config.EmbeddingProperties;
import com.example.echo.memory.dto.EmbeddingResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmbeddingServiceTest {

    @Mock
    private EmbeddingClient embeddingClient;

    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setModel("text-embedding-3-large");
        properties.setDimensions(3);
        properties.setTimeoutMs(1000);
        embeddingService = new EmbeddingService(embeddingClient, properties);
    }

    /** 실제 응답과 같은 JSON에서 만든다 - DTO에 setter가 없어 Feign도 같은 방식으로 채운다 */
    private EmbeddingResponse response() throws Exception {
        return new ObjectMapper().readValue(
                "{\"data\": [{\"index\": 0, \"embedding\": [1.0, 0.0, 0.0]}]}", EmbeddingResponse.class);
    }

    @Test
    @DisplayName("embed는 응답 헤더의 OpenAI 처리 시간을 벡터와 함께 반환한다")
    void embed_returnsProcessingMsFromHeader() throws Exception {
        // given
        when(embeddingClient.createEmbeddings(any(), any()))
                .thenReturn(ResponseEntity.ok().header("openai-processing-ms", "65").body(response()));

        // when
        Optional<EmbeddingService.QueryEmbedding> result = embeddingService.embed("질의");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().vector()).containsExactly(1f, 0f, 0f);
        assertThat(result.get().openaiProcessingMs()).isEqualTo(65);
    }

    @Test
    @DisplayName("embed는 처리 시간 헤더가 없어도 벡터를 반환하고 처리 시간은 비워 둔다")
    void embed_leavesProcessingMsNullWithoutHeader() throws Exception {
        // given
        when(embeddingClient.createEmbeddings(any(), any())).thenReturn(ResponseEntity.ok(response()));

        // when
        Optional<EmbeddingService.QueryEmbedding> result = embeddingService.embed("질의");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().vector()).containsExactly(1f, 0f, 0f);
        assertThat(result.get().openaiProcessingMs()).isNull();
    }

    @Test
    @DisplayName("embed는 호출이 실패하면 예외를 던지지 않고 빈 결과를 반환한다")
    void embed_returnsEmptyWhenClientFails() {
        // given
        when(embeddingClient.createEmbeddings(any(), any())).thenThrow(new RuntimeException("Read timed out"));

        // when
        Optional<EmbeddingService.QueryEmbedding> result = embeddingService.embed("질의");

        // then
        assertThat(result).isEmpty();
    }
}
