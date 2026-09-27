package com.planlist.app.reminder

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.planlist.app.PlanListApp
import java.util.concurrent.TimeUnit

/**
 * 兜底补齐排程窗口。
 *
 * WorkManager **只**负责"把闹钟窗口补满"，**不**负责精确提醒
 * （其最小周期 15 分钟且受 Doze 影响，做不到准时）。
 */
class AlarmTopUpWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? PlanListApp ?: return Result.success()
        return try {
            app.container.reminderScheduler.scheduleWindow()
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val PERIODIC_NAME = "planlist-alarm-top-up-periodic"
        private const val ONCE_NAME = "planlist-alarm-top-up-once"

        /** 每 12 小时补齐一次，用 KEEP 保证不会堆叠多个周期任务。 */
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<AlarmTopUpWorker>(12, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** 立即补一次（App 回到前台时调用），用 REPLACE 保证不会排队堆积。 */
        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<AlarmTopUpWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONCE_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
