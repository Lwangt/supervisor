package com.planlist.app

import android.app.Application
import com.planlist.app.reminder.NotificationFactory

/**
 * 应用入口。
 *
 * **这里刻意只做最轻的初始化，不做任何排程工作。**
 *
 * 原因：`Application.onCreate` 不只在用户打开 App 时执行，
 * 在**进程被闹钟广播冷启动**时同样会执行。如果这里顺手跑一遍完整的 7 天窗口重排
 * （读数据库 + 几十次 setExact），真正的紧急任务 —— 把通知弹出来 ——
 * 就被排在了一堆与本次提醒无关的工作后面。冷启动路径上每一毫秒都该给通知。
 *
 * 排程改由这些真正该负责的时机来做（见 ReminderScheduler.scheduleWindow 的调用点）：
 *  - MainActivity.onStart          用户真正打开 App
 *  - RescheduleReceiver            开机 / 应用更新 / 改时间 / 改时区 / 获得精确闹钟权限
 *  - DailyTopUpReceiver            每日 00:05 自续期补齐
 *  - ReminderReceiver              每次提醒触发后顺手续期
 */
class PlanListApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // 只是幂等建通知渠道，很快；通知渠道必须存在才能发通知
        NotificationFactory.ensureChannels(this)
    }
}
