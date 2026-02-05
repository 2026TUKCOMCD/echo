package com.example.graduation_project.data.voice

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PCM → WAV 변환 유틸리티
 * 44바이트 WAV 헤더 생성 (RIFF, fmt, data chunks)
 */
object WavConverter {

    private const val WAV_HEADER_SIZE = 44

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
