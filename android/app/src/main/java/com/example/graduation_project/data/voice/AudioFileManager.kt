package com.example.graduation_project.data.voice

import android.content.Context
import com.example.graduation_project.domain.voice.AudioRecordException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 오디오 파일 저장/관리 유틸리티
 * cache 디렉토리를 사용하여 임시 오디오 파일 관리
 *
 * [T2.2-4] AudioRecordManager 구현
 */
class AudioFileManager(private val context: Context) {

    companion object {
        private const val AUDIO_DIR_NAME = "audio_records"
        private const val FILE_PREFIX = "echo_voice_"
        private const val FILE_EXTENSION = ".wav"
        private const val DATE_FORMAT = "yyyyMMdd_HHmmss_SSS"

        /** 보관 기간: 1시간 (임시 파일이므로 짧게) */
        private const val MAX_FILE_AGE_MS = 60 * 60 * 1000L

        /** 최대 파일 수 */
        private const val MAX_FILE_COUNT = 10
    }

    /** 오디오 파일 저장 디렉토리 */
    private val audioDir: File
        get() = File(context.cacheDir, AUDIO_DIR_NAME).also {
            if (!it.exists()) it.mkdirs()
        }

    /**
     * WAV ByteArray를 파일로 저장
     *
     * @param wavData WAV 포맷의 오디오 데이터
     * @return 저장된 File
     * @throws AudioRecordException.FileSaveError 저장 실패
     * @throws AudioRecordException.InsufficientStorageError 공간 부족
     */
    fun saveWavFile(wavData: ByteArray): File {
        val usableSpace = audioDir.usableSpace
        if (usableSpace < wavData.size * 2) {
            throw AudioRecordException.InsufficientStorageError()
        }

        val fileName = generateFileName()
        val file = File(audioDir, fileName)

        try {
            FileOutputStream(file).use { fos ->
                fos.write(wavData)
                fos.flush()
            }
        } catch (e: IOException) {
            file.delete()
            throw AudioRecordException.FileSaveError(cause = e)
        }

        return file
    }

    /**
     * 오래된 오디오 파일 정리
     * MAX_FILE_AGE_MS보다 오래된 파일 삭제
     */
    fun cleanupOldFiles() {
        val now = System.currentTimeMillis()
        audioDir.listFiles()
            ?.filter { it.name.startsWith(FILE_PREFIX) && it.extension == "wav" }
            ?.filter { now - it.lastModified() > MAX_FILE_AGE_MS }
            ?.forEach { it.delete() }
    }

    /**
     * 파일 수 초과 시 가장 오래된 파일부터 삭제
     */
    fun enforceFileLimit() {
        val files = audioDir.listFiles()
            ?.filter { it.name.startsWith(FILE_PREFIX) && it.extension == "wav" }
            ?.sortedBy { it.lastModified() }
            ?: return

        if (files.size > MAX_FILE_COUNT) {
            files.take(files.size - MAX_FILE_COUNT).forEach { it.delete() }
        }
    }

    /**
     * 특정 파일 삭제
     */
    fun deleteFile(file: File): Boolean {
        return if (file.exists() && file.parentFile?.absolutePath == audioDir.absolutePath) {
            file.delete()
        } else false
    }

    /**
     * 모든 오디오 파일 삭제
     */
    fun clearAll() {
        audioDir.listFiles()
            ?.filter { it.name.startsWith(FILE_PREFIX) }
            ?.forEach { it.delete() }
    }

    private fun generateFileName(): String {
        val dateFormat = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        return "$FILE_PREFIX$timestamp$FILE_EXTENSION"
    }
}
