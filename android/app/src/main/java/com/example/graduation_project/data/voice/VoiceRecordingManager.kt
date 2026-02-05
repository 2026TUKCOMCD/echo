package com.example.graduation_project.data.voice

import android.content.Context
import com.example.graduation_project.domain.voice.VadConfig
import com.example.graduation_project.domain.voice.VadException
import com.example.graduation_project.domain.voice.VadListener
import com.example.graduation_project.domain.voice.VadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream

/**
 * AudioRecorder + VAD 통합 관리자
 * 음성 감지 시작/종료 이벤트를 콜백으로 전달
 * 음성 데이터는 WAV 포맷으로 변환하여 전달
 */
class VoiceRecordingManager(
    private val context: Context,
    private val config: VadConfig = VadConfig()
) {
    private val audioRecorder = AudioRecorder(context, config)
    private val vadProcessor = VadProcessor(context, config)

    private val _vadState = MutableStateFlow<VadState>(VadState.Idle)
    val vadState: StateFlow<VadState> = _vadState.asStateFlow()

    private var recordingJob: Job? = null
    private var scope: CoroutineScope? = null
    private var listener: VadListener? = null

    // 음성 데이터 버퍼 (PCM raw bytes)
    private val audioBuffer = ByteArrayOutputStream()
    private var isSpeechActive = false

    /**
     * VAD 리스너 설정
     */
    fun setVadListener(listener: VadListener) {
        this.listener = listener
    }

    /**
     * VAD 리스너 제거
     */
    fun removeVadListener() {
        this.listener = null
    }

    /**
     * 음성 감지 시작
     */
    fun start() {
        if (recordingJob?.isActive == true) return

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        scope?.launch {
            try {
                // VAD 초기화
                vadProcessor.initialize()
                _vadState.value = VadState.Listening

                // 타임아웃 설정
                withTimeoutOrNull(config.maxRecordingDurationMs) {
                    audioRecorder.startRecording()
                        .catch { e ->
                            val vadException = when (e) {
                                is VadException -> e
                                else -> VadException.UnknownError(cause = e)
                            }
                            _vadState.value = VadState.Error(vadException)
                            listener?.onError(vadException)
                        }
                        .collect { audioFrame ->
                            processAudioFrame(audioFrame)
                        }
                }

                // 타임아웃으로 종료된 경우
                if (isSpeechActive) {
                    finalizeSpeech()
                }

            } catch (e: VadException) {
                _vadState.value = VadState.Error(e)
                listener?.onError(e)
            } catch (e: Exception) {
                val vadException = VadException.UnknownError(cause = e)
                _vadState.value = VadState.Error(vadException)
                listener?.onError(vadException)
            }
        }.also { recordingJob = it }
    }

    /**
     * 음성 감지 중지
     */
    fun stop() {
        recordingJob?.cancel()
        recordingJob = null
        audioRecorder.stopRecording()
        vadProcessor.close()

        if (isSpeechActive) {
            finalizeSpeech()
        }

        audioBuffer.reset()
        isSpeechActive = false
        _vadState.value = VadState.Stopped

        scope?.cancel()
        scope = null
    }

    /**
     * 리소스 해제
     */
    fun release() {
        stop()
        listener = null
    }

    private fun processAudioFrame(audioFrame: ShortArray) {
        val isSpeech = vadProcessor.isSpeech(audioFrame)

        when {
            // 음성 시작 감지
            isSpeech && !isSpeechActive -> {
                isSpeechActive = true
                audioBuffer.reset()
                appendToBuffer(audioFrame)
                _vadState.value = VadState.SpeechDetected
                listener?.onSpeechStart()
            }
            // 음성 계속 중
            isSpeech && isSpeechActive -> {
                appendToBuffer(audioFrame)
            }
            // 음성 종료 감지
            !isSpeech && isSpeechActive -> {
                // Silero VAD의 silenceDurationMs가 충족되면 isSpeech가 false로 전환됨
                finalizeSpeech()
            }
            // 무음 상태 유지
            else -> {
                // Listening 상태 유지
            }
        }
    }

    private fun finalizeSpeech() {
        val pcmData = audioBuffer.toByteArray()
        audioBuffer.reset()
        isSpeechActive = false

        // PCM → WAV 변환
        val wavData = WavConverter.pcmToWav(
            pcmData = pcmData,
            sampleRate = config.sampleRate,
            channels = VadConfig.CHANNELS_MONO,
            bitsPerSample = VadConfig.BITS_PER_SAMPLE
        )

        _vadState.value = VadState.SpeechEnded(wavData)
        listener?.onSpeechEnd(wavData)

        // 다음 발화를 위해 Listening 상태로 복귀
        _vadState.value = VadState.Listening
    }

    private fun appendToBuffer(audioFrame: ShortArray) {
        // ShortArray를 ByteArray로 변환하여 버퍼에 추가
        val byteData = WavConverter.shortArrayToByteArray(audioFrame)
        audioBuffer.write(byteData)
    }
}
