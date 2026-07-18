package com.example.graduation_project.data.alarm

import android.content.Context
import android.content.SharedPreferences

/**
 * 대화 알람 설정을 SharedPreferences에 저장
 *
 * - 대화 시간 (HH:mm 형식)
 */
class ConversationAlarmStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /**
     * 대화 시간 저장
     * @param time "HH:mm" 형식 또는 null (미설정)
     */
    fun saveConversationTime(time: String?) {
        prefs.edit().apply {
            if (time != null) {
                putString(KEY_CONVERSATION_TIME, time)
            } else {
                remove(KEY_CONVERSATION_TIME)
            }
            apply()
        }
    }

    /**
     * 저장된 대화 시간 조회
     * @return "HH:mm" 형식 또는 null
     */
    fun getConversationTime(): String? = prefs.getString(KEY_CONVERSATION_TIME, null)

    /**
     * 모든 설정 초기화
     */
    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREF_NAME = "conversation_alarm_prefs"
        private const val KEY_CONVERSATION_TIME = "conversation_time"
    }
}
