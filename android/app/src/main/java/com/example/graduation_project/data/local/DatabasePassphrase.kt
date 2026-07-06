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

    /** 저장된 비밀번호를 바이트로 반환. 없으면 새로 생성해 저장 후 반환. */
    fun getOrCreate(context: Context): ByteArray {
        val p = prefs(context)
        val existing = p.getString(KEY_PASSPHRASE, null)
        if (existing != null) return existing.toByteArray(Charsets.UTF_8)

        val random = ByteArray(32)
        SecureRandom().nextBytes(random)
        val passphrase = Base64.encodeToString(random, Base64.NO_WRAP)
        p.edit().putString(KEY_PASSPHRASE, passphrase).apply()
        return passphrase.toByteArray(Charsets.UTF_8)
    }

    fun isDbEncrypted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DB_ENCRYPTED, false)

    fun markDbEncrypted(context: Context) {
        prefs(context).edit().putBoolean(KEY_DB_ENCRYPTED, true).apply()
    }
}
