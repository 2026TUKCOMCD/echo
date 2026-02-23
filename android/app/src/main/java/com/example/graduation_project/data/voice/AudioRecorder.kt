package com.example.graduation_project.data.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.example.graduation_project.domain.voice.VadConfig
import com.example.graduation_project.domain.voice.VadException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * AudioRecord를 사용한 실시간 오디오 스트림 획득
 * 16kHz, 16-bit, Mono PCM 포맷
 */
class AudioRecorder(
    private val context: Context,
    private val config: VadConfig = VadConfig()
) {
    private var audioRecord: AudioRecord? = null
    private val bufferSize: Int

    init {
        val minBufferSize = AudioRecord.getMinBufferSize(
            config.sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        bufferSize = maxOf(minBufferSize, config.frameSize * 2) // 16-bit = 2 bytes per sample
    }

    /**
     * 오디오 스트림을 Flow로 반환
     * @return ShortArray Flow (16-bit PCM samples)
     * @throws VadException.PermissionDenied 권한 없음
     * @throws VadException.AudioRecordInitError 초기화 실패
     */
    fun startRecording(): Flow<ShortArray> = flow {
        // 권한 체크
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            throw VadException.PermissionDenied()
        }

        // AudioRecord 초기화
        audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                config.sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            ).also {
                if (it.state != AudioRecord.STATE_INITIALIZED) {
                    throw VadException.AudioRecordInitError()
                }
            }
        } catch (e: SecurityException) {
            throw VadException.PermissionDenied()
        } catch (e: IllegalArgumentException) {
            throw VadException.AudioRecordInitError(cause = e)
        } catch (e: Exception) {
            throw VadException.AudioRecordInitError(cause = e)
        }

        audioRecord?.startRecording()

        val buffer = ShortArray(config.frameSize)

        try {
            while (coroutineContext.isActive) {
                val readResult = audioRecord?.read(buffer, 0, config.frameSize) ?: -1

                when {
                    readResult > 0 -> emit(buffer.copyOf(readResult))
                    readResult == AudioRecord.ERROR_INVALID_OPERATION -> {
                        throw VadException.RecordingError("Invalid operation")
                    }
                    readResult == AudioRecord.ERROR_BAD_VALUE -> {
                        throw VadException.RecordingError("Bad value")
                    }
                    readResult == AudioRecord.ERROR_DEAD_OBJECT -> {
                        throw VadException.RecordingError("Audio record dead")
                    }
                }
            }
        } finally {
            stopRecording()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 녹음 중지 및 리소스 해제
     */
    fun stopRecording() {
        audioRecord?.let {
            try {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                // 리소스 해제 중 에러는 무시
            }
        }
        audioRecord = null
    }
}
