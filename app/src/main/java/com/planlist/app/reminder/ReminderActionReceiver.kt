package com.planlist.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.planlist.app.PlanListApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 通知上的按钮动作：完成整组 / 稍后提醒。
 *
 * 写入走 LogRepository 的 UPSERT，因此重复点击是幂等的。
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1L)
        val dateText = intent.getStringExtra(EXTRA_DATE) ?: return
        if (groupId <= 0L) return

        val date = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: return
        val app = context.applicationContext as? PlanListApp ?: return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = app.container
                when (action) {
                    ACTION_COMPLETE -> {
                        val items = container.planRepository.itemsOfGroup(groupId)
                        container.logRepository.completeGroup(date, groupId, items.map { it.id })
                        container.reminderScheduler.cancelGroupForDate(groupId, date)
                        dismiss(context, groupId)
                    }

                    ACTION_SNOOZE -> {
                        val snoozeMinutes = container.settingsRepository.current().snoozeMinutes
                        container.reminderScheduler.scheduleSnooze(groupId, date, snoozeMinutes)
                        dismiss(context, groupId)
                    }
                }
            } catch (t: Throwable) {
                // 动作失败也不允许崩溃；用户仍可打开 App 手动打卡
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun dismiss(context: Context, groupId: Long) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(groupId.toInt())
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.planlist.app.action.COMPLETE"
        const val ACTION_SNOOZE = "com.planlist.app.action.SNOOZE"
        const val EXTRA_GROUP_ID = "groupId"
        const val EXTRA_DATE = "date"
    }
}
