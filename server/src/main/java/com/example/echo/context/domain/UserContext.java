package com.example.echo.context.domain;

import com.example.echo.common.dto.WeatherData;
import com.example.echo.health.dto.EnrichedHealthData;
import com.example.echo.user.dto.UserPreferences;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContext {
    private Long userId;
    private LocalDate date;

    @Builder.Default
    private List<ConversationTurn> conversationHistory = new ArrayList<>();

    private EnrichedHealthData enrichedHealthData;
    private UserPreferences preferences;
    private WeatherData todayWeather;

    /**
     * 캐싱된 시스템 프롬프트
     * 대화 시작 시 1회 생성되어 대화 종료까지 재사용
     */
    private String systemPrompt;

    private LocalDateTime lastAccessTime;
    private boolean isActive;
}