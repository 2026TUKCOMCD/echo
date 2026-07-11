package com.example.graduation_project

import android.app.Application
import com.example.graduation_project.data.alarm.ConversationAlarmScheduler
import com.example.graduation_project.data.api.ApiClient
import com.example.graduation_project.data.local.TokenStorage

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiClient.init(TokenStorage(this))

        // 강제 종료/딥 슬립 등으로 AlarmManager 등록이 사라진 경우 대비:
        // 다음날 알람은 오늘 알람이 울려야 예약되는 체인 구조라, 앱 실행 시마다 저장된 설정으로 복구
        ConversationAlarmScheduler.rescheduleFromStorage(this)
    }
}
