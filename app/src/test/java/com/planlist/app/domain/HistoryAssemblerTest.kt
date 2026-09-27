package com.planlist.app.domain

import com.planlist.app.data.db.entity.ItemLogEntity
import com.planlist.app.data.db.entity.LogStatus
import com.planlist.app.data.db.entity.PlanGroupEntity
import com.planlist.app.data.db.entity.PlanItemEntity
import com.planlist.app.data.db.entity.PlanKind
import com.planlist.app.data.db.entity.RecurrenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class HistoryAssemblerTest {

    private val day1 = LocalDate.of(2026, 3, 1)
    private val day2 = LocalDate.of(2026, 3, 2)
    private val day3 = LocalDate.of(2026, 3, 3)

    private val group = PlanGroupEntity(
        id = 1L,
        name = "午餐",
        kind = PlanKind.MEAL,
        timeOfDay = 750,
        recurrenceType = RecurrenceType.DAILY,
        anchorDate = "2026-01-01",
    )

    private val items = listOf(
        PlanItemEntity(id = 11L, groupId = 1L, name = "A"),
        PlanItemEntity(id = 12L, groupId = 1L, name = "B"),
    )

    private fun log(date: LocalDate, itemId: Long) = ItemLogEntity(
        date = date.toString(),
        groupId = 1L,
        itemId = itemId,
        status = LogStatus.COMPLETED,
        loggedAt = 0L,
    )

    @Test
    fun produces_one_summary_per_day_in_range() {
        val result = HistoryAssembler.summaries(
            from = day1,
            to = day3,
            now = LocalDateTime.of(2026, 3, 3, 20, 0),
            groups = listOf(group),
            items = items,
            logs = emptyList(),
        )
        assertEquals(3, result.size)
        assertEquals(listOf(day1, day2, day3), result.map { it.date })
    }

    @Test
    fun fully_completed_day_is_done() {
        val result = HistoryAssembler.summaries(
            from = day1,
            to = day1,
            now = LocalDateTime.of(2026, 3, 1, 20, 0),
            groups = listOf(group),
            items = items,
            logs = listOf(log(day1, 11L), log(day1, 12L)),
        ).single()
        assertTrue(result.isDone)
        assertEquals(2, result.completedItems)
    }

    @Test
    fun partially_completed_day_is_partial() {
        val result = HistoryAssembler.summaries(
            from = day2,
            to = day2,
            now = LocalDateTime.of(2026, 3, 2, 20, 0),
            groups = listOf(group),
            items = items,
            logs = listOf(log(day2, 11L)),
        ).single()
        assertTrue(result.isPartial)
        // 只是条目完成了一半，整组还没完成
        assertEquals(0, result.completedGroups)
        assertEquals(1, result.completedItems)
    }

    @Test
    fun day_with_no_logs_is_missed() {
        val result = HistoryAssembler.summaries(
            from = day3,
            to = day3,
            now = LocalDateTime.of(2026, 3, 3, 20, 0),
            groups = listOf(group),
            items = items,
            logs = emptyList(),
        ).single()
        assertTrue(result.isMissed)
    }

    @Test
    fun day_before_anchor_is_rest() {
        val result = HistoryAssembler.summaries(
            from = LocalDate.of(2025, 12, 31),
            to = LocalDate.of(2025, 12, 31),
            now = LocalDateTime.of(2025, 12, 31, 20, 0),
            groups = listOf(group),
            items = items,
            logs = emptyList(),
        ).single()
        assertTrue(result.isRest)
        assertEquals(0, result.totalGroups)
    }

    @Test
    fun group_without_items_does_not_dilute_completion_rate() {
        val emptyGroup = PlanGroupEntity(
            id = 2L,
            name = "空组",
            kind = PlanKind.WORKOUT,
            timeOfDay = 1200,
            recurrenceType = RecurrenceType.DAILY,
            anchorDate = "2026-01-01",
        )
        val result = HistoryAssembler.summaries(
            from = day1,
            to = day1,
            now = LocalDateTime.of(2026, 3, 1, 20, 0),
            groups = listOf(group, emptyGroup),
            items = items,
            logs = listOf(log(day1, 11L), log(day1, 12L)),
        ).single()
        assertEquals(1, result.totalGroups)
        assertTrue(result.isDone)
    }

    @Test
    fun inverted_range_returns_empty_list() {
        val result = HistoryAssembler.summaries(
            from = day3,
            to = day1,
            now = LocalDateTime.of(2026, 3, 3, 20, 0),
            groups = listOf(group),
            items = items,
            logs = emptyList(),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun logs_from_other_days_do_not_leak() {
        val result = HistoryAssembler.summaries(
            from = day1,
            to = day1,
            now = LocalDateTime.of(2026, 3, 2, 20, 0),
            groups = listOf(group),
            items = items,
            logs = listOf(log(day2, 11L), log(day2, 12L)),
        ).single()
        assertFalse(result.isDone)
        assertTrue(result.isMissed)
    }
}
