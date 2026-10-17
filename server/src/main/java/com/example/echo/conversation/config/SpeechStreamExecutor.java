package com.example.echo.conversation.config;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

/**
 * 스트리밍 응답 전용 스레드풀 2개.
 *
 * 파이프라인 풀({@link #executor()}): 요청 스레드가 첫 조각 오디오를 클라이언트로 보내는 동안, LLM 스트림을 끝까지 읽고
 * 나머지 조각의 TTS를 미리 연다. 한 대화 턴당 동시에 최대 2개(LLM 읽기, 나머지 TTS 열기)를 쓴다.
 * 장기기억 추출 등 백그라운드 작업(applicationTaskExecutor)과 섞이면 느린 백그라운드 작업 뒤에 줄을 서서
 * 응답이 늦어질 수 있어 분리한다.
 *
 * 실시간 턴 풀({@link #liveTurnExecutor()}): 실시간 메시지(/message-live)의 commit 뒤 처리
 * (전사 대기 → 응답 프레임 전송). 이 작업은 턴이 끝날 때까지 파이프라인 풀의 작업을 기다리므로 같은 풀에 두면 안 된다.
 * 같은 풀이면 실시간 턴이 스레드를 모두 차지했을 때 그 턴들이 기다리는 LLM 작업이 큐에서 실행되지 못해,
 * llm-timeout-seconds 동안 멈춘 뒤 전부 실패한다.
 *
 * ThreadPoolExecutor는 큐가 가득 찰 때까지 core 수를 넘겨 스레드를 늘리지 않으므로 core = max로 두고,
 * 한가할 때는 스레드가 줄어들도록 core 스레드도 유휴 시간 뒤 종료한다. 큐까지 가득 차면 요청을 거절(예외)해
 * 무한정 쌓이지 않게 한다.
 *
 * Executor 타입 빈으로 등록하지 않고 감싸 두는 이유: Executor 빈이 하나 더 생기면 Spring Boot가
 * applicationTaskExecutor 자동 구성을 건너뛰거나, 타입으로 주입받는 곳이 모호해질 수 있다.
 */
@Component
public class SpeechStreamExecutor implements DisposableBean {

    private static final int THREADS = 16;
    private static final int QUEUE_CAPACITY = 32;

    private final ThreadPoolTaskExecutor delegate;
    private final ThreadPoolTaskExecutor liveTurns;

    public SpeechStreamExecutor() {
        delegate = newPool("speech-stream-");
        liveTurns = newPool("speech-live-turn-");
    }

    private static ThreadPoolTaskExecutor newPool(String threadNamePrefix) {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(THREADS);
        pool.setMaxPoolSize(THREADS);
        pool.setAllowCoreThreadTimeOut(true);
        pool.setQueueCapacity(QUEUE_CAPACITY);
        pool.setThreadNamePrefix(threadNamePrefix);
        // 배포/재시작 시 진행 중인 턴(LLM 완료 → 히스토리 저장)을 최대한 마치고 종료
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setAwaitTerminationSeconds(30);
        pool.initialize();
        return pool;
    }

    public Executor executor() {
        return delegate;
    }

    /** 파이프라인 풀의 작업을 기다리는 실시간 턴 처리용 - {@link #executor()}와 섞지 않는다 */
    public Executor liveTurnExecutor() {
        return liveTurns;
    }

    @Override
    public void destroy() {
        // 실시간 턴이 파이프라인 작업을 기다리므로 턴 쪽부터 마무리한다
        liveTurns.shutdown();
        delegate.shutdown();
    }
}
