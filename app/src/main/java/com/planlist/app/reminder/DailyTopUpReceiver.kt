package com.planlist.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.planlist.app.PlanListApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 每日补齐排程窗口。
 *
 * 这里刻意**不用 WorkManager**：WorkManager 会在合并 manifest 时带进
 * ACCESS_NETWORK_STATE 与 FOREGROUND_SERVICE 两个权限，
 * 对一个宣称"纯本地不联网"的应用来说是没必要的噪声，也白白增加依赖体积。
 *
 * 改用一次性精确闹钟自行续期：每次触发时既补满 7 天窗口，也把明天的自己排上。
 * 这样即使连续多天不打开 App，滚动窗口也不会断。
 */
class DailyTopUpReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? PlanListApp ?: return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.container.reminderScheduler.scheduleWindow()
                app.container.reminderScheduler.scheduleDailyTopUp()
            } catch (t: Throwable) {
                // 忽略：App 下次启动时还会再排一次
            } finally {
                pendingResult.finish()
            }
        }
    }
}
