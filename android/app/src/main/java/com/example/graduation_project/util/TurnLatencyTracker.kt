package com.example.graduation_project.util

import android.util.Log

/**
 * 한 턴의 앱 쪽 지연 구간을 모아 첫 소리가 날 때 한 번에 로그로 남긴다.
 *
 * 서버 `[지연측정]` 로그(stt, llm_first_chunk, tts_first_byte ...)는 음성 파일을 받은 뒤부터만 잰다.
 * 그 앞뒤(말 끝 → VAD 판정 → 업로드, 첫 프레임 → 재생 시작)는 앱에서만 보이므로 여기서 잰다.
 * 형식은 서버와 같게 맞춰 `adb logcat | grep 지연측정`으로 함께 볼 수 있게 한다.
 *
 * 한 턴의 흐름과 기록 지점:
 * ```
 * 실제 말 끝 ─(무음 꼬리)─ onSpeechEnded ─ onRequestStart ─ onUploadEnd ─ onFirstFrame ─ onPlaybackStart
 *   app_silence_tail       app_handoff      app_upload    app_wait_first_frame  app_player_start
 * └──────────────────────────── app_perceived_total ─────────────────────────────────────┘
 * ```
 * 녹음 스레드, OkHttp 스레드, 메인 스레드에서 호출되므로 모든 진입점을 동기화한다.
 * 측정 전용이며 앱 동작에는 영향을 주지 않는다 (오디오/텍스트 내용은 기록하지 않음).
 */
object TurnLatencyTracker {

    private const val TAG = "TurnLatency"

    /** 단조 증가 시계(ms). JVM 단위 테스트에서도 동작하도록 SystemClock 대신 nanoTime을 쓴다 */
    internal var clock: () -> Long = { System.nanoTime() / 1_000_000 }
    internal var logger: (String) -> Unit = { Log.i(TAG, it) }

    private var kind: String? = null
    private var speechEndedAt: Long? = null
    private var silenceTailMs: Long? = null
    private var audioLengthMs: Long? = null
    private var wavBytes: Int? = null
    private var requestStartedAt: Long? = null
    private var uploadEndedAt: Long? = null
    private var firstFrameAt: Long? = null

    /**
     * VAD가 발화 끝을 판정한 시점. 새 턴의 시작이므로 이전 기록을 지운다.
     * @param silenceTailMs 실제 말 끝 이후 붙어 있던 무음 길이 추정값 (0이면 추정 불가)
     */
    @Synchronized
    fun onSpeechEnded(silenceTailMs: Long, audioLengthMs: Long, wavBytes: Int) {
        reset()
        speechEndedAt = clock()
        this.silenceTailMs = silenceTailMs
        this.audioLengthMs = audioLengthMs
        this.wavBytes = wavBytes
    }

    /**
     * 서버 요청을 시작하는 시점.
     * @param kind "message"(음성 턴) 또는 "start"(첫 인사). 첫 인사는 녹음이 없으므로 이전 기록을 지운다.
     */
    @Synchronized
    fun onRequestStart(kind: String) {
        if (kind != KIND_MESSAGE) reset()
        this.kind = kind
        requestStartedAt = clock()
        uploadEndedAt = null
        firstFrameAt = null
    }

    /**
     * 요청 본문 전송 완료 (토큰 갱신으로 다시 보내면 마지막 전송 기준).
     *
     * 주의: OkHttp `requestBodyEnd`는 본문을 소켓 송신 버퍼에 다 쓴 시점이라 실제 네트워크 전송 완료가 아니다.
     * 실측(2026-09-26)에서 50~176KB WAV가 10~20ms로 찍혔다 → `app_upload`는 참고용이고,
     * 실제 업로드는 `app_wait_first_frame`에 섞여 있다. 네트워크 시간은
     * `app_wait_first_frame` − 서버 `message_first_audio`로 역산한다.
     */
    @Synchronized
    fun onUploadEnd() {
        if (requestStartedAt == null) return
        uploadEndedAt = clock()
    }

    /** 스트리밍 응답의 첫 TEXT 프레임까지 받은 시점 */
    @Synchronized
    fun onFirstFrame() {
        if (requestStartedAt == null) return
        firstFrameAt = clock()
    }

    /**
     * 실제 소리가 나기 시작한 시점. 모은 구간을 로그로 남기고 기록을 지운다.
     * 요청 기록이 없으면(tts-retry 재생 등) 아무것도 하지 않는다.
     */
    @Synchronized
    fun onPlaybackStart() {
        val requestAt = requestStartedAt ?: return
        val now = clock()
        val tag = kind ?: KIND_MESSAGE

        val speechEnd = speechEndedAt
        val silence = silenceTailMs
        if (tag == KIND_MESSAGE && speechEnd != null) {
            logger("[지연측정] stage=app_audio_length, kind=$tag, audioMs=$audioLengthMs, wavBytes=$wavBytes")
            logger(stage("app_silence_tail", tag, silence ?: 0))
            logger(stage("app_handoff", tag, requestAt - speechEnd))
        }
        uploadEndedAt?.let { logger(stage("app_upload", tag, it - requestAt)) }
        firstFrameAt?.let { first ->
            logger(stage("app_wait_first_frame", tag, first - (uploadEndedAt ?: requestAt)))
            logger(stage("app_player_start", tag, now - first))
        }
        val totalFrom = if (tag == KIND_MESSAGE && speechEnd != null) speechEnd - (silence ?: 0) else requestAt
        logger(stage("app_perceived_total", tag, now - totalFrom))

        reset()
    }

    @Synchronized
    fun reset() {
        kind = null
        speechEndedAt = null
        silenceTailMs = null
        audioLengthMs = null
        wavBytes = null
        requestStartedAt = null
        uploadEndedAt = null
        firstFrameAt = null
    }

    private fun stage(name: String, kind: String, elapsedMs: Long) =
        "[지연측정] stage=$name, kind=$kind, elapsedMs=$elapsedMs"

    const val KIND_MESSAGE = "message"
    const val KIND_START = "start"
}
