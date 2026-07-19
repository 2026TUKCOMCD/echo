package com.example.graduation_project.data.local

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/**
 * Room DB(SQLCipher) 암호화에 쓸 비밀번호를 안전하게 보관.
 *
 * - 첫 실행 시 32바이트 랜덤 값을 생성해 Base64 문자열로 저장.
 * - 저장소는 EncryptedSharedPreferences(Android Keystore 기반).
 */
object DatabasePassphrase {

    private const val PREF_NAME = "secure_db_prefs"
    private const val KEY_PASSPHRASE = "db_passphrase"
    private const val KEY_DB_ENCRYPTED = "db_encrypted"

    private fun prefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    /**
     * 저장된 비밀번호를 바이트로 반환. 없으면 새로 생성해 저장 후 반환.
     *
     * 백업/복원 등으로 암호화된 값만 남고 Keystore 키가 유실되면 복호화 시
     * AEADBadTagException이 발생한다 (Keystore 키는 백업에 포함되지 않아 발생 가능).
     * 이 경우 손상된 저장소를 초기화하고 새 비밀번호를 발급한다.
     * (기존 SQLCipher DB는 새 비밀번호로 열 수 없지만, isDbEncrypted()도 함께
     *  초기화되어 false를 반환하므로 AppDatabase.buildDatabase()가 자동으로 삭제 후 재생성한다.)
     */
    fun getOrCreate(context: Context): ByteArray {
        val existing = try {
            prefs(context).getString(KEY_PASSPHRASE, null)
        } catch (e: Exception) {
            deleteCorruptedPrefs(context)
            null
        }
        if (existing != null) return existing.toByteArray(Charsets.UTF_8)

        val random = ByteArray(32)
        SecureRandom().nextBytes(random)
        val passphrase = Base64.encodeToString(random, Base64.NO_WRAP)
        prefs(context).edit().putString(KEY_PASSPHRASE, passphrase).apply()
        return passphrase.toByteArray(Charsets.UTF_8)
    }

    fun isDbEncrypted(context: Context): Boolean = try {
        prefs(context).getBoolean(KEY_DB_ENCRYPTED, false)
    } catch (e: Exception) {
        deleteCorruptedPrefs(context)
        false
    }

    fun markDbEncrypted(context: Context) {
        prefs(context).edit().putBoolean(KEY_DB_ENCRYPTED, true).apply()
    }

    private fun deleteCorruptedPrefs(context: Context) {
        try {
            java.io.File(context.filesDir.parent + "/shared_prefs/$PREF_NAME.xml").delete()
        } catch (e: Exception) { /* ignore */ }
    }
}
