package com.planlist.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.planlist.app.PlanListApp
import com.planlist.app.data.db.entity.LogStatus
import com.planlist.app.data.db.entity.hour
import com.planlist.app.data.db.entity.minute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 闹钟到点触发。
 *
 * 关键行为：**先查库再决定要不要响**。
 * 用户可能已经提前手动完成了这一组，此时必须静默退出，
 * 否则会出现"我明明打了卡，怎么还被提醒"这种最招人烦的体验问题。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1L)
        val dateText = intent.getStringExtra(EXTRA_DATE) ?: return
        if (groupId <= 0L) return

        val app = context.applicationContext as? PlanListApp ?: return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                deliver(context.applicationContext, app, groupId, dateText)
            } catch (t: Throwable) {
                // 接收器里绝不能让异常逃逸：会导致 ANR 或进程崩溃
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun deliver(
        context: Context,
        app: PlanListApp,
        groupId: Long,
        dateText: String,
    ) {
        val date = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: return
        val container = app.container

        val group = container.planRepository.groupById(groupId) ?: return
        if (!group.enabled) return

        val items = container.planRepository.itemsOfGroup(groupId)
        if (items.isEmpty()) return

        val logs = container.logRepository.forDate(date)
        val statuses = logs.associate { it.itemId to it.status }
        val alreadyDone = items.all { statuses[it.id] == LogStatus.COMPLETED }
        if (alreadyDone) return

        NotificationFactory.ensureChannels(context)
        NotificationFactory.vibrate(context)

        if (Permissions.hasNotificationPermission(context)) {
            val dueText = String.format("%02d:%02d", group.hour, group.minute)
            val notification = NotificationFactory.build(context, group, items, date, dueText)
            runCatching {
                NotificationManagerCompat.from(context).notify(group.id.toInt(), notification)
            }
        }

        // 顺手滚动续期：每次触发都是一次免费的"补齐窗口"机会
        container.reminderScheduler.scheduleWindow()
    }

    companion object {
        const val EXTRA_GROUP_ID = "groupId"
        const val EXTRA_DATE = "date"
    }
}
