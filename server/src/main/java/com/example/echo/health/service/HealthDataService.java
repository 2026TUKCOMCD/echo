package com.example.echo.health.service;

import com.example.echo.health.dto.HealthData;
import org.springframework.stereotype.Service;

@Service
public class HealthDataService {

    public HealthData getTodayHealthData(Long userId) {
        // TODO: 실제 DB 연동 시 Repository 사용
        return HealthData.builder()
                .steps(4200)
                .sleepDurationMinutes(390)  // 6시간 30분
                .exerciseDistanceKm(1.8)
                .exerciseActivity("아침 산책")
                .build();
    }
}