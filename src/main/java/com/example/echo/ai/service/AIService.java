package com.example.echo.ai.service;

import com.example.echo.ai.client.OpenAIClient;
import com.example.echo.context.domain.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AIService {

    private final OpenAIClient openAIClient;

    public String generateGreeting(String systemPrompt, UserContext context) {
        // TODO: OpenAI 호출해서 첫 인사 생성
        return null;
    }

    public String generateResponse(String conversationPrompt) {
        // TODO: OpenAI 호출해서 응답 생성
        return null;
    }
}