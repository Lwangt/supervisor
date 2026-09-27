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

class TodayAssemblerTest {

    private val date = LocalDate.of(2026, 3, 5)

    private fun group(id: Long, name: String, hour: Int, minute: Int = 0) = PlanGroupEntity(
        id = id,
        name = name,
        kind = PlanKind.MEAL,
        timeOfDay = hour * 60 + minute,
        recurrenceType = RecurrenceType.DAILY,
        anchorDate = "2026-01-01",
    )

    private fun item(id: Long, groupId: Long, name: String, calories: Int? = null) = PlanItemEntity(
        id = id,
        groupId = groupId,
        name = name,
        calories = calories,
    )

    private fun log(groupId: Long, itemId: Long, status: LogStatus = LogStatus.COMPLETED) =
        ItemLogEntity(
            date = date.toString(),
            groupId = groupId,
            itemId = itemId,
            status = status,
            loggedAt = 0L,
        )

    private val breakfast = group(1L, "早餐", 8)
    private val lunch = group(2L, "午餐", 12, 30)
    private val workout = group(3L, "训练", 19)

    private val items = listOf(
        item(11L, 1L, "鸡蛋", 140),
        item(12L, 1L, "牛奶", 120),
        item(21L, 2L, "鸡胸肉", 210),
        item(22L, 2L, "米饭", 260),
        item(31L, 3L, "卧推"),
    )

    @Test
    fun completed_group_is_labelled_done() {
        val now = LocalDateTime.of(2026, 3, 5, 13, 0)
        val result = TodayAssembler.assemble(
            date = date,
            now = now,
            groups = listOf(breakfast, lunch, workout),
            items = items,
            logs = listOf(log(1L, 11L), log(1L, 12L)),
        )
        assertEquals(TimeSlot.DONE, result.first { it.group.id == 1L }.slot)
    }

    @Test
    fun earliest_pending_group_is_now_and_later_due_group_is_overdue() {
        val now = LocalDateTime.of(2026, 3, 5, 13, 0)
        val result = TodayAssembler.assemble(
            date = date,
            now = now,
            groups = listOf(breakfast, lunch, workout),
            items = items,
            logs = emptyList(),
        )
        // 焦点是最早未完成的组（早餐 08:00）
        assertEquals(TimeSlot.NOW, result.first { it.group.id == 1L }.slot)
        // 午餐 12:30 已过且不是焦点
        assertEquals(TimeSlot.OVERDUE, result.first { it.group.id == 2L }.slot)
        // 训练 19:00 还没到
        assertEquals(TimeSlot.UP_NEXT, result.first { it.group.id == 3L }.slot)
    }

    @Test
    fun upcoming_focus_is_now_before_its_time() {
        val now = LocalDateTime.of(2026, 3, 5, 7, 0)
        val result = TodayAssembler.assemble(
            date = date,
            now = now,
            groups = listOf(breakfast, lunch, workout),
            items = items,
            logs = emptyList(),
        )
        assertEquals(TimeSlot.NOW, result.first { it.group.id == 1L }.slot)
        assertEquals(TimeSlot.UP_NEXT, result.first { it.group.id == 2L }.slot)
        assertEquals(TimeSlot.UP_NEXT, result.first { it.group.id == 3L }.slot)
    }

    @Test
    fun when_all_complete_there_is_no_now_group() {
        val now = LocalDateTime.of(2026, 3, 5, 23, 59)
        val result = TodayAssembler.assemble(
            date = date,
            now = now,
            groups = listOf(breakfast, lunch, workout),
            items = items,
            logs = listOf(
                log(1L, 11L), log(1L, 12L),
                log(2L, 21L), log(2L, 22L),
                log(3L, 31L),
            ),
        )
        assertTrue(result.all { it.slot == TimeSlot.DONE })
    }

