package com.planlist.app.domain

import com.planlist.app.data.db.entity.PlanGroupEntity
import com.planlist.app.data.db.entity.PlanKind
import com.planlist.app.data.db.entity.RecurrenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

class NextReminderCalculatorTest {

    private val from = LocalDateTime.of(2026, 3, 5, 8, 0)

    private fun group(
        name: String,
        hour: Int,
        minute: Int = 0,
        anchor: String = "2026-01-01",
        end: String? = null,
        recurrence: RecurrenceType = RecurrenceType.DAILY,
        weekdaysMask: Int = 0,
        offsetMinutes: Int = 0,
        enabled: Boolean = true,
    ) = PlanGroupEntity(
        name = name,
        kind = PlanKind.MEAL,
        timeOfDay = hour * 60 + minute,
        recurrenceType = recurrence,
        weekdaysMask = weekdaysMask,
        anchorDate = anchor,
        endDate = end,
        reminderOffsetMinutes = offsetMinutes,
        enabled = enabled,
    )

    @Test
    fun picks_the_earliest_upcoming_trigger() {
        val result = NextReminderCalculator.next(
            groups = listOf(
                group("早餐", 8, 0),    // 恰好等于 from，不算未来 -> 明天
                group("午餐", 12, 30),
                group("训练", 19, 0),
            ),
            from = from,
        )
        assertEquals("午餐", result!!.groupName)
        assertEquals(LocalDateTime.of(2026, 3, 5, 12, 30), result.triggerAt)
    }

    @Test
    fun offset_can_make_a_later_group_fire_first() {
        val result = NextReminderCalculator.next(
            groups = listOf(
                group("训练", 12, 15, offsetMinutes = 0),   // 12:15
                group("午餐", 12, 30, offsetMinutes = 30),  // 12:00，提前提醒反而更早
            ),
            from = from,
        )
        assertEquals("午餐", result!!.groupName)
        assertEquals(LocalDateTime.of(2026, 3, 5, 12, 0), result.triggerAt)
    }

    @Test
    fun trigger_is_strictly_after_from() {
        // 与 from 同一分钟不能算"下一次"，否则会立刻重复触发
        val result = NextReminderCalculator.next(
            groups = listOf(group("早餐", 8, 0)),
            from = from,
        )
        assertEquals(LocalDateTime.of(2026, 3, 6, 8, 0), result!!.triggerAt)
    }

    @Test
    fun returns_null_when_there_are_no_groups() {
        assertNull(NextReminderCalculator.next(emptyList(), from))
    }

    @Test
    fun ignores_disabled_groups() {
        val result = NextReminderCalculator.next(
            groups = listOf(group("午餐", 12, 30, enabled = false)),
            from = from,
        )
        assertNull(result)
    }

    @Test
    fun returns_null_when_every_plan_has_ended() {
        val result = NextReminderCalculator.next(
            groups = listOf(group("午餐", 12, 30, end = "2026-03-01")),
            from = from,
        )
        assertNull(result)
    }

    @Test
    fun skips_to_the_next_selected_weekday() {
        val nextMonday = LocalDate.of(2026, 3, 5).with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val result = NextReminderCalculator.next(
            groups = listOf(
                group(
                    "训练",
                    7,
                    0,
                    recurrence = RecurrenceType.WEEKDAYS,
                    weekdaysMask = 1 shl 0, // 仅周一
                ),
            ),
            from = from,
        )
        assertEquals(LocalDateTime.of(nextMonday, java.time.LocalTime.of(7, 0)), result!!.triggerAt)
    }

    @Test
    fun plan_starting_in_the_future_is_reported_at_its_start() {
        val result = NextReminderCalculator.next(
            groups = listOf(group("新计划", 12, 30, anchor = "2026-04-01")),
            from = from,
        )
        assertEquals(LocalDateTime.of(2026, 4, 1, 12, 30), result!!.triggerAt)
    }

}

