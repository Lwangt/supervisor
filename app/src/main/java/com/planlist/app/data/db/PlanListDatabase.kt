package com.planlist.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.planlist.app.data.db.dao.LogDao
import com.planlist.app.data.db.dao.PlanDao
import com.planlist.app.data.db.entity.ItemLogEntity
import com.planlist.app.data.db.entity.PlanGroupEntity
import com.planlist.app.data.db.entity.PlanItemEntity

/**
 * 本地数据库。
 *
 * exportSchema = true 会把 schema JSON 写到 app/schemas/（KSP 参数配置见 build.gradle.kts），
 * 供后续迁移测试使用。
 *
 * **禁止使用 fallbackToDestructiveMigration()**：打卡类应用丢历史数据是灾难。
 * 每次改表必须补 Migration + 迁移测试。
 */
@Database(
    entities = [
        PlanGroupEntity::class,
        PlanItemEntity::class,
        ItemLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PlanListDatabase : RoomDatabase() {

    abstract fun planDao(): PlanDao

    abstract fun logDao(): LogDao

    companion object {
        const val NAME = "planlist.db"

        @Volatile
        private var instance: PlanListDatabase? = null

        fun get(context: Context): PlanListDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PlanListDatabase::class.java,
                    NAME,
                ).build().also { instance = it }
            }
    }
}
