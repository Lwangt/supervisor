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
 * 开机 / 应用更新 / 时间或时区变更后重排所有闹钟。
 *
 * 覆盖小米的 QUICKBOOT_POWERON：国产 ROM 的快速开机不会发标准 BOOT_COMPLETED。
 */
class RescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? PlanListApp ?: return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.container.reminderScheduler.scheduleWindow()
                AlarmTopUpWorker.enqueue(context.applicationContext)
            } catch (t: Throwable) {
                // 忽略：下次 App 启动时还会再排一次
            } finally {
                pendingResult.finish()
            }
        }
    }
}
