package com.example.graduation_project.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.graduation_project.data.local.dao.LocationPointDao
import com.example.graduation_project.data.local.dao.MessageDao
import com.example.graduation_project.data.local.entity.LocationPointEntity
import com.example.graduation_project.data.local.entity.MessageEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Room 데이터베이스 클래스
 *
 * ## 싱글톤 패턴
 * - 앱 전체에서 하나의 인스턴스만 사용
 * - getInstance()로 접근
 *
 * ## 버전 관리
 * - 스키마 변경 시 version 증가 필요
 * - 마이그레이션 전략 필요 (현재는 fallbackToDestructiveMigration 사용)
 */
@Database(
    entities = [MessageEntity::class, LocationPointEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao

    abstract fun locationPointDao(): LocationPointDao

    companion object {
        private const val DATABASE_NAME = "echo_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration 1 → 2: location_points 테이블 추가
         * 기존 messages 테이블은 그대로 유지
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS location_points (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        timestamp INTEGER NOT NULL,
                        date TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        /**
         * 데이터베이스 인스턴스 가져오기
         * - 스레드 안전한 싱글톤
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

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
    }
}
