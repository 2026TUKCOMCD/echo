package com.example.echo.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 스케줄링 설정
 *
 * Clock 빈은 Asia/Seoul 고정 - DB 커넥션(serverTimezone=Asia/Seoul)과 날짜 경계를 일치시키고,
 * 테스트에서 Clock.fixed(...)로 교체해 날짜 기반 로직(예: 모델 로테이션)을 검증할 수 있게 한다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Seoul"));
    }
}
