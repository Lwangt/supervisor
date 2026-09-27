package com.planlist.app.domain

import com.planlist.app.data.db.entity.PlanGroupEntity
import java.time.LocalDateTime

/**
 * 计算"下一次提醒会在什么时候响"。
 *
 * 存在的意义是**让排程可观测**：提醒类应用最怕的是"我以为排上了，其实没有"，
 * 而用户要等到点才知道。设置页拿它显示下一次提醒时间，不必等待就能自检。
 *
 * 纯函数，不依赖 Android 框架，因此可以完整单元测试。
 */
object NextReminderCalculator {

    data class NextReminder(
        val groupName: String,
        val triggerAt: LocalDateTime,
    )

    /**
     * @param groups 全部计划（内部会过滤掉未启用的）
     * @param from   以该时刻为基准，返回**严格晚于**它的最近一次提醒
     * @param searchLimitDays 向前搜索的最大天数，防止死循环
     */
    fun next(
        groups: List<PlanGroupEntity>,
        from: LocalDateTime,
        searchLimitDays: Long = DEFAULT_SEARCH_LIMIT_DAYS,
    ): NextReminder? {
        var best: NextReminder? = null

        for (group in groups) {
            if (!group.enabled) continue

            val offset = group.reminderOffsetMinutes.toLong()
            // 我们要的是 trigger = due - offset > from，
            // 等价于 due > from + offset，所以把下界抬到 from + offset 再找下一次"应做时刻"。
            val due = RecurrenceCalculator.nextOccurrence(group, from.plusMinutes(offset), searchLimitDays)
                ?: continue

            val trigger = due.minusMinutes(offset)
            if (!trigger.isAfter(from)) continue

            val currentBest = best
            if (currentBest == null || trigger.isBefore(currentBest.triggerAt)) {
                best = NextReminder(group.name, trigger)
            }
        }
        return best
    }

    const val DEFAULT_SEARCH_LIMIT_DAYS: Long = 400L
}
