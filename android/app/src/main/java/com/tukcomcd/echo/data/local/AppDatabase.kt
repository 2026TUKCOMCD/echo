package com.tukcomcd.echo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.tukcomcd.echo.data.local.dao.MessageDao
import com.tukcomcd.echo.data.local.entity.MessageEntity

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
    entities = [MessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao

    companion object {
        private const val DATABASE_NAME = "echo_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

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
                // 스키마 변경 시 데이터 삭제 후 재생성 (개발 단계용)
                // TODO: 프로덕션에서는 마이그레이션 전략 구현 필요
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