    @Test
    fun result_is_sorted_by_due_time() {
        val result = TodayAssembler.assemble(
            date = date,
            now = LocalDateTime.of(2026, 3, 5, 7, 0),
            groups = listOf(workout, lunch, breakfast),
            items = items,
            logs = emptyList(),
        )
        assertEquals(listOf("早餐", "午餐", "训练"), result.map { it.group.name })
    }

    @Test
    fun group_not_scheduled_today_is_excluded() {
        val once = PlanGroupEntity(
            id = 9L,
            name = "一次性计划",
            kind = PlanKind.MEAL,
            timeOfDay = 600,
            recurrenceType = RecurrenceType.ONCE,
            anchorDate = "2026-04-01",
        )
        val result = TodayAssembler.assemble(
            date = date,
            now = LocalDateTime.of(2026, 3, 5, 7, 0),
            groups = listOf(breakfast, once),
            items = items,
            logs = emptyList(),
        )
        assertEquals(listOf("早餐"), result.map { it.group.name })
    }

    @Test
    fun group_without_items_is_never_complete() {
        val empty = PlanGroupEntity(
            id = 5L,
            name = "空组",
            kind = PlanKind.MEAL,
            timeOfDay = 600,
            recurrenceType = RecurrenceType.DAILY,
            anchorDate = "2026-01-01",
        )
        val result = TodayAssembler.assemble(
            date = date,
            now = LocalDateTime.of(2026, 3, 5, 12, 0),
            groups = listOf(empty),
            items = emptyList(),
            logs = emptyList(),
        )
        val group = result.single()
        assertFalse(group.isComplete)
        assertEquals(0, group.totalCount)
        assertEquals(0f, group.progress, 0.001f)
    }

    @Test
    fun macro_mode_is_on_when_any_item_has_macros() {
        val result = TodayAssembler.assemble(
            date = date,
            now = LocalDateTime.of(2026, 3, 5, 7, 0),
            groups = listOf(breakfast, workout),
            items = items,
            logs = emptyList(),
        )
        assertTrue(result.first { it.group.id == 1L }.useMacroMode)
        // 训练组只填了名称，没有宏量 -> 降级为计数模式
        assertFalse(result.first { it.group.id == 3L }.useMacroMode)
    }

    @Test
    fun eaten_macros_count_only_completed_items() {
        val result = TodayAssembler.assemble(
            date = date,
            now = LocalDateTime.of(2026, 3, 5, 9, 0),
            groups = listOf(breakfast),
            items = items,
            logs = listOf(log(1L, 11L)), // 只完成鸡蛋
        )
        val group = result.single()
        assertEquals(260, group.planned.calories)   // 140 + 120
        assertEquals(140, group.eaten.calories)     // 只算鸡蛋
        assertEquals(1, group.completedCount)
        val expected = 140f / 260f
        assertEquals(expected, group.progress, 0.001f)
    }

    @Test
    fun skipped_items_are_not_counted_as_eaten() {
        val result = TodayAssembler.assemble(
            date = date,
            now = LocalDateTime.of(2026, 3, 5, 9, 0),
            groups = listOf(breakfast),
            items = items,
            logs = listOf(log(1L, 11L, LogStatus.SKIPPED)),
        )
        val group = result.single()
        assertEquals(0, group.eaten.calories)
        assertFalse(group.isComplete)
        assertEquals(0, group.completedCount)
        assertEquals(2, group.totalCount)
    }

    @Test
    fun day_summary_aggregates_groups_and_items() {
        val groups = TodayAssembler.assemble(
            date = date,
            now = LocalDateTime.of(2026, 3, 5, 20, 0),
            groups = listOf(breakfast, lunch, workout),
            items = items,
            logs = listOf(log(1L, 11L), log(1L, 12L), log(2L, 21L)),
        )
        val summary = TodayAssembler.daySummary(date, groups)
        assertEquals(3, summary.totalGroups)
        assertEquals(1, summary.completedGroups)
        assertEquals(5, summary.totalItems)
        assertEquals(3, summary.completedItems)
        assertTrue(summary.isPartial)
        assertFalse(summary.isDone)
    }
}
