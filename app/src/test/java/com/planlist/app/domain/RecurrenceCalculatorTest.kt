package com.planlist.app.domain

import com.planlist.app.data.db.entity.PlanGroupEntity
import com.planlist.app.data.db.entity.PlanKind
import com.planlist.app.data.db.entity.RecurrenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 重复规则的核心回归测试，对应 docs/TEST_PLAN.md §2.1 / §2.2。
 *
 * 星期相关用例一律用 TemporalAdjusters 从固定基准日推导，
 * 避免依赖"我记对了 2026-03-05 是周几"这种脆弱假设。
 */
class RecurrenceCalculatorTest {

    private val base = LocalDate.of(2026, 1, 1)

    private fun group(
        recurrence: RecurrenceType,
        anchor: String = base.toString(),
        end: String? = null,
        intervalDays: Int = 1,
        weekdaysMask: Int = 0,
        monthDays: String = "",
        enabled: Boolean = true,
        timeOfDay: Int = 12 * 60 + 30,
        name: String = "测试组",
    ) = PlanGroupEntity(
        name = name,
        kind = PlanKind.MEAL,
        timeOfDay = timeOfDay,
        recurrenceType = recurrence,
        intervalDays = intervalDays,
        weekdaysMask = weekdaysMask,
        monthDays = monthDays,
        anchorDate = anchor,
        endDate = end,
        enabled = enabled,
    )

    private fun nextWeekday(day: DayOfWeek): LocalDate =
        base.with(TemporalAdjusters.nextOrSame(day))

    // ------------------------------------------------------------ DAILY

    @Test
    fun daily_occurs_on_anchor_day() {
        val g = group(RecurrenceType.DAILY)
        assertTrue(RecurrenceCalculator.occursOn(g, base))
    }

    @Test
    fun daily_does_not_occur_before_anchor() {
        val g = group(RecurrenceType.DAILY)
        assertFalse(RecurrenceCalculator.occursOn(g, base.minusDays(1)))
    }

    @Test
    fun daily_occurs_on_end_date_inclusive() {
        val g = group(RecurrenceType.DAILY, end = "2026-01-10")
        assertTrue(RecurrenceCalculator.occursOn(g, LocalDate.of(2026, 1, 10)))
    }

    @Test
    fun daily_does_not_occur_after_end_date() {
        val g = group(RecurrenceType.DAILY, end = "2026-01-10")
        assertFalse(RecurrenceCalculator.occursOn(g, LocalDate.of(2026, 1, 11)))
    }

    // ------------------------------------------------------ EVERY_N_DAYS

    @Test
    fun every_n_days_hits_anchor_and_multiples() {
        val g = group(RecurrenceType.EVERY_N_DAYS, intervalDays = 2)
        assertTrue(RecurrenceCalculator.occursOn(g, base))
        assertFalse(RecurrenceCalculator.occursOn(g, base.plusDays(1)))
        assertTrue(RecurrenceCalculator.occursOn(g, base.plusDays(2)))
        assertTrue(RecurrenceCalculator.occursOn(g, base.plusDays(100)))
    }

    @Test
    fun every_three_days_phase_is_stable() {
        val g = group(RecurrenceType.EVERY_N_DAYS, intervalDays = 3)
        assertTrue(RecurrenceCalculator.occursOn(g, base.plusDays(99)))
        assertFalse(RecurrenceCalculator.occursOn(g, base.plusDays(100)))
    }

    @Test
    fun every_one_day_behaves_like_daily() {
        val g = group(RecurrenceType.EVERY_N_DAYS, intervalDays = 1)
        assertTrue(RecurrenceCalculator.occursOn(g, base))
        assertTrue(RecurrenceCalculator.occursOn(g, base.plusDays(7)))
    }

    @Test
    fun every_n_days_with_dirty_interval_does_not_crash() {
        // 脏数据：intervalDays = 0。必须钳制为 1，而不是抛 ArithmeticException
        val g = group(RecurrenceType.EVERY_N_DAYS, intervalDays = 0)
        assertTrue(RecurrenceCalculator.occursOn(g, base))
        assertTrue(RecurrenceCalculator.occursOn(g, base.plusDays(3)))
    }

