package com.planlist.app

import android.content.Context
import com.planlist.app.core.SystemTimeSource
import com.planlist.app.core.TimeSource
import com.planlist.app.data.backup.BackupRepository
import com.planlist.app.data.db.PlanListDatabase
import com.planlist.app.data.repo.LogRepository
import com.planlist.app.data.repo.PlanRepository
import com.planlist.app.data.repo.SettingsRepository
import com.planlist.app.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 手动依赖容器。
 *
 * 刻意不用 Hilt：个人自用项目引入注解处理框架只会增加编译时间与排查成本，
 * 依赖关系一共就这几个，一个手写容器更透明。
 */
class AppContainer(context: Context) {

    /** 应用级作用域：给"不关心结果的后台补排"这类 fire-and-forget 工作使用。 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val time: TimeSource = SystemTimeSource()

    val database: PlanListDatabase = PlanListDatabase.get(context)

    val planRepository = PlanRepository(database)

    val logRepository = LogRepository(database, time)

    val settingsRepository = SettingsRepository(context)

    val reminderScheduler = ReminderScheduler(context, planRepository, time)

    val backupRepository = BackupRepository(database, settingsRepository, time)
}
