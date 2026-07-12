package com.example.graduation_project.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 디버그 빌드 전용 크래시 리포터.
 *
 * ADB를 연결할 수 없는 테스트 기기에서 크래시 원인을 확인하기 위해,
 * 처리되지 않은 예외의 스택트레이스를 파일로 남기고 다음 실행 시 화면에 표시한다.
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date())
                File(appContext.filesDir, FILE_NAME).writeText(
                    "발생 시각: $time\n스레드: ${thread.name}\n\n${Log.getStackTraceString(throwable)}"
                )
            } catch (_: Exception) {
                // 크래시 기록 실패 시에도 기본 크래시 처리는 계속 진행
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    /** 이전 실행에서 저장된 크래시 내용. 없으면 null */
    fun readLastCrash(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return try {
            file.readText()
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (_: Exception) {
            // 삭제 실패는 무시 (다음 크래시 시 덮어써짐)
        }
    }
}
