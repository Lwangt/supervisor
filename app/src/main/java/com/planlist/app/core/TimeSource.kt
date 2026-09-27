package com.planlist.app.core

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 时间源抽象。
 *
 * 所有"现在几点"的读取都必须经过这里，绝不允许在业务代码里直接调用
 * [LocalDateTime.now]。原因：提醒算法与"今日/逾期/待办"判定全部依赖当前时刻，
 * 注入固定时钟才能写出稳定的单元测试（见 docs/TEST_PLAN.md L1）。
 */
interface TimeSource {
    fun now(): LocalDateTime
    fun today(): LocalDate = now().toLocalDate()
    fun zone(): ZoneId
    fun millis(): Long
}

/** 生产实现。 */
class SystemTimeSource(
    private val clock: Clock = Clock.systemDefaultZone(),
) : TimeSource {
    override fun now(): LocalDateTime = LocalDateTime.now(clock)
    override fun zone(): ZoneId = clock.zone
    override fun millis(): Long = clock.millis()
}

/** 测试实现：时刻可控。 */
class FixedTimeSource(
    initial: LocalDateTime,
    private val zoneId: ZoneId = ZoneId.of("Asia/Shanghai"),
) : TimeSource {
    var current: LocalDateTime = initial
        private set

    override fun now(): LocalDateTime = current
    override fun zone(): ZoneId = zoneId
    override fun millis(): Long = current.atZone(zoneId).toInstant().toEpochMilli()

    fun set(value: LocalDateTime) { current = value }
    fun advanceMinutes(minutes: Long) { current = current.plusMinutes(minutes) }
    fun advanceDays(days: Long) { current = current.plusDays(days) }
}
