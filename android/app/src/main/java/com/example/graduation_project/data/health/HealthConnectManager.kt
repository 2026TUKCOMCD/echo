package com.example.graduation_project.data.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import com.example.graduation_project.domain.health.HealthConnectAvailability

/**
 * Health Connect SDK 초기화 및 가용성 확인 담당.
 * - checkAvailability()로 상태 확인
 * - NotInstalled 상태일 경우 openPlayStoreForHealthConnect()로 설치 유도
 */
class HealthConnectManager(private val context: Context) {

    companion object {
        private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
        private const val PLAY_STORE_URI = "market://details?id=$HEALTH_CONNECT_PACKAGE"
        private const val PLAY_STORE_WEB_URI =
            "https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE"

        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        )
    }

    /**
     * Health Connect SDK 가용성 확인.
     */
    fun checkAvailability(): HealthConnectAvailability {
        return when (HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PACKAGE)) {
            HealthConnectClient.SDK_AVAILABLE ->
                HealthConnectAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.NotInstalled
            else ->
                HealthConnectAvailability.NotSupported
        }
    }

    /**
     * 현재 부여된 권한이 REQUIRED_PERMISSIONS를 모두 포함하는지 확인.
     */
    suspend fun checkGrantedPermissions(): Boolean {
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(REQUIRED_PERMISSIONS)
    }

    /**
     * NotInstalled 상태일 때 Play Store로 연결.
     * Play Store 앱이 없으면 브라우저 웹 링크로 폴백.
     */
    fun openPlayStoreForHealthConnect() {
        val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(PLAY_STORE_URI)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val webIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(PLAY_STORE_WEB_URI)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolved = context.packageManager.resolveActivity(playStoreIntent, 0)
        context.startActivity(if (resolved != null) playStoreIntent else webIntent)
    }
}
