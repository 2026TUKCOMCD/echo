package com.example.graduation_project.domain.voice

import java.io.File

/**
 * AudioRecordManager의 이벤트 콜백 인터페이스
 * VadListener보다 상위 수준으로, 파일 기반 결과를 전달
 *
 * [T2.2-4] AudioRecordManager 구현
 */
interface AudioRecordListener {

    /** 녹음 준비 완료 (Listening 상태 진입) */
    fun onReady()

    /** 음성 감지 시작 (녹음 시작) */
    fun onRecordingStart()

    /** 녹음 완료, WAV 파일 저장됨 */
    fun onRecordingComplete(audioFile: File)

    /** 에러 발생 */
    fun onError(exception: AudioRecordException)
}
