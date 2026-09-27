package com.planlist.app.domain

import com.planlist.app.data.db.entity.PlanGroupEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 一天内的分钟数上限（23:59）。 */
private const val MAX_MINUTE_OF_DAY = 23 * 60 + 59

/**
 * 组设定的时刻。
 *
 * 脏数据保护：timeOfDay 越界时钳制到合法区间，绝不抛 IllegalArgumentException。
 * 数据库里出现脏数据（例如历史迁移或手工改库）不能让整个今日页崩溃。
 */
fun PlanGroupEntity.dueTime(): LocalTime {
    val clamped = timeOfDay.coerceIn(0, MAX_MINUTE_OF_DAY)
    return LocalTime.of(clamped / 60, clamped % 60)
}

/** 该组在指定日期的应做时刻。 */
fun PlanGroupEntity.dueDateTime(date: LocalDate): LocalDateTime =
    LocalDateTime.of(date, dueTime())