    // ---------------------------------------------------------- WEEKDAYS

    @Test
    fun weekdays_mask_selects_monday() {
        val monday = nextWeekday(DayOfWeek.MONDAY)
        val g = group(RecurrenceType.WEEKDAYS, weekdaysMask = 1 shl 0)
        assertTrue(RecurrenceCalculator.occursOn(g, monday))
        assertFalse(RecurrenceCalculator.occursOn(g, monday.plusDays(1)))
    }

    @Test
    fun weekdays_mask_selects_sunday_as_bit_six() {
        val sunday = nextWeekday(DayOfWeek.SUNDAY)
        val g = group(RecurrenceType.WEEKDAYS, weekdaysMask = 1 shl 6)
        assertTrue(RecurrenceCalculator.occursOn(g, sunday))
        assertFalse(RecurrenceCalculator.occursOn(g, sunday.minusDays(1)))
    }

    @Test
    fun weekdays_all_seven_days_occurs_every_day() {
        val g = group(RecurrenceType.WEEKDAYS, weekdaysMask = 0b1111111)
        for (offset in 0L..13L) {
            assertTrue(RecurrenceCalculator.occursOn(g, base.plusDays(offset)))
        }
    }

    @Test
    fun weekdays_empty_mask_never_occurs() {
        val g = group(RecurrenceType.WEEKDAYS, weekdaysMask = 0)
        for (offset in 0L..13L) {
            assertFalse(RecurrenceCalculator.occursOn(g, base.plusDays(offset)))
        }
    }

    @Test
    fun weekdays_workday_mask_excludes_weekend() {
        // 工作日 = 周一~周五 = bit0..bit4
        val g = group(RecurrenceType.WEEKDAYS, weekdaysMask = 0b0011111)
        val saturday = nextWeekday(DayOfWeek.SATURDAY)
        val sunday = nextWeekday(DayOfWeek.SUNDAY)
        val monday = nextWeekday(DayOfWeek.MONDAY)
        assertFalse(RecurrenceCalculator.occursOn(g, saturday))
        assertFalse(RecurrenceCalculator.occursOn(g, sunday))
        assertTrue(RecurrenceCalculator.occursOn(g, monday))
    }

    // -------------------------------------------------------- MONTH_DAYS

    @Test
    fun month_days_matches_listed_days_only() {
        val g = group(RecurrenceType.MONTH_DAYS, monthDays = "1,15")
        assertTrue(RecurrenceCalculator.occursOn(g, LocalDate.of(2026, 3, 1)))
        assertTrue(RecurrenceCalculator.occursOn(g, LocalDate.of(2026, 3, 15)))
        assertFalse(RecurrenceCalculator.occursOn(g, LocalDate.of(2026, 3, 2)))
    }

    @Test
    fun month_days_31_is_skipped_in_short_months_without_crashing() {
        val g = group(RecurrenceType.MONTH_DAYS, monthDays = "31")
        // 2 月只有 28 天，这一天永远不会到来
        for (day in 1..28) {
            assertFalse(RecurrenceCalculator.occursOn(g, LocalDate.of(2026, 2, day)))
        }
        // 4 月只有 30 天
        assertFalse(RecurrenceCalculator.occursOn(g, LocalDate.of(2026, 4, 30)))
        // 1 月有 31 天
        assertTrue(RecurrenceCalculator.occursOn(g, LocalDate.of(2026, 1, 31)))
    }

    @Test
    fun month_days_29_handles_leap_year_correctly() {
        val g = group(RecurrenceType.MONTH_DAYS, monthDays = "29")
        assertFalse(RecurrenceCalculator.occursOn(g, LocalDate.of(2027, 2, 28)))
        assertTrue(RecurrenceCalculator.occursOn(g, LocalDate.of(2028, 2, 29)))
    }

    @Test
    fun month_days_empty_string_never_occurs() {
        val g = group(RecurrenceType.MONTH_DAYS, monthDays = "")
        for (day in 1..31) {
            assertFalse(RecurrenceCalculator.occursOn(g, LocalDate.of(2026, 1, day)))
        }
    }

