package com.example.graduation_project.presentation.permission

import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 건강 권한 요청은 Android 버전마다 받는 곳이 다르다 - 14 이상은 플랫폼 권한, 13 이하는 Health Connect 앱.
 * 일반 RequestMultiplePermissions로 요청하면 13 이하에서는 권한 화면 없이 바로 거부되므로(정의되지 않은 권한),
 * 대화 화면 권한 흐름이 쓰는 계약이 13 이하에서 Health Connect 앱의 권한 화면을 여는지 확인한다.
 */
@RunWith(RobolectricTestRunner::class)
class HealthConnectPermissionContractTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `Android 13 이하에서는 건강 권한 요청을 Health Connect 앱의 권한 화면으로 보낸다`() {
        val intent = PermissionChecker.healthConnectPermissionContract()
            .createIntent(context, PermissionChecker.getHealthConnectPermissions())

        assertEquals("androidx.health.ACTION_REQUEST_PERMISSIONS", intent.action)
        assertEquals(HEALTH_CONNECT_PACKAGE, intent.`package`)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun `Android 14 이상에서는 플랫폼 권한 요청을 쓴다`() {
        val intent = PermissionChecker.healthConnectPermissionContract()
            .createIntent(context, PermissionChecker.getHealthConnectPermissions())

        assertEquals(ActivityResultContracts.RequestMultiplePermissions.ACTION_REQUEST_PERMISSIONS, intent.action)
    }

    private companion object {
        const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
    }
}
