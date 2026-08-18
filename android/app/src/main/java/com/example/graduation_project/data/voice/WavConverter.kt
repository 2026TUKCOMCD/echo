package com.example.graduation_project.data.voice

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * PCM → WAV 변환 유틸리티
 * 44바이트 WAV 헤더 생성 (RIFF, fmt, data chunks)
 */
object WavConverter {

    private const val WAV_HEADER_SIZE = 44
    private const val MAX_16BIT_AMPLITUDE = 32768.0

    /**
     * PCM 데이터를 WAV 포맷으로 변환
     *
     * @param pcmData PCM 오디오 데이터 (raw bytes)
     * @param sampleRate 샘플 레이트 (Hz), 기본값 16000
     * @param channels 채널 수, 기본값 1 (Mono)
     * @param bitsPerSample 샘플당 비트 수, 기본값 16
     * @return WAV 포맷의 ByteArray (헤더 + 데이터)
     */
    fun pcmToWav(
        pcmData: ByteArray,
        sampleRate: Int = 16000,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        val outputStream = ByteArrayOutputStream()

        // WAV 헤더 작성
        outputStream.write(createWavHeader(
            pcmDataSize = pcmData.size,
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            byteRate = byteRate,
            blockAlign = blockAlign
        ))

        // PCM 데이터 작성
        outputStream.write(pcmData)

        return outputStream.toByteArray()
    }

    /**
     * ShortArray PCM 데이터를 WAV 포맷으로 변환
     *
     * @param pcmData PCM 오디오 데이터 (16-bit samples)
     * @param sampleRate 샘플 레이트 (Hz), 기본값 16000
     * @param channels 채널 수, 기본값 1 (Mono)
     * @return WAV 포맷의 ByteArray (헤더 + 데이터)
     */
    fun pcmToWav(
        pcmData: ShortArray,
        sampleRate: Int = 16000,
        channels: Int = 1
    ): ByteArray {
        val byteData = shortArrayToByteArray(pcmData)
        return pcmToWav(byteData, sampleRate, channels, 16)
    }

    /**
     * 발화 종료 후 뒤에 붙은 무음 꼬리를 잘라낸다.
     *
     * Silero VAD의 hangover(silenceDurationMs)로 인해 실제 발화가 끝난 뒤에도
     * 무음 구간이 그대로 버퍼에 쌓이는데, 이렇게 정보량이 적은 오디오를 그대로
     * STT에 넘기면 환각(hallucination) 응답을 유발하기 쉽다.
     * VAD의 isSpeech() 값은 이미 hangover로 스무딩되어 있어 트리밍에 쓸 수 없으므로,
     * 원본 PCM에서 프레임 단위로 에너지(dBFS)를 다시 계산해 뒤쪽 무음만 잘라낸다.
     *
     * @param pcmData 16-bit PCM raw bytes (little endian)
     * @param sampleRate 샘플 레이트 (Hz)
     * @param frameSizeBytes 프레임 크기 (bytes) - VAD 프레임 크기와 동일하게 맞춤
     * @param thresholdDbfs 이 값 미만이면 무음으로 판정 (dBFS)
     * @param paddingMs 마지막 유효 프레임 뒤에 남겨둘 여유 시간 (어미 보존용)
     * @return 트리밍된 PCM 데이터. 전체가 임계값 미만이면(엣지 케이스) 원본을 그대로 반환한다.
     */
    fun trimTrailingSilence(
        pcmData: ByteArray,
        sampleRate: Int,
        frameSizeBytes: Int,
        thresholdDbfs: Double,
        paddingMs: Int
    ): ByteArray {
        if (frameSizeBytes <= 0 || pcmData.size < frameSizeBytes) {
            return pcmData
        }

        val frameCount = pcmData.size / frameSizeBytes
        var lastLoudFrame = -1
        for (i in frameCount - 1 downTo 0) {
            val start = i * frameSizeBytes
            if (frameDbfs(pcmData, start, start + frameSizeBytes) >= thresholdDbfs) {
                lastLoudFrame = i
                break
            }
        }

        // 전체가 임계값 미만이면 과도하게 잘라내지 않고 원본 유지
        if (lastLoudFrame == -1) {
            return pcmData
        }

        val samplesPerFrame = frameSizeBytes / 2
        val frameDurationMs = samplesPerFrame.toDouble() / sampleRate * 1000
        val paddingFrames = ceil(paddingMs / frameDurationMs).toInt()

        val keepFrames = (lastLoudFrame + 1 + paddingFrames).coerceAtMost(frameCount)
        val keepBytes = keepFrames * frameSizeBytes
        return if (keepBytes >= pcmData.size) pcmData else pcmData.copyOf(keepBytes)
    }

    /**
     * 16-bit PCM 프레임 구간의 RMS 에너지를 dBFS로 계산
     */
    private fun frameDbfs(data: ByteArray, start: Int, end: Int): Double {
        var sumOfSquares = 0.0
        var sampleCount = 0
        var i = start
        while (i + 1 < end) {
            val sample = ((data[i + 1].toInt() shl 8) or (data[i].toInt() and 0xFF)).toShort()
            sumOfSquares += sample.toDouble() * sample.toDouble()
            sampleCount++
            i += 2
        }
        if (sampleCount == 0) return Double.NEGATIVE_INFINITY
        val rms = sqrt(sumOfSquares / sampleCount)
        if (rms <= 0.0) return Double.NEGATIVE_INFINITY
        return 20.0 * log10(rms / MAX_16BIT_AMPLITUDE)
    }

    /**
     * ShortArray를 Little Endian ByteArray로 변환
     */
    fun shortArrayToByteArray(shortArray: ShortArray): ByteArray {
        val byteBuffer = ByteBuffer.allocate(shortArray.size * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
        shortArray.forEach { byteBuffer.putShort(it) }
        return byteBuffer.array()
    }

    private fun createWavHeader(
        pcmDataSize: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        byteRate: Int,
        blockAlign: Int
    ): ByteArray {
        val totalSize = pcmDataSize + WAV_HEADER_SIZE - 8 // 전체 파일 크기 - 8 (RIFF + size)

        return ByteBuffer.allocate(WAV_HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN)
            // RIFF chunk
            .put("RIFF".toByteArray())           // ChunkID
            .putInt(totalSize)                    // ChunkSize
            .put("WAVE".toByteArray())           // Format
            // fmt sub-chunk
            .put("fmt ".toByteArray())           // Subchunk1ID
            .putInt(16)                          // Subchunk1Size (PCM = 16)
            .putShort(1)                         // AudioFormat (PCM = 1)
            .putShort(channels.toShort())        // NumChannels
            .putInt(sampleRate)                  // SampleRate
            .putInt(byteRate)                    // ByteRate
            .putShort(blockAlign.toShort())      // BlockAlign
            .putShort(bitsPerSample.toShort())   // BitsPerSample
            // data sub-chunk
            .put("data".toByteArray())           // Subchunk2ID
            .putInt(pcmDataSize)                 // Subchunk2Size
            .array()
    }
}
