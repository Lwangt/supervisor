package com.planlist.app.reminder

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.planlist.app.core.FixedTimeSource
import com.planlist.app.data.db.PlanListDatabase
import com.planlist.app.data.db.entity.PlanGroupEntity
import com.planlist.app.data.db.entity.PlanItemEntity
import com.planlist.app.data.db.entity.PlanKind
import com.planlist.app.data.db.entity.RecurrenceType
import com.planlist.app.data.repo.PlanRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * L2 集成测试：对应 docs/TEST_PLAN.md §3 的 R4 / R5 / R6。
 *
 * 断言的是"真的往 AlarmManager 里排了几个闹钟、排在哪一刻"，
 * 而不是"我们调用了一个方法" —— 后者对提醒类应用没有任何说服力。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderSchedulerTest {

    private lateinit var db: PlanListDatabase
    private lateinit var planRepository: PlanRepository
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager

    /** 固定为 2026-03-05 08:00（Asia/Shanghai），此时当天 12:30 的提醒尚未发生。 */
    private val time = FixedTimeSource(LocalDateTime.of(2026, 3, 5, 8, 0))

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, PlanListDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        planRepository = PlanRepository(db)
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // PlanListApp.onCreate 会真的执行并排下一个"每日补齐"闹钟 —— 那是正确的生产行为，
        // 不是测试污染，所以不去清它（Robolectric 的 getScheduledAlarms() 返回不可变副本，也清不掉）。
        // 改为记录基线，后面一律断言"本次用例新增的闹钟"。
        baselineAlarms = shadowOf(alarmManager).scheduledAlarms.toList()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertDailyGroup(hour: Int, minute: Int = 0, offsetMinutes: Int = 0): Long =
        planRepository.upsertGroupWithItems(
            PlanGroupEntity(
                name = "午餐",
                kind = PlanKind.MEAL,
                timeOfDay = hour * 60 + minute,
                recurrenceType = RecurrenceType.DAILY,
                anchorDate = "2026-01-01",
                reminderOffsetMinutes = offsetMinutes,
            ),
            listOf(PlanItemEntity(groupId = 0L, name = "鸡胸肉", calories = 210)),
        )

    /** setUp 时刻已经存在的闹钟（即 Application.onCreate 排下的那个"每日补齐"）。 */
    private var baselineAlarms: List<ShadowAlarmManager.ScheduledAlarm> = emptyList()

    /** 本次用例真正新增的闹钟。用同一性比较，避免影响到基线里的对象。 */
    private fun newAlarms(): List<ShadowAlarmManager.ScheduledAlarm> =
        shadowOf(alarmManager).scheduledAlarms
            .filterNot { existing -> baselineAlarms.any { it === existing } }

    private fun scheduledCount(): Int = newAlarms().size

    // ------------------------------------------------------------------ R4

    @Test
    fun schedules_one_alarm_per_day_in_the_window() = runTest {
        insertDailyGroup(hour = 12, minute = 30)

        ReminderScheduler(context, planRepository, time).scheduleWindow(days = 7)

        // 今天 12:30 还没到（现在 08:00），加上之后 6 天 = 7 个
        assertEquals(7, scheduledCount())
    }

    @Test
    fun first_alarm_lands_on_the_configured_wall_clock_time() = runTest {
        insertDailyGroup(hour = 12, minute = 30)

        ReminderScheduler(context, planRepository, time).scheduleWindow(days = 7)

        val expected = LocalDateTime.of(2026, 3, 5, 12, 30)
            .atZone(ZoneId.of("Asia/Shanghai"))
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, newAlarms().minOf { it.triggerAtTime })
    }

    @Test
    fun reminder_offset_shifts_the_alarm_earlier() = runTest {
        insertDailyGroup(hour = 12, minute = 30, offsetMinutes = 30)

        ReminderScheduler(context, planRepository, time).scheduleWindow(days = 1)

        val expected = LocalDateTime.of(2026, 3, 5, 12, 0)
            .atZone(ZoneId.of("Asia/Shanghai"))
            .toInstant()
            .toEpochMilli()
        assertEquals(expected, newAlarms().single().triggerAtTime)
    }

    @Test
    fun times_already_passed_today_are_not_scheduled() = runTest {
        // 现在 08:00，组设在 06:00 -> 今天的不排，只排之后 6 天
        insertDailyGroup(hour = 6)

        ReminderScheduler(context, planRepository, time).scheduleWindow(days = 7)

        assertEquals(6, scheduledCount())
    }

    @Test
    fun disabled_group_produces_no_alarms() = runTest {
        val groupId = insertDailyGroup(hour = 12, minute = 30)
        planRepository.setEnabled(groupId, false)

        ReminderScheduler(context, planRepository, time).scheduleWindow(days = 7)

        assertEquals(0, scheduledCount())
    }

    @Test
    fun group_scheduled_only_on_weekdays_produces_five_or_six_alarms() = runTest {
        planRepository.upsertGroupWithItems(
            PlanGroupEntity(
                name = "训练",
                kind = PlanKind.WORKOUT,
                timeOfDay = 19 * 60,
                recurrenceType = RecurrenceType.WEEKDAYS,
                weekdaysMask = 0b0011111, // 周一~周五
                anchorDate = "2026-01-01",
            ),
            listOf(PlanItemEntity(groupId = 0L, name = "卧推")),
        )

        ReminderScheduler(context, planRepository, time).scheduleWindow(days = 7)

        // 2026-03-05 是周四，7 天窗口 = 周四五六日一二三 -> 工作日只有 5 天
        assertEquals(5, scheduledCount())
    }

    @Test
    fun rescheduling_is_idempotent() = runTest {
        insertDailyGroup(hour = 12, minute = 30)
        val scheduler = ReminderScheduler(context, planRepository, time)

        scheduler.scheduleWindow(days = 7)
        val first = scheduledCount()
        scheduler.scheduleWindow(days = 7)

        assertTrue(first > 0)
        assertEquals(first, scheduledCount())
    }

    // ------------------------------------------------------------------ R6

    @Test
    fun cancel_group_removes_all_of_its_alarms() = runTest {
        val groupId = insertDailyGroup(hour = 12, minute = 30)
        val scheduler = ReminderScheduler(context, planRepository, time)

        scheduler.scheduleWindow(days = 7)
        assertTrue(scheduledCount() > 0)

        scheduler.cancelGroup(groupId)

        assertEquals(0, scheduledCount())
    }

    @Test
    fun cancel_all_reminders_clears_every_group() = runTest {
        insertDailyGroup(hour = 12, minute = 30)
        insertDailyGroup(hour = 19)
        val scheduler = ReminderScheduler(context, planRepository, time)

        scheduler.scheduleWindow(days = 7)
        assertTrue(scheduledCount() > 0)

        scheduler.cancelAllReminders()

        assertEquals(0, scheduledCount())
    }

    // ------------------------------------------------------- 每日自续期补齐

    @Test
    fun daily_top_up_is_scheduled_at_next_0005() = runTest {
        val scheduler = ReminderScheduler(context, planRepository, time)

        // 现在是 2026-03-05 08:00，所以下一次补齐应落在 03-06 00:05
        scheduler.scheduleDailyTopUp()

        val expected = LocalDateTime.of(2026, 3, 6, 0, 5)
            .atZone(ZoneId.of("Asia/Shanghai"))
            .toInstant()
            .toEpochMilli()
        assertEquals(1, scheduledCount())
        assertEquals(expected, newAlarms().single().triggerAtTime)
    }

    @Test
    fun daily_top_up_is_idempotent() = runTest {
        val scheduler = ReminderScheduler(context, planRepository, time)

        scheduler.scheduleDailyTopUp()
        scheduler.scheduleDailyTopUp()

        // 重复排程必须覆盖同一个 PendingIntent，而不是堆出两个闹钟
        assertEquals(1, scheduledCount())
    }

    @Test
    fun top_up_and_reminders_coexist() = runTest {
        insertDailyGroup(hour = 12, minute = 30)
        val scheduler = ReminderScheduler(context, planRepository, time)

        scheduler.scheduleWindow(days = 7)
        scheduler.scheduleDailyTopUp()

        assertEquals(8, scheduledCount())
    }

    // ------------------------------------------------------ PendingIntent 唯一性

    @Test
    fun different_dates_produce_distinct_alarm_times_for_the_same_group() = runTest {
        insertDailyGroup(hour = 12, minute = 30)
        ReminderScheduler(context, planRepository, time).scheduleWindow(days = 3)

        val times = newAlarms().map { it.triggerAtTime }.sorted()
        assertEquals(3, times.size)
        assertEquals(3, times.toSet().size)
    }
}
