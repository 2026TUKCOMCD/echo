package com.example.graduation_project.util

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class DeviceUtilTest {

    // ===== 삼성 기기 판별 =====

    @Test
    fun `isSamsungDevice_제조사가_samsung이면_true_반환`() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "samsung")

        val result = DeviceUtil.isSamsungDevice()

        assertTrue(result)
    }

    @Test
    fun `isSamsungDevice_제조사가_samsung이_아니면_false_반환`() {
        ReflectionHelpers.setStaticField(Build::class.java, "MANUFACTURER", "Google")

        val result = DeviceUtil.isSamsungDevice()

        assertFalse(result)
    }
}
