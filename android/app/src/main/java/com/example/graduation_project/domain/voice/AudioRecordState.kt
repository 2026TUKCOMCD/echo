package com.example.graduation_project.domain.voice

import java.io.File

/**
 * AudioRecordManager의 녹음 상태
 * VadState보다 상위 수준으로, 파일 저장까지 포함한 전체 워크플로우 상태
 *
 * 상태 흐름: Idle → Preparing → Listening → Recording → Processing → Completed / Error
 *
 * [T2.2-4] AudioRecordManager 구현
 */
sealed class AudioRecordState {

    /** 초기 상태 - 녹음 비활성 */
    data object Idle : AudioRecordState()

    /** 준비 중 - VAD 초기화, 마이크 준비 */
    data object Preparing : AudioRecordState()

    /** 대기 중 - 음성 입력 대기 (VAD 리스닝) */
    data object Listening : AudioRecordState()

    /** 녹음 중 - 음성 감지되어 녹음 진행 */
    data object Recording : AudioRecordState()

    /** 처리 중 - 녹음 완료, WAV 파일 저장 중 */
    data object Processing : AudioRecordState()

    /** 완료 - WAV 파일 저장 완료 */
    data class Completed(val audioFile: File) : AudioRecordState()

    /** 에러 발생 */
    data class Error(val exception: AudioRecordException) : AudioRecordState()
}
