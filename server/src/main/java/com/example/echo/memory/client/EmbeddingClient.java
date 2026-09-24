/*
 * OpenAI Embeddings API 클라이언트
 *
 * 사용처: EmbeddingService (장기기억 사실 문장 → 벡터)
 * - API 키는 STT와 같은 OpenAIFeignConfig 인터셉터로 부착
 */
package com.example.echo.memory.client;

import com.example.echo.memory.dto.EmbeddingRequest;
import com.example.echo.memory.dto.EmbeddingResponse;
import com.example.echo.voice.config.OpenAIFeignConfig;
import feign.Request;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "embedding-client",
        url = "${openai.api.url}",
        configuration = OpenAIFeignConfig.class
)
public interface EmbeddingClient {

    /**
     * @param options 호출별 타임아웃 - 설정 클래스의 타임아웃(STT용 30초)보다 우선한다.
     *                대화 중 호출(짧게)과 부팅 백필(JVM 첫 호출이라 느림, 길게)이 달라 호출마다 넘긴다
     */
    @PostMapping("/embeddings")
    EmbeddingResponse createEmbeddings(@RequestBody EmbeddingRequest request, Request.Options options);
}
