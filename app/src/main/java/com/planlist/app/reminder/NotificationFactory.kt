package com.planlist.app.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.planlist.app.MainActivity
import com.planlist.app.R
import com.planlist.app.data.db.entity.PlanGroupEntity
import com.planlist.app.data.db.entity.PlanKind
import com.planlist.app.data.db.entity.PlanItemEntity
import java.time.LocalDate

/**
 * 通知与震动。
 *
 * 双通道震动策略：通知渠道震动可能被静音/勿扰吞掉，因此额外主动震一次。
 */
object NotificationFactory {

    const val CHANNEL_REMINDERS = "reminders"

    private const val VIBRATION_PATTERN = longArrayOf(0, 400, 200, 400)

    private const val ACTION_OFFSET_COMPLETE = 11
    private const val ACTION_OFFSET_SNOOZE = 22
    private const val ACTION_OFFSET_OPEN = 33

    /** 幂等创建通知渠道。Application.onCreate 与每次发通知前都会调用。 */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_REMINDERS) != null) return

        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.app_name) + " 提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "饮食与运动计划的到点提醒"
            enableVibration(true)
            vibrationPattern = VIBRATION_PATTERN
            enableLights(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 主动震动。
     *
     * 必须整体 try/catch：部分 ROM 在缺少权限或省电模式下会直接抛异常，
     * 而"震动失败"绝不能连累"通知显示"。
     */
    fun vibrate(context: Context) {
        try {
            val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, -1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.vibrate(effect)
            }
        } catch (t: Throwable) {
            // 故意吞掉：震动是增强体验，不是功能本身
        }
    }

    fun build(
        context: Context,
        group: PlanGroupEntity,
        items: List<PlanItemEntity>,
        date: LocalDate,
        dueText: String,
    ): Notification {
        val completeIntent = actionPendingIntent(
            context, ReminderActionReceiver.ACTION_COMPLETE, group.id, date, ACTION_OFFSET_COMPLETE,
        )
        val snoozeIntent = actionPendingIntent(
            context, ReminderActionReceiver.ACTION_SNOOZE, group.id, date, ACTION_OFFSET_SNOOZE,
        )
        val openIntent = PendingIntent.getActivity(
            context,
            requestCode(group.id, date) + ACTION_OFFSET_OPEN,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data = Uri.parse("planlist://open/group/" + group.id + "/date/" + date)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val kindLabel = if (group.kind == PlanKind.MEAL) "饮食" else "运动"
        val summary = buildString {
            append(kindLabel)
            append(" · ")
            append(dueText)
            if (items.isNotEmpty()) {
                append(" · ")
                append(items.size)
                append(" 项")
            }
        }

        val detail = items.joinToString("\n") { item ->
            if (item.amountText.isBlank()) item.name else item.name + "  " + item.amountText
        }

        return NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(group.name)
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (detail.isBlank()) summary else summary + "\n" + detail))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .setVibrate(VIBRATION_PATTERN)
            .addAction(0, "完成整组", completeIntent)
            .addAction(0, "稍后 10 分钟", snoozeIntent)
            .build()
    }

    private fun actionPendingIntent(
        context: Context,
        action: String,
        groupId: Long,
        date: LocalDate,
        offset: Int,
    ): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            data = Uri.parse("planlist://action/" + action + "/group/" + groupId + "/date/" + date)
            putExtra(ReminderActionReceiver.EXTRA_GROUP_ID, groupId)
            putExtra(ReminderActionReceiver.EXTRA_DATE, date.toString())
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(groupId, date) + offset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * 由 (groupId, date) 稳定推导的 requestCode。
     * 稳定推导是幂等排程的前提：同一个 (组, 日期) 永远映射到同一个 PendingIntent。
     */
    fun requestCode(groupId: Long, date: LocalDate): Int {
        val raw = groupId * 1_000_000L + (date.toEpochDay() % 1_000_000L)
        return (raw xor (raw ushr 32)).toInt() and 0x7FFFFFFF
    }
}
