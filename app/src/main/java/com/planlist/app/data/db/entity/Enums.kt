package com.planlist.app.data.db.entity

/** 计划组类型：饮食 / 运动。 */
enum class PlanKind {
    MEAL,
    WORKOUT,
}

/**
 * 重复规则，与 Macro7 的 5 种规则一一对应。
 */
enum class RecurrenceType {
    /** 每天 */
    DAILY,

    /** 每 N 天（以 anchorDate 为相位基准） */
    EVERY_N_DAYS,

    /** 每周指定星期几（weekdaysMask） */
    WEEKDAYS,

    /** 每月指定日期（monthDays） */
    MONTH_DAYS,

    /** 仅一次（anchorDate 当天） */
    ONCE,
}

/** 打卡状态。 */
enum class LogStatus {
    COMPLETED,
    SKIPPED,
}
