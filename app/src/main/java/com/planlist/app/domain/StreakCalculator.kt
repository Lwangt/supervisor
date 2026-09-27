package com.planlist.app.domain

import java.time.LocalDate

/**
 * 连续打卡天数与完成率。
 *
 * 产品语义（写死在测试里，见 docs/TEST_PLAN.md §2.4）：
 * - **休息日（当天没有任何计划）既不算完成，也不打断连续**。
 * - **今天还没做完不算断** —— 否则每天早上一睁眼就会看到"连续 0 天"，体验极差。
 * - 部分完成的一天不算完成，但也不重置（相当于"跳过"）。
 */
object StreakCalculator {

    /**
     * 当前连续完成天数。
     *
     * @param days 按任意顺序传入的每日完成情况
     * @param today 今天；传入后，今天未完成不计入也不打断
     */
    fun currentStreak(days: List<DaySummary>, today: LocalDate? = null): Int {
        val ordered = days
            .filter { today == null || !it.date.isAfter(today) }
            .sortedByDescending { it.date }

        var streak = 0
        var isFirstScanned = true

        for (day in ordered) {
            if (day.isRest) continue

            if (day.isDone) {
                streak++
                isFirstScanned = false
                continue
            }

            // 今天尚未完成：跳过，继续看昨天，不打断连续
            if (isFirstScanned && today != null && day.date == today) {
                isFirstScanned = false
                continue
            }

            break
        }
        return streak
    }

    /**
     * 完成率 = 已完成天数 / 有计划的已过去天数。
     * 没有任何计划日时返回 0.0，不返回 NaN。
     */
    fun completionRate(days: List<DaySummary>, today: LocalDate? = null): Double {
        val relevant = days.filter {
            !it.isRest && (today == null || !it.date.isAfter(today))
        }
        if (relevant.isEmpty()) return 0.0
        return relevant.count { it.isDone }.toDouble() / relevant.size.toDouble()
    }
}
