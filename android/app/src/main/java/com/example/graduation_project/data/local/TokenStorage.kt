package com.example.graduation_project.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Access/Refresh Token을 EncryptedSharedPreferences에 안전하게 저장.
 *
 * - 첫 호출 시 Keystore 초기화 비용이 큼 → ViewModel/Repository에서 IO 디스패처 호출 권장.
 * - EncryptedSharedPreferences 초기화/복호화 실패 시(업데이트, 알파 버전 버그 등)
 *   손상된 파일을 삭제하고 null을 반환하여 크래시 대신 로그아웃으로 처리.
 */
class TokenStorage(private val context: Context) {

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getAccessToken(): String? = try {
        prefs.getString(KEY_ACCESS_TOKEN, null)
    } catch (e: Exception) {
        deletePrefsFile()
        null
    }

    fun getRefreshToken(): String? = try {
        prefs.getString(KEY_REFRESH_TOKEN, null)
    } catch (e: Exception) {
        deletePrefsFile()
        null
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        try {
            prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .apply()
        } catch (e: Exception) {
            deletePrefsFile()
        }
    }

    fun clear() {
        try {
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            deletePrefsFile()
        }
    }

    /**
     * 현재 accessToken의 JWT payload(sub claim)에서 userId를 동기적으로 추출.
     * 서명 검증은 하지 않음 - 로컬 캐시 격리용 식별자 용도이며, 실제 인가는 서버가 담당.
     */
    fun getCurrentUserId(): Long? {
        val token = getAccessToken() ?: return null
        return try {
            val payload = token.split(".").getOrNull(1) ?: return null
            val padded = payload.padEnd((payload.length + 3) / 4 * 4, '=')
            val decoded = android.util.Base64.decode(padded, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
            org.json.JSONObject(String(decoded, Charsets.UTF_8)).optString("sub").toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun deletePrefsFile() {
        try {
            val file = java.io.File(
                context.filesDir.parent + "/shared_prefs/$PREF_NAME.xml"
            )
            file.delete()
        } catch (e: Exception) { /* ignore */ }
    }

    companion object {
        private const val PREF_NAME = "secure_auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}
