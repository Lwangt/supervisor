package com.planlist.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.planlist.app.core.TimeSource
import com.planlist.app.data.repo.PlanRepository
import com.planlist.app.domain.RecurrenceCalculator
import com.planlist.app.domain.dueDateTime
import java.time.LocalDate

/**
 * 闹钟排程。
 *
 * 采用「滚动窗口 + 定期补齐」：
 *  - 每次 App 启动、每个闹钟触发、每次开机/改时间、WorkManager 每 12 小时，都会把窗口补满
 *  - 这样即使系统在某次省电清理中删掉了闹钟，也会在相对短的时间内被补回来
 */
class ReminderScheduler(
    private val context: Context,
    private val planRepository: PlanRepository,
    private val time: TimeSource,
) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    /** Android 12+ 需用户授予精确闹钟权限；未授予时自动降级为宽松提醒。 */
    fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            true
        } else {
            alarmManager?.canScheduleExactAlarms() == true
        }

    /**
     * 把从今天起的 [days] 天内所有未来且未完成的提醒排进 AlarmManager。
     *
     * 幂等：重复调用只会覆盖同一批 PendingIntent，不会产生重复闹钟。
     */
    suspend fun scheduleWindow(days: Int = WINDOW_DAYS) {
        val manager = alarmManager ?: return
        val now = time.now()
        val zone = time.zone()
        val groups = planRepository.enabledGroups()
        if (groups.isEmpty()) return

        for (offset in 0 until days) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            for (group in groups) {
                if (!RecurrenceCalculator.occursOn(group, date)) continue

                val trigger = group.dueDateTime(date)
                    .minusMinutes(group.reminderOffsetMinutes.toLong())

                // 只排未来时刻：避免 App 一启动就补发一堆历史通知
                if (!trigger.isAfter(now)) continue

                val millis = trigger.atZone(zone).toInstant().toEpochMilli()
                setAlarm(manager, millis, reminderPendingIntent(groupId = group.id, date = date))
            }
        }
    }

    /** 关闭提醒总开关时调用：取消窗口内所有组的闹钟。 */
    suspend fun cancelAllReminders() {
        val manager = alarmManager ?: return
        val today = time.today()
        for (group in planRepository.allGroups()) {
            for (offset in -CANCEL_LOOKBACK_DAYS..WINDOW_DAYS) {
                val date = today.plusDays(offset.toLong())
                manager.cancel(reminderPendingIntent(group.id, date))
                manager.cancel(snoozePendingIntent(group.id, date))
            }
        }
    }

    /** 取消某个组在窗口内所有日期的提醒。 */
    suspend fun cancelGroup(groupId: Long) {
        val manager = alarmManager ?: return
        val today = time.today()
        for (offset in -CANCEL_LOOKBACK_DAYS..WINDOW_DAYS) {
            val date = today.plusDays(offset.toLong())
            manager.cancel(reminderPendingIntent(groupId, date))
            manager.cancel(snoozePendingIntent(groupId, date))
        }
    }

    /** 只取消某一天的提醒（通知里点「完成整组」时使用）。 */
    fun cancelGroupForDate(groupId: Long, date: LocalDate) {
        val manager = alarmManager ?: return
        manager.cancel(reminderPendingIntent(groupId, date))
    }

    /** 稍后提醒：排一个一次性闹钟，使用独立的请求码，不影响每日提醒。 */
    fun scheduleSnooze(groupId: Long, date: LocalDate, delayMinutes: Int) {
        val manager = alarmManager ?: return
        val triggerAt = time.millis() + delayMinutes.coerceIn(1, 120) * 60_000L
        setAlarm(manager, triggerAt, snoozePendingIntent(groupId, date))
    }

    private fun setAlarm(manager: AlarmManager, triggerAtMillis: Long, pendingIntent: PendingIntent) {
        try {
            if (canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent,
                )
            } else {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent,
                )
            }
        } catch (e: SecurityException) {
            // 精确闹钟权限在两次检查之间被撤销 —— 退化为宽松提醒，
            // 绝不因为一个异常而中断整轮排程（那会导致当天后续提醒全部丢失）
            runCatching {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent,
                )
            }
        }
    }

    private fun reminderPendingIntent(groupId: Long, date: LocalDate): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            // data 必须唯一：PendingIntent 的相等性同时比较 requestCode 与 data，
            // 只靠 requestCode 去重会让同一组不同日期的闹钟互相覆盖（经典且极难排查）
            data = Uri.parse("planlist://reminder/group/" + groupId + "/date/" + date)
            putExtra(ReminderReceiver.EXTRA_GROUP_ID, groupId)
            putExtra(ReminderReceiver.EXTRA_DATE, date.toString())
        }
        return PendingIntent.getBroadcast(
            context,
            NotificationFactory.requestCode(groupId, date),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun snoozePendingIntent(groupId: Long, date: LocalDate): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = Uri.parse("planlist://snooze/group/" + groupId + "/date/" + date)
            putExtra(ReminderReceiver.EXTRA_GROUP_ID, groupId)
            putExtra(ReminderReceiver.EXTRA_DATE, date.toString())
        }
        return PendingIntent.getBroadcast(
            context,
            NotificationFactory.requestCode(groupId, date) + SNOOZE_REQUEST_OFFSET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        /** 滚动窗口天数。7 天足够，再长会让"改规则后重排"的收益下降。 */
        const val WINDOW_DAYS = 7

        /** 取消时向前回看的范围，覆盖跨天补打卡场景。 */
        private const val CANCEL_LOOKBACK_DAYS = 2L

        private const val SNOOZE_REQUEST_OFFSET = 999_983
    }
}