    @Test
    fun month_days_tolerates_whitespace_and_garbage() {
        val g = group(RecurrenceType.MONTH_DAYS, monthDays = "1, 15 ,31,abc,-3,999")
        assertTrue(RecurrenceCalculator.occursOn(g, LocalDate.of(2026, 1, 1)))
        assertTrue(RecurrenceCalculator.occursOn(g, LocalDate.of(2026, 1, 15)))
        assertTrue(RecurrenceCalculator.occursOn(g, LocalDate.of(2026, 1, 31)))
        assertFalse(RecurrenceCalculator.occursOn(g, LocalDate.of(2026, 1, 2)))
    }

    // -------------------------------------------------------------- ONCE

    @Test
    fun once_occurs_only_on_anchor() {
        val g = group(RecurrenceType.ONCE)
        assertTrue(RecurrenceCalculator.occursOn(g, base))
        assertFalse(RecurrenceCalculator.occursOn(g, base.plusDays(1)))
        assertFalse(RecurrenceCalculator.occursOn(g, base.minusDays(1)))
    }

    // ------------------------------------------------------------ 通用

    @Test
    fun disabled_group_never_occurs() {
        val g = group(RecurrenceType.DAILY, enabled = false)
        assertFalse(RecurrenceCalculator.occursOn(g, base))
        assertFalse(RecurrenceCalculator.occursOn(g, base.plusDays(3)))
    }

    @Test
    fun corrupted_anchor_does_not_crash_and_never_occurs() {
        val g = group(RecurrenceType.DAILY, anchor = "not-a-date")
        assertFalse(RecurrenceCalculator.occursOn(g, base))
    }

    @Test
    fun corrupted_end_date_does_not_crash() {
        val g = group(RecurrenceType.DAILY, end = "2026/01/10")
        assertFalse(RecurrenceCalculator.occursOn(g, base))
    }

    @Test
    fun end_before_anchor_never_occurs() {
        val g = group(RecurrenceType.DAILY, end = "2025-12-01")
        assertFalse(RecurrenceCalculator.occursOn(g, base))
    }

    @Test
    fun anchor_and_end_on_same_day() {
        val g = group(RecurrenceType.DAILY, end = base.toString())
        assertTrue(RecurrenceCalculator.occursOn(g, base))
        assertFalse(RecurrenceCalculator.occursOn(g, base.plusDays(1)))
    }

    // ------------------------------------------------- nextOccurrence

    @Test
    fun next_occurrence_later_today_returns_today() {
        val g = group(RecurrenceType.DAILY)
        val from = LocalDateTime.of(2026, 3, 5, 10, 0)
        assertEquals(
            LocalDateTime.of(2026, 3, 5, 12, 30),
            RecurrenceCalculator.nextOccurrence(g, from),
        )
    }

    @Test
    fun next_occurrence_after_today_returns_tomorrow() {
        val g = group(RecurrenceType.DAILY)
        val from = LocalDateTime.of(2026, 3, 5, 14, 0)
        assertEquals(
            LocalDateTime.of(2026, 3, 6, 12, 30),
            RecurrenceCalculator.nextOccurrence(g, from),
        )
    }

    @Test
    fun next_occurrence_is_strictly_after_from() {
        // 恰好等于触发时刻时必须给下一天，否则同一分钟会被反复触发
        val g = group(RecurrenceType.DAILY)
        val from = LocalDateTime.of(2026, 3, 5, 12, 30)
        assertEquals(
            LocalDateTime.of(2026, 3, 6, 12, 30),
            RecurrenceCalculator.nextOccurrence(g, from),
        )
    }

    @Test
    fun next_occurrence_from_late_night_crosses_midnight() {
        val g = group(RecurrenceType.DAILY, timeOfDay = 0)
        val from = LocalDateTime.of(2026, 3, 5, 23, 59)
        assertEquals(
            LocalDateTime.of(2026, 3, 6, 0, 0),
            RecurrenceCalculator.nextOccurrence(g, from),
        )
    }

