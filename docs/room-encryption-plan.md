# Room DB 암호화 구현 계획서 (B7 대응)

> 이 문서는 haiku 모델(또는 낮은 추론 능력의 에이전트)이 **순서대로 실행**할 수 있도록,
> 각 단계를 "대상 파일 → 정확한 코드 → 검증 방법"으로 구체화한 구현 가이드입니다.
> **목표**: Room DB(`echo_database`)의 `messages`, `location_points` 테이블을 SQLCipher로 암호화.

## 배경 (반드시 이해하고 시작)

- Room 자체에는 암호화 기능이 **없습니다.** 표준 방법은 **SQLCipher** 라이브러리를 붙이는 것입니다.
- 구조: **SQLCipher**가 DB 파일을 암호화하고, DB를 여는 **비밀번호(passphrase)** 는 **EncryptedSharedPreferences**(Android Keystore 기반)에 저장합니다.
  - 이 앱은 이미 `TokenStorage.kt`에서 EncryptedSharedPreferences를 토큰 저장에 쓰고 있습니다. 같은 방식을 DB 비밀번호에도 적용합니다.
- **`androidx.security:security-crypto`** 는 `EncryptedSharedPreferences`만 제공하며, DB 암호화 기능은 없습니다. (혼동 주의)
- 라이브러리: **`net.zetetic:sqlcipher-android`** (최신 아티팩트). 패키지는 `net.zetetic.database.sqlcipher`.

## 결정된 사항 (이미 확정됨, haiku는 그대로 따름)

1. **기존 데이터**: 평문→암호화 마이그레이션은 **하지 않는다.** 암호화 DB는 기존 평문 파일을 열 수 없으므로, 최초 암호화 실행 시 **기존 평문 DB를 삭제하고 빈 암호화 DB로 새로 시작**한다.
   - 근거: 아직 배포 전이고, 배포 후에는 모든 사용자가 처음부터 암호화 DB로 설치되므로 전환이 필요 없다. (영향받는 건 현재 QA 기기뿐)
   - 이 방식은 위험한 마이그레이션 코드가 없어 빌드/실행이 안전하다.
2. **라이브러리**: `net.zetetic:sqlcipher-android:4.6.1`.

## 안전 원칙 (haiku 필독)

- 각 단계는 **번호 순서대로** 실행한다.
- 코드는 이 문서에 적힌 그대로 입력한다. 임의로 API 이름을 바꾸지 않는다.
- **STEP 5(빌드 검증)에서 실패하면 멈추고 오류 메시지를 보고한다.** 스스로 API를 추측해 고치지 않는다.
- 기존 파일을 수정할 때는 반드시 먼저 Read로 현재 내용을 확인한 뒤 Edit한다.

---

## STEP 1. 버전 카탈로그에 라이브러리 추가

**파일**: `android/gradle/libs.versions.toml`

### 1-1. `[versions]` 섹션에 추가 (기존 `securityCrypto = ...` 줄 아래)

```toml
sqlcipher = "4.6.1"
```

### 1-2. `[libraries]` 섹션에 추가 (기존 `androidx-security-crypto = ...` 줄 아래)

```toml
# SQLCipher (Room DB 암호화)
sqlcipher-android = { group = "net.zetetic", name = "sqlcipher-android", version.ref = "sqlcipher" }
```

> 참고: `sqlcipher-android`가 `androidx.sqlite`를 전이 의존성으로 가져오고 Room도 같은 라이브러리를 쓰므로, `androidx.sqlite`를 **따로 추가하지 않는다.** (버전 충돌 방지)

---

## STEP 2. 모듈 build.gradle에 의존성 추가

**파일**: `android/app/build.gradle.kts`

`dependencies { ... }` 블록 안, 기존 `// Security (EncryptedSharedPreferences)` 항목 근처에 추가:

```kotlin
    // SQLCipher (Room DB 암호화)
    implementation(libs.sqlcipher.android)
```

> 참고: `androidx.security.crypto`(EncryptedSharedPreferences)는 이미 있으므로 그대로 둔다.

---

## STEP 3. DB 비밀번호 관리 클래스 생성

**새 파일 생성**: `android/app/src/main/java/com/example/graduation_project/data/local/DatabasePassphrase.kt`

역할:
- 첫 실행 시 랜덤 비밀번호를 만들어 EncryptedSharedPreferences에 저장하고, 이후엔 저장된 값을 돌려준다.
- "암호화가 적용됨" 플래그를 관리한다(기존 평문 DB를 최초 1회만 삭제하기 위함).

