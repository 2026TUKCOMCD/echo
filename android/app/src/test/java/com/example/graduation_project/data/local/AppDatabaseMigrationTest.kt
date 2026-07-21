package com.example.graduation_project.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AppDatabase Room 마이그레이션 회귀 테스트
 *
 * Room의 androidx.room:room-testing MigrationTestHelper는 이 프로젝트의 Room 2.8.4 +
 * Robolectric 조합에서 "This driver is configured to open a database named..." 예외를
 * 던지는 신규 SQLiteDriver 경로 관련 비호환 문제가 있어 사용할 수 없었다.
 * 대신 순수 SupportSQLiteOpenHelper로 v3 스키마(app/schemas/.../3.json에 내보낸 실제
 * CREATE TABLE 문 그대로)를 직접 구성하고, 프로덕션 MIGRATION_3_4를 그대로 실행해 검증한다.
 *
 * MIGRATION_3_4(conversation_diary_link 테이블 추가)가
 * - 기존 messages/diaries 데이터를 보존하는지
 * - 신규 테이블 컬럼이 ConversationDiaryLinkEntity(컬럼명/타입/NOT NULL)와 정확히 일치하는지
 * 확인한다. 컬럼 타입/NOT NULL이 어긋나면 실기기에서 destructive fallback이 발동해
 * 기존 메시지가 통째로 삭제될 수 있으므로, 이 테스트가 그 회귀를 막는다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35]) // targetSdk(36)이 로컬 Robolectric(4.14.1, 최대 API 35 지원)보다 앞서있어 명시 고정
class AppDatabaseMigrationTest {

    /** app/schemas/.../3.json에 내보낸 실제 v3 CREATE TABLE 문 (messages/location_points/diaries) */
    private fun openV3Database(): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `messages` (`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, " +
                            "`role` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `location_points` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `timestamp` INTEGER NOT NULL, `date` TEXT NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `diaries` (`date` TEXT NOT NULL, `serverId` INTEGER NOT NULL, " +
                            "`title` TEXT, `content` TEXT, `status` TEXT NOT NULL, `failureReason` TEXT, `weather` TEXT, " +
                            "`mood` TEXT, `updatedAt` TEXT, PRIMARY KEY(`date`))"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `MIGRATION_3_4는 기존 messages diaries 데이터를 보존하고 conversation_diary_link 테이블을 정확히 만든다`() {
        // given: v3 상태의 DB를 만들고 기존 데이터를 채운다
        val db = openV3Database()
        db.execSQL(
            "INSERT INTO messages (id, conversationId, role, content, timestamp) " +
                "VALUES ('m1', 'conv-1', 'user', '안녕하세요', 1000)"
        )
        db.execSQL(
            "INSERT INTO diaries (date, serverId, title, content, status, failureReason, weather, mood, updatedAt) " +
                "VALUES ('2026-01-15', 1, '1월 15일의 일기', '오늘 하루', 'SUCCESS', NULL, '맑음', NULL, '2026-01-15T20:00:00')"
        )

        // when: 프로덕션 코드의 MIGRATION_3_4를 그대로 실행
        AppDatabase.MIGRATION_3_4.migrate(db)

        // then: 기존 messages 데이터 보존
        db.query("SELECT id, conversationId FROM messages").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("m1", cursor.getString(cursor.getColumnIndexOrThrow("id")))
            assertEquals("conv-1", cursor.getString(cursor.getColumnIndexOrThrow("conversationId")))
        }

        // then: 기존 diaries 데이터 보존
        db.query("SELECT date, status FROM diaries").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2026-01-15", cursor.getString(cursor.getColumnIndexOrThrow("date")))
            assertEquals("SUCCESS", cursor.getString(cursor.getColumnIndexOrThrow("status")))
        }

        // then: 신규 테이블 컬럼 구조가 ConversationDiaryLinkEntity와 정확히 일치하는지
        // (PRAGMA table_info로 컬럼명/타입/NOT NULL을 직접 대조 - 이게 어긋나면 destructive fallback 위험)
        val columns = mutableMapOf<String, Pair<String, Boolean>>() // name -> (type, notNull)
        db.query("PRAGMA table_info(conversation_diary_link)").use { cursor ->
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            val typeIdx = cursor.getColumnIndexOrThrow("type")
            val notNullIdx = cursor.getColumnIndexOrThrow("notnull")
            while (cursor.moveToNext()) {
                columns[cursor.getString(nameIdx)] = cursor.getString(typeIdx) to (cursor.getInt(notNullIdx) == 1)
            }
        }
        assertEquals(setOf("conversationId", "diaryDate"), columns.keys)
        assertEquals("TEXT" to true, columns["conversationId"])
        assertEquals("TEXT" to true, columns["diaryDate"])

        // then: 실제 insert/query도 정상 동작
        db.execSQL("INSERT INTO conversation_diary_link (conversationId, diaryDate) VALUES ('conv-1', '2026-01-15')")
        db.query("SELECT diaryDate FROM conversation_diary_link WHERE conversationId = 'conv-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2026-01-15", cursor.getString(0))
        }

        db.close()
    }
}
