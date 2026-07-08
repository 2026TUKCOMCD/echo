/*
 * OpenRouter 채팅 모델 매일 로테이션
 *
 * 선택 방식: 날짜 기반 stateless 선택 (LocalDate.toEpochDay() % 후보 개수)
 * - 인메모리 인덱스를 두지 않음 -> 서버가 낮에 재시작돼도 같은 날엔 항상 같은 모델
 * - @Scheduled 잡은 자정에 현재 모델을 로그로 남기는 관찰용 역할이며,
 *   실제 선택은 매 요청마다 재계산되므로 스케줄러가 못 돌아도 정확성에 영향 없음
 */
package com.example.echo.ai.service;

import com.example.echo.ai.config.OpenRouterChatProperties;
import com.example.echo.ai.exception.AIException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelRotationService {

    private final OpenRouterChatProperties chatProperties;
    private final Clock clock;

    /**
     * 오늘 사용할 모델을 반환한다.
     */
    public String currentModel() {
        List<String> models = chatProperties.getModels();
        if (models == null || models.isEmpty()) {
            throw new AIException("openrouter.chat.models 설정이 비어 있습니다.");
        }

        long day = LocalDate.now(clock).toEpochDay();
        int index = Math.floorMod(day, models.size());
        return models.get(index);
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void logDailyRotation() {
        log.info("OpenRouter 오늘의 채팅 모델: {}", currentModel());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logOnStartup() {
        log.info("OpenRouter 오늘의 채팅 모델 (시작 시): {}", currentModel());
    }
}
