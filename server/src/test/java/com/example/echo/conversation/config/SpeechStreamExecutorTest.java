package com.example.echo.conversation.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SpeechStreamExecutor - 스트리밍 전용 스레드풀")
class SpeechStreamExecutorTest {

    private final SpeechStreamExecutor executor = new SpeechStreamExecutor();

    @AfterEach
    void tearDown() {
        executor.destroy();
    }

    @Test
    @DisplayName("파이프라인 풀: 동시 작업 8개가 큐에서 기다리지 않고 한꺼번에 실행된다")
    void pipelinePool_runsConcurrentTasksWithoutQueueing() throws InterruptedException {
        int tasks = 8;
        // 모두 동시에 실행 중이어야만 통과하는 장벽 - 큐에 쌓이는 작업이 있으면 시간 초과
        CyclicBarrier allRunning = new CyclicBarrier(tasks);
        AtomicInteger passed = new AtomicInteger();

        for (int i = 0; i < tasks; i++) {
            executor.executor().execute(() -> {
                try {
                    allRunning.await(3, TimeUnit.SECONDS);
                    passed.incrementAndGet();
                } catch (Exception ignored) {
                    // 시간 초과 = 동시에 실행되지 못함
                }
            });
        }

        Thread.sleep(3500);
        assertThat(passed.get()).isEqualTo(tasks);
    }

    @Test
    @DisplayName("실시간 턴이 스레드를 모두 차지해도, 턴들이 기다리는 파이프라인 작업은 실행되어 모두 끝난다")
    void liveTurnsWaitingOnPipeline_allComplete() throws InterruptedException {
        int turns = 16;
        CountDownLatch finished = new CountDownLatch(turns);

        for (int i = 0; i < turns; i++) {
            executor.liveTurnExecutor().execute(() -> {
                try {
                    // finishTurn처럼 파이프라인 풀의 작업(LlmRun 등)을 넣고 끝날 때까지 기다림
                    CompletableFuture.runAsync(() -> { }, executor.executor()).get(3, TimeUnit.SECONDS);
                    finished.countDown();
                } catch (Exception ignored) {
                    // 시간 초과 = 파이프라인 작업이 실행되지 못함
                }
            });
        }

        assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
    }
}
