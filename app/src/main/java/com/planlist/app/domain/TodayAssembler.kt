package com.planlist.app.domain

import com.planlist.app.data.db.entity.ItemLogEntity
import com.planlist.app.data.db.entity.LogStatus
import com.planlist.app.data.db.entity.PlanGroupEntity
import com.planlist.app.data.db.entity.PlanItemEntity
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 今日页上单个组的语义状态，与 Macro7 的 NOW / OVERDUE / UP NEXT 标签对应。
 */
enum class TimeSlot {
    /** 下一步该做的那一组（唯一的焦点）。 */
    NOW,

    /** 已过应做时刻且未完成。 */
    OVERDUE,

    /** 还没到点。 */
    UP_NEXT,

    /** 已完成。 */
    DONE,
}

/** 今日页的单个组视图模型。 */
data class TodayGroup(
    val group: PlanGroupEntity,
    val items: List<PlanItemEntity>,
    val statuses: Map<Long, LogStatus>,
    val dueAt: LocalDateTime,
    val slot: TimeSlot,
) {
    val totalCount: Int get() = items.size

    val completedCount: Int get() = items.count { statuses[it.id] == LogStatus.COMPLETED }

    /** 空组不算完成，避免"计划里没有条目"被误显示为已完成。 */
    val isComplete: Boolean get() = items.isNotEmpty() && completedCount == items.size

    val planned: Macros get() = MacroMath.sum(items)

    val eaten: Macros get() = MacroMath.sum(items.filter { statuses[it.id] == LogStatus.COMPLETED })

    val useMacroMode: Boolean get() = MacroMath.hasAnyMacro(items)

    val progress: Float
        get() = MacroMath.progress(eaten, planned, completedCount, totalCount, useMacroMode)
}

/**
 * 把「计划规则 + 打卡日志 + 当前时刻」组合成今日视图。
 *
 * 纯函数，不碰数据库、不读系统时间 —— 因此可以用 FixedTimeSource 精确测试。
 */
object TodayAssembler {

    fun assemble(
        date: LocalDate,
        now: LocalDateTime,
        groups: List<PlanGroupEntity>,
        items: List<PlanItemEntity>,
        logs: List<ItemLogEntity>,
    ): List<TodayGroup> {
        val itemsByGroup = items.groupBy { it.groupId }
        val statusByItem: Map<Long, LogStatus> = logs.associate { it.itemId to it.status }

        val applicable = RecurrenceCalculator.occurrencesOn(groups, date)

        val base = applicable.map { group ->
            TodayGroup(
                group = group,
                items = itemsByGroup[group.id].orEmpty().sortedWith(compareBy({ it.sortOrder }, { it.id })),
                statuses = statusByItem,
                dueAt = group.dueDateTime(date),
                slot = TimeSlot.UP_NEXT,
            )
        }.sortedBy { it.dueAt }

        // 焦点 = 最早一个尚未完成的组。它既是"现在该做的"，也是列表里最需要被推上前台的一张卡。
        val focusId = base.filter { !it.isComplete }.minByOrNull { it.dueAt }?.group?.id

        return base.map { tg ->
            val slot = when {
                tg.isComplete -> TimeSlot.DONE
                tg.group.id == focusId -> TimeSlot.NOW
                !tg.dueAt.isAfter(now) -> TimeSlot.OVERDUE
                else -> TimeSlot.UP_NEXT
            }
            tg.copy(slot = slot)
        }
    }

    /** 当天整体完成度（用于进度环与历史着色）。 */
    fun daySummary(date: LocalDate, groups: List<TodayGroup>): DaySummary {
        val total = groups.size
        val done = groups.count { it.isComplete }
        val totalItems = groups.sumOf { it.totalCount }
        val doneItems = groups.sumOf { it.completedCount }
        return DaySummary(date, total, done, totalItems, doneItems)
    }
}

/** 单日完成情况。 */
data class DaySummary(
    val date: LocalDate,
    val totalGroups: Int,
    val completedGroups: Int,
    val totalItems: Int,
    val completedItems: Int,
) {
    val isRest: Boolean get() = totalGroups == 0
    val isDone: Boolean get() = totalGroups > 0 && completedGroups == totalGroups
    val isPartial: Boolean get() = completedGroups in 1 until totalGroups
    val isMissed: Boolean get() = totalGroups > 0 && completedGroups == 0
}
