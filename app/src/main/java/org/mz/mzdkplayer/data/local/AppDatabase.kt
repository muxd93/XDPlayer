package org.mz.mzdkplayer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration // 👈 记得导入
import androidx.sqlite.db.SupportSQLiteDatabase // 👈 记得导入

@Database(entities = [MediaCacheEntity::class,MediaHistoryEntity::class,AudioCacheEntity::class ], version = 5, exportSchema = false) // 👈 版本改为 4
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun mediaHistoryDao(): MediaHistoryDao // 新增
    abstract fun audioDao(): AudioDao //  3. 新增 AudioDao 访问接口
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // 👇 【定义 V1 到 V2 的迁移】 👇
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 新增的列都是 String 类型，在 Room 中对应 TEXT NOT NULL，
                // 必须提供 DEFAULT 值，否则无法将现有数据升级。
                db.execSQL("ALTER TABLE media_cache ADD COLUMN dataSourceType TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE media_cache ADD COLUMN fileName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE media_cache ADD COLUMN connectionName TEXT NOT NULL DEFAULT ''")
            }
        }

        // 👆 【定义 V1 到 V2 的迁移】 👆
        // 定义 V2 到 V3 的迁移
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `media_history` (
                        `mediaUri` TEXT NOT NULL, 
                        `fileName` TEXT NOT NULL, 
                        `playbackPosition` INTEGER NOT NULL, 
                        `mediaDuration` INTEGER NOT NULL, 
                        `protocolName` TEXT NOT NULL, 
                        `connectionName` TEXT NOT NULL, 
                        `serverAddress` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        `mediaType` TEXT NOT NULL, 
                        PRIMARY KEY(`mediaUri`)
                    )
                """.trimIndent()
                )
            }
        }
        // 定义 V3 到 V4 的迁移：为 media_cache 表添加 groupKey 列和新的索引
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. 添加新的 groupKey 列 (必须提供 DEFAULT 值)
                db.execSQL("ALTER TABLE media_cache ADD COLUMN groupKey TEXT NOT NULL DEFAULT ''")

                // 2. 添加索引 (根据 MediaCacheEntity 中定义的索引)
                // 添加 groupKey 索引 (用于加速分组查询)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_cache_groupKey` ON `media_cache` (`groupKey`)")

                // 添加 title 索引 (用于加速排序和搜索)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_cache_title` ON `media_cache` (`title`)")

                // tmdbId 索引可能已存在（或通过之前的索引添加），但再次执行确保无误
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_cache_tmdbId` ON `media_cache` (`tmdbId`)")
            }
        }
        // 定义 V4 到 V5 的迁移：创建 audio_cache 表
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建音频缓存表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `audio_cache` (
                        `audioUri` TEXT NOT NULL, 
                        `dataSourceType` TEXT NOT NULL, 
                        `fileName` TEXT NOT NULL, 
                        `connectionName` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `artist` TEXT NOT NULL, 
                        `album` TEXT NOT NULL, 
                        `duration` INTEGER NOT NULL DEFAULT 0, 
                        `localCoverPath` TEXT, 
                        `lyrics` TEXT, 
                        `dateAdded` INTEGER NOT NULL, 
                        PRIMARY KEY(`audioUri`)
                    )
                """.trimIndent())

                // 为常用查询字段添加索引（歌手、专辑）
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_cache_artist` ON `audio_cache` (`artist`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audio_cache_album` ON `audio_cache` (`album`)")
            }
        }
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mzdk_player_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5) // 添加迁移
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

}