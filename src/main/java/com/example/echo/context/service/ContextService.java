package com.example.echo.context.service;

import com.example.echo.common.client.WeatherClient;
import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.example.echo.health.service.HealthDataService;
import com.example.echo.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ContextService {

    private final ConcurrentHashMap<Long, UserContext> contextStore = new ConcurrentHashMap<>();

    private final UserService userService;
    private final HealthDataService healthDataService;
    private final WeatherClient weatherClient;

    public UserContext initializeContext(Long userId) {
        UserContext context = UserContext.builder()
                .userId(userId)
                .date(LocalDate.now())
                .conversationHistory(new ArrayList<>())
                .todayHealthData(healthDataService.getTodayHealthData(userId))
                .preferences(userService.getPreferences(userId))
                .todayWeather(weatherClient.getCurrentWeather())
                .lastAccessTime(LocalDateTime.now())
                .isActive(true)
                .build();

        contextStore.put(userId, context);
        return context;
    }

    public UserContext getContext(Long userId) {
        UserContext context = contextStore.get(userId);
        if (context == null) {
            throw new IllegalStateException("Context not found for userId: " + userId);
        }
        context.setLastAccessTime(LocalDateTime.now());
        return context;
    }

    public void addConversationTurn(Long userId, String userMessage, String aiResponse) {
        UserContext context = getContext(userId);

        ConversationTurn turn = ConversationTurn.builder()
                .userMessage(userMessage)
                .aiResponse(aiResponse)
                .timestamp(LocalDateTime.now())
                .build();

        context.getConversationHistory().add(turn);
    }

    public void finalizeContext(Long userId) {
        contextStore.remove(userId);
    }
}