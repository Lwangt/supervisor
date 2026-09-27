package com.planlist.app

import android.app.Application
import com.planlist.app.reminder.AlarmTopUpWorker
import com.planlist.app.reminder.NotificationFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PlanListApp : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        NotificationFactory.ensureChannels(this)

        // 启动即补齐闹钟窗口：这是"系统某次省电清理删掉了闹钟"后最重要的恢复路径
        applicationScope.launch {
            runCatching { container.reminderScheduler.scheduleWindow() }
                .onFailure { /* 首次启动无计划时会走到这里，属正常 */ }
        }
        AlarmTopUpWorker.enqueue(this)
    }
}
