/*
 * OpenRouter Chat Completion API 클라이언트
 *
 * OpenFeign을 사용한 선언적 HTTP 클라이언트
 * - OpenRouter는 OpenAI 호환 API를 제공하므로 기존 DTO를 그대로 사용
 * - OpenRouterFeignConfig에서 API 키를 Authorization 헤더에 자동 부착
 *
 * 사용처: AIService에서 AI 응답 생성 시 호출
 */
package com.example.echo.ai.client;

import com.example.echo.ai.config.OpenRouterFeignConfig;
import com.example.echo.ai.dto.ChatCompletionRequest;
import com.example.echo.ai.dto.ChatCompletionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "openrouter-chat-client",
        url = "${openrouter.api.url}",
        configuration = OpenRouterFeignConfig.class
)
public interface OpenRouterClient {

    /**
     * OpenRouter Chat Completion API 호출
     *
     * @param request 모델, 메시지, temperature 등 요청 파라미터
     * @return AI 생성 응답 (choices[0].message.content에 텍스트)
     */
    @PostMapping("/chat/completions")
    ChatCompletionResponse createChatCompletion(@RequestBody ChatCompletionRequest request);
}
