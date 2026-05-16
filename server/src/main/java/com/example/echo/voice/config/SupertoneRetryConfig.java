package com.example.echo.voice.config;

import com.example.echo.voice.exception.RetryableVoiceException;
import com.example.echo.voice.exception.SupertoneInsufficientCreditException;
import com.example.echo.voice.exception.VoiceProcessingException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Map;

/**
 * Supertone TTS 재시도 정책.
 * RetryableVoiceException(5xx)만 재시도, 402/4xx는 즉시 실패.
 * 최대 3회 시도 (초기 1회 + 재시도 2회), 500ms 고정 간격.
 */
@Configuration
public class SupertoneRetryConfig {

    @Bean
    public RetryTemplate supertoneRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        Map<Class<? extends Throwable>, Boolean> retryableExceptions = Map.of(
                RetryableVoiceException.class, true,
                SupertoneInsufficientCreditException.class, false,
                VoiceProcessingException.class, false
        );
        RetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions, true);
        retryTemplate.setRetryPolicy(retryPolicy);

        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(500L);
        retryTemplate.setBackOffPolicy(backOffPolicy);

        return retryTemplate;
    }
}
