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
    }
}