```kotlin
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
```

---

## STEP 4. AppDatabase가 암호화 팩토리를 쓰도록 수정

**파일**: `android/app/src/main/java/com/example/graduation_project/data/local/AppDatabase.kt`

### 4-1. import 추가 (파일 상단 import 블록에)

```kotlin
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
```

### 4-2. `buildDatabase` 함수 교체

**교체 전 (현재 코드):**

```kotlin
        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2)
                // Migration 실패 시에만 fallback (안전망)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
```

**교체 후:**

```kotlin
        private fun buildDatabase(context: Context): AppDatabase {
            val appContext = context.applicationContext

            // 1) DB 비밀번호 준비
            val passphrase = DatabasePassphrase.getOrCreate(appContext)

            // 2) 최초 암호화 적용 시: 기존 평문 DB가 있으면 삭제.
            //    (암호화 DB는 평문 파일을 열 수 없어 크래시가 나므로, 최초 1회만 지운다.)
            if (!DatabasePassphrase.isDbEncrypted(appContext)) {
                appContext.deleteDatabase(DATABASE_NAME) // 없으면 아무 일도 안 함
                DatabasePassphrase.markDbEncrypted(appContext)
            }

            // 3) SQLCipher 네이티브 로드 후 암호화 팩토리로 Room 빌드
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(passphrase)

            return Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2)
                // Migration 실패 시에만 fallback (안전망)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
```

> **주의**: DAO, Repository, ViewModel, UI 코드는 **전혀 수정하지 않는다.** 암복호화는 SQLCipher가 투명하게 처리하므로 나머지 코드는 그대로 동작한다.

---

## STEP 5. 빌드 검증

터미널에서 실행:

```bash
cd android
./gradlew assembleDebug
```

- **성공하면** STEP 6으로 진행.
- **실패하면** 멈추고 오류 메시지 전체를 보고한다. (특히 `SupportOpenHelperFactory`, `net.zetetic` 관련 오류면 라이브러리 버전/패키지명 문제일 수 있으니 임의로 고치지 말고 사람에게 보고.)

---

## STEP 6. 동작 검증 (수동)

앱을 실기기/에뮬레이터에 설치 후 확인:

1. **신규 설치**: 앱 삭제 후 재설치 → 대화 진행 → 앱 재시작 → 대화 기록이 보이면 정상. (암호화 DB가 정상적으로 저장·복호화됨)
2. **기존 평문 설치 위에 업데이트**: 암호화 적용 전 버전을 쓰던 기기에 이 버전을 덮어 설치 → **크래시 없이** 실행되면 정상. (기존 대화는 삭제되고 빈 상태로 시작됨 — 의도된 동작)
3. **암호화 확인(선택)**: 루팅/디버그 환경에서 DB 파일을 꺼내 일반 SQLite 뷰어로 평문 열람이 **안 되면** 암호화 성공.

DB 파일 경로:
```
/data/data/com.example.graduation_project/databases/echo_database
```

---

## 검증 체크리스트 (완료 시 표시)

- [ ] STEP 1: libs.versions.toml에 sqlcipher 추가
- [ ] STEP 2: build.gradle.kts에 의존성 1줄 추가
- [ ] STEP 3: DatabasePassphrase.kt 생성
- [ ] STEP 4: AppDatabase.kt의 import + buildDatabase 교체
- [ ] STEP 5: `./gradlew assembleDebug` 성공
- [ ] STEP 6: 신규 설치 / 기존 설치 덮어쓰기 동작 확인

---

## 롤백 방법 (문제 발생 시)

1. STEP 4의 `buildDatabase`를 원래대로 되돌린다(`openHelperFactory`, `System.loadLibrary`, 비밀번호/삭제 로직 제거).
2. STEP 1·2의 의존성 추가를 제거한다.
3. STEP 3에서 만든 `DatabasePassphrase.kt`를 삭제한다.
4. 앱을 삭제 후 재설치하면 평문 DB로 복귀한다.

---

## 참고: 이 방식이 앱 기능에 영향을 주지 않는 이유

SQLCipher는 **디스크에 저장할 때 암호화, 읽을 때 복호화**를 드라이버 계층에서 자동 처리한다(투명 암호화). 따라서 상위 계층(DAO 쿼리, Repository, ViewModel, UI)은 평문 데이터를 다루던 그대로 동작하며, 코드 변경이 필요 없다. 성능 영향은 하드웨어 AES 가속으로 무시할 수 있는 수준이다.
