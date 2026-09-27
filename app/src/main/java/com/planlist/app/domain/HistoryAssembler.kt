package com.planlist.app.domain

import com.planlist.app.data.db.entity.ItemLogEntity
import com.planlist.app.data.db.entity.LogStatus
import com.planlist.app.data.db.entity.PlanGroupEntity
import com.planlist.app.data.db.entity.PlanItemEntity
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 历史区间的每日完成情况。
 *
 * 与今日页共用同一套 RecurrenceCalculator，因此"历史着色"和"当天看到的计划"
 * 永远一致 —— 不会出现"日历说这天有计划，点进去却没有"的矛盾。
 */
object HistoryAssembler {

    fun summaries(
        from: LocalDate,
        to: LocalDate,
        now: LocalDateTime,
        groups: List<PlanGroupEntity>,
        items: List<PlanItemEntity>,
        logs: List<ItemLogEntity>,
    ): List<DaySummary> {
        if (to.isBefore(from)) return emptyList()

        val itemsByGroup = items.groupBy { it.groupId }
        val statusByItem: Map<Long, LogStatus> = logs.associate { it.itemId to it.status }

        val result = ArrayList<DaySummary>()
        var date = from
        while (!date.isAfter(to)) {
            val applicable = RecurrenceCalculator.occurrencesOn(groups, date)

            var totalGroups = 0
            var doneGroups = 0
            var totalItems = 0
            var doneItems = 0

            for (group in applicable) {
                val groupItems = itemsByGroup[group.id].orEmpty()
                // 没有条目的组不参与统计：否则会凭空拉低完成率
                if (groupItems.isEmpty()) continue
                totalGroups++
                totalItems += groupItems.size
                val done = groupItems.count { statusByItem[it.id] == LogStatus.COMPLETED }
                doneItems += done
                if (done == groupItems.size) doneGroups++
            }

            result.add(DaySummary(date, totalGroups, doneGroups, totalItems, doneItems))
            date = date.plusDays(1)
        }
        return result
    }
}
