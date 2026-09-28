package com.example.echo.conversation.config;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * 스트리밍 응답 파이프라인 전용 스레드풀.
 *
 * 요청 스레드가 첫 조각 오디오를 클라이언트로 보내는 동안, 이 풀에서 LLM 스트림을 끝까지 읽고
 * 나머지 조각의 TTS를 미리 연다. 장기기억 추출 등 백그라운드 작업(applicationTaskExecutor)과 섞이면
 * 느린 백그라운드 작업 뒤에 줄을 서서 응답이 늦어질 수 있어 분리한다.
 *
 * Executor 타입 빈으로 등록하지 않고 감싸 두는 이유: Executor 빈이 하나 더 생기면 Spring Boot가
 * applicationTaskExecutor 자동 구성을 건너뛰거나, 타입으로 주입받는 곳이 모호해질 수 있다.
 *
 * 한 대화 턴당 동시에 최대 2개(LLM 읽기, 나머지 TTS 열기)를 쓴다. 큐가 가득 차면 요청을 거절(예외)해
 * 무한정 쌓이지 않게 한다.
 */
@Component
public class SpeechStreamExecutor implements DisposableBean {

    private final ThreadPoolTaskExecutor delegate;

    public SpeechStreamExecutor() {
        delegate = new ThreadPoolTaskExecutor();
        delegate.setCorePoolSize(4);
        delegate.setMaxPoolSize(16);
        delegate.setQueueCapacity(32);
        delegate.setThreadNamePrefix("speech-stream-");
        // 배포/재시작 시 진행 중인 턴(LLM 완료 → 히스토리 저장)을 최대한 마치고 종료
        delegate.setWaitForTasksToCompleteOnShutdown(true);
        delegate.setAwaitTerminationSeconds(30);
        delegate.initialize();
    }

    public Executor executor() {
        return delegate;
    }

    @Override
    public void destroy() {
        delegate.shutdown();
    }
}
