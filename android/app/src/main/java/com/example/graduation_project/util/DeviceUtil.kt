package com.example.graduation_project.util

import android.os.Build

/**
 * 기기 관련 유틸리티
 */
object DeviceUtil {

    /**
     * 삼성 기기인지 확인
     * 삼성 기기는 Device Care의 "앱 절전" 추가 설정이 필요함
     */
    fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    }
}
