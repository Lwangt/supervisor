package com.planlist.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 计划组。
 *
 * 注意：某个组"是否落在某一天"**不存库**，由纯函数
 * [com.planlist.app.domain.RecurrenceCalculator.occursOn] 实时计算。
 * 这样改规则能立刻对历史与未来生效，且不需要任何"重新生成日程"的操作。
 */
@Entity(
    tableName = "plan_group",
    indices = [Index(value = ["enabled", "timeOfDay"])],
)
data class PlanGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val kind: PlanKind,
    val timeOfDay: Int,
    val recurrenceType: RecurrenceType,
    val intervalDays: Int = 1,
    val weekdaysMask: Int = 0,
    val monthDays: String = "",
    val anchorDate: String,
    val endDate: String? = null,
    val enabled: Boolean = true,
    val reminderOffsetMinutes: Int = 0,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

// ---- 派生属性放在类外，避免 Room 把它们当成数据库列 ----

val PlanGroupEntity.hour: Int get() = timeOfDay / 60
val PlanGroupEntity.minute: Int get() = timeOfDay % 60

/** 有效性下限保护：脏数据（intervalDays < 1）按 1 处理，绝不崩溃。 */
val PlanGroupEntity.safeIntervalDays: Int get() = if (intervalDays < 1) 1 else intervalDays

val PlanGroupEntity.anchor: LocalDate get() = LocalDate.parse(anchorDate)
val PlanGroupEntity.end: LocalDate? get() = endDate?.let { LocalDate.parse(it) }

/** "每天 12:30" 这类人类可读摘要，计划列表直接显示。 */
fun PlanGroupEntity.recurrenceSummary(): String = when (recurrenceType) {
    RecurrenceType.DAILY -> "每天"
    RecurrenceType.EVERY_N_DAYS -> if (safeIntervalDays == 1) "每天" else "每 " + safeIntervalDays + " 天"
    RecurrenceType.WEEKDAYS -> {
        val labels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val picked = labels.filterIndexed { i, _ -> (weekdaysMask shr i) and 1 == 1 }
        when {
            picked.isEmpty() -> "未选择"
            picked.size == 7 -> "每天"
            picked == listOf("周一", "周二", "周三", "周四", "周五") -> "工作日"
            picked == listOf("周六", "周日") -> "周末"
            else -> picked.joinToString("、")
        }
    }
    RecurrenceType.MONTH_DAYS -> {
        val days = parseMonthDays(monthDays)
        if (days.isEmpty()) "未选择" else "每月 " + days.sorted().joinToString(",") + " 日"
    }
    RecurrenceType.ONCE -> "仅一次 " + anchorDate
}

/** 宽松解析 "1, 15 ,31" —— 忽略空格与非法项，永不抛异常。 */
fun parseMonthDays(raw: String): Set<Int> =
    raw.split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it in 1..31 }
        .toSet()
