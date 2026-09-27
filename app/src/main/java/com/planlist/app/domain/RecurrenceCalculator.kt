package com.planlist.app.domain

import com.planlist.app.data.db.entity.PlanGroupEntity
import com.planlist.app.data.db.entity.RecurrenceType
import com.planlist.app.data.db.entity.parseMonthDays
import com.planlist.app.data.db.entity.safeIntervalDays
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 重复规则求值。
 *
 * 这是全项目最核心的纯函数：**日程不进数据库**，某一天该做哪些组完全由这里算出来。
 * 因此它必须：
 *  - 不依赖 Android 框架（可在纯 JVM 单元测试里跑到死）
 *  - 对任何脏数据（空串、非法文本、越界数字）都返回合理结果而不是抛异常
 *  - 可注入时钟（由调用方传入"现在"，本类自身不读系统时间）
 *
 * 对应测试见 docs/TEST_PLAN.md §2.1 / §2.2（约 40 例）。
 */
object RecurrenceCalculator {

    /** 搜索下一次发生时刻的最大天数，防止死循环。 */
    const val DEFAULT_SEARCH_LIMIT_DAYS: Long = 800L

    /**
     * 该组是否落在 [date] 这一天。
     *
     * 判定顺序：启用 → 锚点可解析 → 不早于锚点 → 不晚于结束日 → 按规则匹配。
     */
    fun occursOn(group: PlanGroupEntity, date: LocalDate): Boolean {
        if (!group.enabled) return false

        val anchor = try {
            LocalDate.parse(group.anchorDate)
        } catch (e: Exception) {
            // 锚点损坏的计划不参与任何日子，但也不影响其他计划
            return false
        }

        val end: LocalDate? = try {
            group.endDate?.let { LocalDate.parse(it) }
        } catch (e: Exception) {
            return false
        }

        if (date.isBefore(anchor)) return false
        if (end != null && date.isAfter(end)) return false

        return when (group.recurrenceType) {
            RecurrenceType.DAILY -> true

            RecurrenceType.EVERY_N_DAYS -> {
                val days = ChronoUnit.DAYS.between(anchor, date)
                days >= 0 && days % group.safeIntervalDays.toLong() == 0L
            }

            RecurrenceType.WEEKDAYS -> {
                // bit0 = 周一 … bit6 = 周日，与 DayOfWeek.value(1..7) 对齐。
                // 这里刻意不用 java.util.Calendar.MONDAY(=2)，那是历史上最容易搞错的偏移。
                val index = date.dayOfWeek.value - 1
                ((group.weekdaysMask shr index) and 1) == 1
            }

            RecurrenceType.MONTH_DAYS -> date.dayOfMonth in parseMonthDays(group.monthDays)

            RecurrenceType.ONCE -> date == anchor
        }
    }

    /**
     * 严格晚于 [from] 的下一次发生时刻；找不到（例如已过结束日）返回 null。
     *
     * "严格晚于"很重要：若返回等于 from 的时刻，会在同一分钟反复触发同一个闹钟。
     */
    fun nextOccurrence(
        group: PlanGroupEntity,
        from: LocalDateTime,
        searchLimitDays: Long = DEFAULT_SEARCH_LIMIT_DAYS,
    ): LocalDateTime? {
        val time = group.dueTime()
        var date = from.toLocalDate()
        var remaining = searchLimitDays

        while (remaining > 0) {
            if (occursOn(group, date)) {
                val candidate = LocalDateTime.of(date, time)
                if (candidate.isAfter(from)) return candidate
            }
            date = date.plusDays(1)
            remaining--
        }
        return null
    }

    /**
     * [date] 当天应当发生的组，按应做时刻升序返回。
     */
    fun occurrencesOn(groups: List<PlanGroupEntity>, date: LocalDate): List<PlanGroupEntity> =
        groups.filter { occursOn(it, date) }
            .sortedWith(compareBy({ it.timeOfDay }, { it.sortOrder }, { it.id }))
}
