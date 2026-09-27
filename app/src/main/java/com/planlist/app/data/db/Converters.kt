package com.planlist.app.data.db

import androidx.room.TypeConverter
import com.planlist.app.data.db.entity.LogStatus
import com.planlist.app.data.db.entity.PlanKind
import com.planlist.app.data.db.entity.RecurrenceType

/**
 * 枚举以名称字符串存库，而不是序号。
 * 理由：以后在枚举中间插入新值时，序号会错位导致历史数据被静默解释成另一种类型。
 */
class Converters {
    @TypeConverter
    fun fromPlanKind(value: PlanKind): String = value.name

    @TypeConverter
    fun toPlanKind(value: String): PlanKind =
        runCatching { PlanKind.valueOf(value) }.getOrDefault(PlanKind.MEAL)

    @TypeConverter
    fun fromRecurrenceType(value: RecurrenceType): String = value.name

    @TypeConverter
    fun toRecurrenceType(value: String): RecurrenceType =
        runCatching { RecurrenceType.valueOf(value) }.getOrDefault(RecurrenceType.DAILY)

    @TypeConverter
    fun fromLogStatus(value: LogStatus): String = value.name

    @TypeConverter
    fun toLogStatus(value: String): LogStatus =
        runCatching { LogStatus.valueOf(value) }.getOrDefault(LogStatus.COMPLETED)
}
