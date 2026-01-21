package com.example.echo.context.service;

import com.example.echo.context.domain.UserContext;
import org.springframework.stereotype.Service;

@Service
public class ContextService {

    public UserContext initializeContext(Long userId) {
        // TODO: 구현
        return null;
    }

    public UserContext getContext(Long userId) {
        // TODO: 구현
        return null;
    }

    public void addConversationTurn(Long userId, String userMessage, String aiResponse) {
        // TODO: 구현
    }

    public void finalizeContext(Long userId) {
        // TODO: 구현
    }
}