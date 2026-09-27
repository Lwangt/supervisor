package com.planlist.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {

    private val today = LocalDate.of(2026, 3, 10)

    private fun day(
        date: LocalDate,
        totalGroups: Int,
        completedGroups: Int,
    ) = DaySummary(
        date = date,
        totalGroups = totalGroups,
        completedGroups = completedGroups,
        totalItems = totalGroups * 3,
        completedItems = completedGroups * 3,
    )

    @Test
    fun counts_consecutive_completed_days() {
        val days = listOf(
            day(today, 3, 3),
            day(today.minusDays(1), 3, 3),
            day(today.minusDays(2), 3, 3),
        )
        assertEquals(3, StreakCalculator.currentStreak(days, today))
    }

    @Test
    fun stops_at_first_gap() {
        val days = listOf(
            day(today, 3, 3),
            day(today.minusDays(1), 3, 3),
            day(today.minusDays(2), 3, 1), // 断点
            day(today.minusDays(3), 3, 3),
        )
        assertEquals(2, StreakCalculator.currentStreak(days, today))
    }

    @Test
    fun today_not_finished_does_not_break_streak() {
        val days = listOf(
            day(today, 3, 0),            // 今天还没做完
            day(today.minusDays(1), 3, 3),
            day(today.minusDays(2), 3, 3),
        )
        assertEquals(2, StreakCalculator.currentStreak(days, today))
    }

    @Test
    fun rest_days_are_skipped_without_breaking() {
        val days = listOf(
            day(today, 3, 3),
            day(today.minusDays(1), 0, 0), // 休息日
            day(today.minusDays(2), 3, 3),
        )
        assertEquals(2, StreakCalculator.currentStreak(days, today))
    }

    @Test
    fun partial_day_does_not_count_but_also_does_not_reset_after_start() {
        val days = listOf(
            day(today, 3, 3),
            day(today.minusDays(1), 3, 1), // 部分完成 -> 断
            day(today.minusDays(2), 3, 3),
        )
        assertEquals(1, StreakCalculator.currentStreak(days, today))
    }

    @Test
    fun returns_zero_when_no_days_have_plans() {
        val days = listOf(
            day(today, 0, 0),
            day(today.minusDays(1), 0, 0),
        )
        assertEquals(0, StreakCalculator.currentStreak(days, today))
    }

    @Test
    fun ignores_future_dates() {
        val days = listOf(
            day(today.plusDays(1), 3, 3),
            day(today, 3, 3),
        )
        assertEquals(1, StreakCalculator.currentStreak(days, today))
    }

    @Test
    fun completion_rate_counts_only_days_with_plans() {
        val days = listOf(
            day(today, 3, 3),             // 完成
            day(today.minusDays(1), 3, 1), // 未完成
            day(today.minusDays(2), 3, 3), // 完成
            day(today.minusDays(3), 0, 0), // 休息，不计入分母
        )
        assertEquals(2.0 / 3.0, StreakCalculator.completionRate(days, today), 0.0001)
    }

    @Test
    fun completion_rate_is_zero_when_no_planned_days() {
        val days = listOf(day(today, 0, 0))
        assertEquals(0.0, StreakCalculator.completionRate(days, today), 0.0001)
    }

    @Test
    fun completion_rate_ignores_future_dates() {
        val days = listOf(
            day(today, 3, 3),
            day(today.plusDays(1), 3, 0),
        )
        assertEquals(1.0, StreakCalculator.completionRate(days, today), 0.0001)
    }

    @Test
    fun day_summary_flags_are_mutually_consistent() {
        val done = day(today, 3, 3)
        assertEquals(true, done.isDone)
        assertEquals(false, done.isPartial)
        assertEquals(false, done.isMissed)
        assertEquals(false, done.isRest)

        val partial = day(today, 3, 1)
        assertEquals(false, partial.isDone)
        assertEquals(true, partial.isPartial)

        val missed = day(today, 3, 0)
        assertEquals(true, missed.isMissed)

        val rest = day(today, 0, 0)
        assertEquals(true, rest.isRest)
    }
}
