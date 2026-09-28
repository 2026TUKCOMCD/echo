package com.example.echo.conversation.service;

import com.example.echo.ai.exception.AIException;
import com.example.echo.ai.service.AIService;
import com.example.echo.ai.stream.ChatCompletionStream;
import com.example.echo.conversation.config.ConversationStreamProperties;
import com.example.echo.conversation.config.SpeechStreamExecutor;
import com.example.echo.conversation.stream.SpeechChunker;
import com.example.echo.conversation.stream.SpeechSegment;
import com.example.echo.conversation.stream.SpeechSegmentSource;
import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.voice.service.VoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * LLM 스트리밍 → 첫 조각 TTS → 나머지 TTS 파이프라인 (/message-stream, /start-stream 공통).
 *
 * <pre>
 * [풀 스레드]  LLM 스트림 읽기 ──첫 조각 확정──┐          ... LLM 완료 → 히스토리 저장 → 나머지 TTS 열기
 * [요청 스레드]                             └→ 첫 조각 TTS 열기 → 반환 → (컨트롤러가 첫 조각 오디오 전송) → 나머지 조각 대기
 * </pre>
 *
 * 히스토리 규칙 - "히스토리 = 어르신이 들은(들을) 말":
 * <ol>
 *   <li>첫 소리 전에 LLM/TTS 실패 → 예외(HTTP 오류). 히스토리에 남기지 않고 LLM 연결도 끊는다.</li>
 *   <li>첫 조각 이후 LLM 실패 → 첫 조각만 응답으로 저장하고, 나머지 조각 없이 정상 종료(END).</li>
 *   <li>나머지 조각 TTS 실패 → 히스토리는 전체로 저장된 뒤라 END 없이 끝내고 클라이언트가 tts-retry로 전체를 다시 받는다.</li>
 *   <li>클라이언트가 끊겨도 LLM은 끝까지 읽어 전체를 저장한다(나중의 tts-retry 대비). 나머지 TTS만 건너뛴다.</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpeechStreamPipeline {

    private final VoiceService voiceService;
    private final AIService aiService;
    private final SpeechStreamExecutor speechStreamExecutor;
    private final ConversationStreamProperties properties;

    /** 지연측정 stage 이름 묶음 (메시지 턴과 첫 인사의 통계가 섞이지 않도록 호출자가 정한다) */
    public record Stages(String llmFirstToken, String llmFirstChunk, String llmTotal,
                         String ttsFirstByte, String ttsRestFirstByte) {
    }

    @FunctionalInterface
    public interface StageRecorder {
        void record(String stage, long elapsedMs);
    }

    /** 요청 스레드에 돌려주는 결과 - 첫 조각 TTS가 열린 상태 */
    public record Started(String firstText, SpeechSegmentSource segments, long ttsStartedAtMs) {
    }

    /**
     * 파이프라인을 시작하고 첫 조각의 TTS 첫 바이트가 도착할 때까지 기다린다.
     *
     * @param openLlm        LLM 스트림을 여는 함수 (풀 스레드에서 호출된다)
     * @param onResponseDone 어르신이 듣게 될 AI 응답 전체가 확정되면 정확히 한 번 호출(히스토리 저장).
     *                       첫 소리 전에 실패하면 호출되지 않는다.
     * @throws RuntimeException 첫 소리 전 실패(규칙 1) - AIException, VoiceProcessingException 등
     */
    public Started start(Supplier<ChatCompletionStream> openLlm, VoiceSettings voiceSettings,
                         Stages stages, StageRecorder recorder, Consumer<String> onResponseDone) {
        LlmRun run = new LlmRun(openLlm, stages, recorder);
        try {
            speechStreamExecutor.executor().execute(run);
        } catch (RejectedExecutionException e) {
            throw new AIException("동시 응답 생성이 많아 요청을 처리할 수 없습니다", e);
        }

        String firstText = await(run.first, run);

        long ttsStart = System.currentTimeMillis();
        InputStream firstAudio;
        try {
            firstAudio = voiceService.textToSpeechStream(firstText, voiceSettings);
        } catch (RuntimeException e) {
            run.cancel();
            throw e;
        }
        recorder.record(stages.ttsFirstByte(), System.currentTimeMillis() - ttsStart);

        AtomicBoolean closed = new AtomicBoolean(false);
        CompletableFuture<SpeechSegment> rest;
        try {
            rest = run.rest.handleAsync((restText, error) -> {
                // 규칙 2: 첫 조각 이후 LLM이 실패하면 첫 조각만 들은 것으로 저장한다
                if (error != null) {
                    log.error("첫 조각 이후 AI 응답 생성 실패 - 첫 조각만 응답으로 저장하고 정상 종료합니다", error);
                    restText = "";
                }
                try {
                    onResponseDone.accept(joinSegments(firstText, restText));
                } catch (RuntimeException e) {
                    // 클라이언트가 이미 떠났으면 이 실패를 볼 곳이 없으므로 여기서 남긴다 (예: 그 사이 대화가 종료됨)
                    log.error("AI 응답 히스토리 저장 실패", e);
                    throw e;
                }

                // 규칙 4: 클라이언트가 이미 떠났으면 나머지 TTS 비용을 쓰지 않는다
                if (restText.isEmpty() || closed.get()) {
                    return null;
                }
                long restStart = System.currentTimeMillis();
                InputStream restAudio = voiceService.textToSpeechStream(restText, voiceSettings);
                recorder.record(stages.ttsRestFirstByte(), System.currentTimeMillis() - restStart);
                return new SpeechSegment(restText, restAudio);
            }, speechStreamExecutor.executor());
        } catch (RejectedExecutionException e) {
            // LLM이 이미 끝나 있어 즉시 실행하려다 거절된 경우 - 아직 아무 소리도 보내지 않았으므로 규칙 1로 처리한다
            closeQuietly(firstAudio);
            run.cancel();
            throw new AIException("동시 응답 생성이 많아 요청을 처리할 수 없습니다", e);
        }

        SpeechSegmentSource source = new TwoSegmentSource(
                new SpeechSegment(firstText, firstAudio), rest, closed, properties.getLlmTimeoutSeconds());
        return new Started(firstText, source, ttsStart);
    }

    /** 히스토리/말풍선에 쓰는 전체 응답 - 조각은 앞뒤 공백을 떼고 나뉘므로 공백 하나로 잇는다 */
    static String joinSegments(String first, String rest) {
        return rest == null || rest.isEmpty() ? first : first + " " + rest;
    }

    private String await(CompletableFuture<String> future, LlmRun run) {
        try {
            return future.get(properties.getLlmTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            run.cancel();
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new AIException("AI 응답 생성 실패: " + cause.getMessage(), cause);
        } catch (TimeoutException e) {
            run.cancel();
            throw new AIException("AI 응답 생성 시간 초과", e);
        } catch (InterruptedException e) {
            run.cancel();
            Thread.currentThread().interrupt();
            throw new AIException("AI 응답 생성 대기 중 중단됨", e);
        }
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // 정리 중이므로 무시
        }
    }

    /** 풀 스레드에서 LLM 스트림을 끝까지 읽어 첫 조각/나머지를 각각의 future로 알린다 */
    private final class LlmRun implements Runnable {

        private final Supplier<ChatCompletionStream> openLlm;
        private final Stages stages;
        private final StageRecorder recorder;
        final CompletableFuture<String> first = new CompletableFuture<>();
        /** 나머지 텍스트(없으면 ""). 첫 조각 이후 실패하면 예외로 완료된다. */
        final CompletableFuture<String> rest = new CompletableFuture<>();
        private final AtomicReference<ChatCompletionStream> stream = new AtomicReference<>();
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        LlmRun(Supplier<ChatCompletionStream> openLlm, Stages stages, StageRecorder recorder) {
            this.openLlm = openLlm;
            this.stages = stages;
            this.recorder = recorder;
        }

        @Override
        public void run() {
            long start = System.currentTimeMillis();
            try {
                ChatCompletionStream opened = openLlm.get();
                stream.set(opened);
                if (cancelled.get()) {
                    opened.close();
                    throw new AIException("AI 응답 생성이 취소되었습니다");
                }

                SpeechChunker chunker = new SpeechChunker(
                        properties.getFirstChunkMinChars(), properties.getFirstChunkMaxChars());
                boolean firstToken = false;
                String delta;
                while ((delta = opened.nextDelta()) != null) {
                    if (!firstToken) {
                        firstToken = true;
                        recorder.record(stages.llmFirstToken(), System.currentTimeMillis() - start);
                    }
                    String chunk = chunker.append(delta);
                    if (chunk != null) {
                        recorder.record(stages.llmFirstChunk(), System.currentTimeMillis() - start);
                        first.complete(aiService.sanitizeGarbledText(chunk));
                    }
                }

                String remainder = chunker.finish();
                recorder.record(stages.llmTotal(), System.currentTimeMillis() - start);
                if (!first.isDone()) {
                    // 첫 조각 기준을 채우지 못한 짧은 응답 - 전체가 하나의 조각
                    if (remainder.isEmpty()) {
                        throw new AIException("AI 응답이 비어 있습니다");
                    }
                    recorder.record(stages.llmFirstChunk(), System.currentTimeMillis() - start);
                    first.complete(aiService.sanitizeGarbledText(remainder));
                    rest.complete("");
                } else {
                    rest.complete(remainder.isEmpty() ? "" : aiService.sanitizeGarbledText(remainder));
                }
            } catch (Throwable e) {
                Throwable failure = cancelled.get() ? new AIException("AI 응답 생성이 취소되었습니다", e) : e;
                first.completeExceptionally(failure);
                rest.completeExceptionally(failure);
            } finally {
                ChatCompletionStream opened = stream.get();
                if (opened != null) {
                    opened.close();
                }
            }
        }

        /** 첫 소리 전에 실패했을 때(규칙 1) LLM 연결을 끊어 생성/과금을 멈춘다 */
        void cancel() {
            cancelled.set(true);
            ChatCompletionStream opened = stream.get();
            if (opened != null) {
                opened.close();
            }
        }
    }

    /** 첫 조각(이미 열림) + 나머지 조각(LLM 완료 후 준비됨) */
    private static final class TwoSegmentSource implements SpeechSegmentSource {

        private SpeechSegment first;
        private final CompletableFuture<SpeechSegment> rest;
        private final AtomicBoolean closed;
        private final long timeoutSeconds;
        private boolean restRequested;
        private volatile boolean restHandedOut;

        TwoSegmentSource(SpeechSegment first, CompletableFuture<SpeechSegment> rest,
                         AtomicBoolean closed, long timeoutSeconds) {
            this.first = first;
            this.rest = rest;
            this.closed = closed;
            this.timeoutSeconds = timeoutSeconds;
        }

        @Override
        public synchronized SpeechSegment next() throws IOException {
            if (first != null) {
                SpeechSegment result = first;
                first = null;
                return result;
            }
            if (restRequested) {
                return null;
            }
            restRequested = true;
            try {
                SpeechSegment result = rest.get(timeoutSeconds, TimeUnit.SECONDS);
                restHandedOut = true;
                return result;
            } catch (ExecutionException e) {
                throw new IOException("다음 응답 조각을 준비하지 못했습니다", e.getCause());
            } catch (TimeoutException e) {
                throw new IOException("다음 응답 조각 준비 시간 초과", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("다음 응답 조각 대기 중 중단됨", e);
            }
        }

        @Override
        public synchronized void close() {
            closed.set(true);
            if (first != null) {
                SpeechSegmentSource.closeQuietly(first);
                first = null;
            }
            // 아직 내주지 않은 나머지 조각은 지금 또는 나중에 준비되는 즉시 닫는다
            rest.thenAccept(segment -> {
                if (segment != null && !restHandedOut) {
                    SpeechSegmentSource.closeQuietly(segment);
                }
            });
        }
    }
}
