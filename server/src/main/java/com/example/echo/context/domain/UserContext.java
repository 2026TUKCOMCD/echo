package com.example.echo.context.domain;

import com.example.echo.common.dto.WeatherData;
import com.example.echo.health.dto.HealthData;
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

    private HealthData todayHealthData;
    private UserPreferences preferences;
    private WeatherData todayWeather;
    private LocalDateTime lastAccessTime;
    private boolean isActive;
}