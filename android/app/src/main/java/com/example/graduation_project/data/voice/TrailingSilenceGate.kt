package com.example.graduation_project.data.voice

import kotlin.math.ceil

/**
 * 실시간 전송용 무음 꼬리 제거기 - [WavConverter.trimTrailingSilence]의 스트리밍 버전.
 *
 * 발화 끝의 무음(VAD hangover, 최대 silenceDurationMs)을 STT에 보내지 않기 위해, 조용한 프레임은 바로 내보내지 않고
 * 들고 있다가:
 * - 다시 큰 소리가 오면 들고 있던 것부터 순서대로 내보낸다 (문장 중간에 쉰 부분은 그대로 살림)
 * - 발화가 끝나면([finish]) 들고 있던 것 중 여유(paddingMs)만 내보내고 나머지는 버린다
 * - 발화 전체가 조용했으면 자르지 않고 전부 내보낸다 (작게 말하는 경우 - trimTrailingSilence와 같은 예외)
 *
 * 그래서 내보낸 바이트를 모두 이으면 같은 발화 PCM에 trimTrailingSilence를 적용한 결과와 같다.
 * 조용한 구간은 원래 말 끝 판정(무음 대기) 동안 기다리던 시간이라 추가 지연은 없다.
 *
 * 한 번에 VAD 프레임 하나씩 넣어야 한다(판정 단위가 프레임). 한 발화에만 쓰고 버린다.
 */
class TrailingSilenceGate(
    sampleRate: Int,
    frameSizeBytes: Int,
    private val thresholdDbfs: Double,
    paddingMs: Int,
    private val emit: (ByteArray) -> Unit
) {
    private val paddingFrames: Int = run {
        val frameDurationMs = (frameSizeBytes / 2).toDouble() / sampleRate * 1000
        ceil(paddingMs / frameDurationMs).toInt()
    }
    private val held = ArrayList<ByteArray>()
    private var sawLoud = false
    private var finished = false

    fun offer(frame: ByteArray) {
        if (finished) return
        if (WavConverter.frameDbfs(frame, 0, frame.size) >= thresholdDbfs) {
            flushHeld(held.size)
            emit(frame)
            sawLoud = true
        } else {
            held.add(frame)
        }
    }

    /** 발화 끝 - 남은 무음 중 여유만 내보낸다 */
    fun finish() {
        if (finished) return
        finished = true
        flushHeld(if (sawLoud) minOf(paddingFrames, held.size) else held.size)
        held.clear()
    }

    private fun flushHeld(count: Int) {
        for (i in 0 until count) emit(held[i])
        held.clear()
    }
}