    @Test
    fun next_occurrence_crosses_month_boundary() {
        val g = group(RecurrenceType.MONTH_DAYS, monthDays = "1", anchor = "2026-01-01")
        val from = LocalDateTime.of(2026, 3, 31, 10, 0)
        assertEquals(
            LocalDateTime.of(2026, 4, 1, 12, 30),
            RecurrenceCalculator.nextOccurrence(g, from),
        )
    }

    @Test
    fun next_occurrence_crosses_year_boundary() {
        val monday = LocalDate.of(2026, 12, 28).with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val g = group(RecurrenceType.WEEKDAYS, weekdaysMask = 1 shl 0, anchor = "2026-01-01")
        val from = LocalDateTime.of(2026, 12, 29, 10, 0)
        assertEquals(
            LocalDateTime.of(monday, java.time.LocalTime.of(12, 30)),
            RecurrenceCalculator.nextOccurrence(g, from),
        )
    }

    @Test
    fun next_occurrence_after_end_date_returns_null() {
        val g = group(RecurrenceType.DAILY, end = "2026-03-05")
        val from = LocalDateTime.of(2026, 3, 6, 0, 0)
        assertNull(RecurrenceCalculator.nextOccurrence(g, from))
    }

    @Test
    fun next_occurrence_returns_null_when_mask_is_empty() {
        val g = group(RecurrenceType.WEEKDAYS, weekdaysMask = 0)
        assertNull(RecurrenceCalculator.nextOccurrence(g, LocalDateTime.of(2026, 3, 5, 10, 0)))
    }

    @Test
    fun dirty_time_of_day_is_clamped_instead_of_throwing() {
        val g = group(RecurrenceType.DAILY, timeOfDay = 99999)
        val next = RecurrenceCalculator.nextOccurrence(g, LocalDateTime.of(2026, 3, 5, 10, 0))
        assertEquals(LocalDateTime.of(2026, 3, 5, 23, 59), next)
    }

    // ------------------------------------------------------------- DST

    @Test
    fun wall_clock_time_survives_dst_spring_forward() {
        val zone = ZoneId.of("America/New_York")
        val before = LocalDateTime.of(2026, 3, 7, 12, 30)
        val after = LocalDateTime.of(2026, 3, 9, 12, 30)

        val instantBefore = before.atZone(zone).toInstant()
        val instantAfter = after.atZone(zone).toInstant()

        // 中间跨过夏令时开始（3 月 8 日 02:00 跳到 03:00），因此相隔 47 小时而非 48
        assertEquals(47L, Duration.between(instantBefore, instantAfter).toHours())
        // 反解回本地时刻必须仍是 12:30 —— 提醒不会漂移一小时
        assertEquals(before, LocalDateTime.ofInstant(instantBefore, zone))
        assertEquals(after, LocalDateTime.ofInstant(instantAfter, zone))
    }

    @Test
    fun zone_injection_gives_same_wall_clock_in_different_zones() {
        val local = LocalDateTime.of(2026, 6, 1, 19, 0)
        val shanghai = local.atZone(ZoneId.of("Asia/Shanghai")).toInstant()
        val newYork = local.atZone(ZoneId.of("America/New_York")).toInstant()
        // 不同时区的同一挂钟时刻对应不同 Instant
        assertTrue(shanghai != newYork)
        assertEquals(local, LocalDateTime.ofInstant(shanghai, ZoneId.of("Asia/Shanghai")))
        assertEquals(local, LocalDateTime.ofInstant(newYork, ZoneId.of("America/New_York")))
    }

    // ------------------------------------------------- occurrencesOn

    @Test
    fun occurrences_on_sorted_by_time_of_day() {
        val morning = group(RecurrenceType.DAILY, timeOfDay = 8 * 60, name = "早餐")
        val noon = group(RecurrenceType.DAILY, timeOfDay = 12 * 60, name = "午餐")
        val night = group(RecurrenceType.DAILY, timeOfDay = 19 * 60, name = "训练")

        val result = RecurrenceCalculator.occurrencesOn(listOf(night, morning, noon), base)
        assertEquals(listOf("早餐", "午餐", "训练"), result.map { it.name })
    }
}
