package com.example.graduation_project.data.location

import android.content.Context
import android.content.SharedPreferences

/**
 * 위치 수집 설정 저장소
 *
 * SharedPreferences를 사용하여 위치 수집 시작 시간을 저장.
 */
class LocationCollectionStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * 위치 수집 시작 시간 저장
     * @param time "HH:mm" 형식 (예: "06:00")
     */
    fun saveStartTime(time: String) {
        prefs.edit().putString(KEY_START_TIME, time).apply()
    }

    /**
     * 위치 수집 시작 시간 조회
     * @return "HH:mm" 형식, 없으면 기본값 "06:00"
     */
    fun getStartTime(): String {
        return prefs.getString(KEY_START_TIME, DEFAULT_START_TIME) ?: DEFAULT_START_TIME
    }

    /**
     * 위치 수집 시작 시간 (시)
     */
    fun getStartHour(): Int {
        val time = getStartTime()
        return try {
            time.split(":")[0].toInt()
        } catch (e: Exception) {
            DEFAULT_HOUR
        }
    }

    /**
     * 위치 수집 시작 시간 (분)
     */
    fun getStartMinute(): Int {
        val time = getStartTime()
        return try {
            time.split(":")[1].toInt()
        } catch (e: Exception) {
            DEFAULT_MINUTE
        }
    }

    /**
     * 위치 수집 활성화 여부 저장
     */
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * 위치 수집 활성화 여부 조회
     */
    fun isEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENABLED, true)
    }

    companion object {
        private const val PREFS_NAME = "location_collection_prefs"
        private const val KEY_START_TIME = "start_time"
        private const val KEY_ENABLED = "enabled"

        const val DEFAULT_START_TIME = "06:00"
        private const val DEFAULT_HOUR = 6
        private const val DEFAULT_MINUTE = 0
    }
}
