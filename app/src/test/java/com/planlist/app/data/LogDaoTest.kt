package com.planlist.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.planlist.app.core.FixedTimeSource
import com.planlist.app.data.db.PlanListDatabase
import com.planlist.app.data.db.entity.ItemLogEntity
import com.planlist.app.data.db.entity.LogStatus
import com.planlist.app.data.db.entity.PlanGroupEntity
import com.planlist.app.data.db.entity.PlanItemEntity
import com.planlist.app.data.db.entity.PlanKind
import com.planlist.app.data.db.entity.RecurrenceType
import com.planlist.app.data.repo.LogRepository
import com.planlist.app.data.repo.PlanRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * L2 集成测试：对应 docs/TEST_PLAN.md §3 的 R1 / R2 / R3。
 *
 * 重点验证"只有真机/真数据库才能暴露"的约束：
 * 唯一索引幂等性、外键级联、以及"删计划不删历史"这条反范式设计。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogDaoTest {

    private lateinit var db: PlanListDatabase
    private lateinit var planRepository: PlanRepository
    private lateinit var logRepository: LogRepository

    private val date = LocalDate.of(2026, 3, 5)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PlanListDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        planRepository = PlanRepository(db)
        logRepository = LogRepository(
            db,
            FixedTimeSource(LocalDateTime.of(2026, 3, 5, 12, 0)),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertSampleGroup(): Pair<Long, List<Long>> {
        val groupId = planRepository.upsertGroupWithItems(
            PlanGroupEntity(
                name = "午餐",
                kind = PlanKind.MEAL,
                timeOfDay = 750,
                recurrenceType = RecurrenceType.DAILY,
                anchorDate = "2026-01-01",
            ),
            listOf(
                PlanItemEntity(groupId = 0L, name = "鸡胸肉", calories = 210),
                PlanItemEntity(groupId = 0L, name = "米饭", calories = 260),
            ),
        )
        return groupId to planRepository.itemsOfGroup(groupId).map { it.id }
    }

    // ------------------------------------------------------------------ R1

    @Test
    fun upsert_is_idempotent_on_date_and_item() = runTest {
        val (groupId, itemIds) = insertSampleGroup()

        repeat(3) {
            logRepository.setItemStatus(date, groupId, itemIds[0], LogStatus.COMPLETED)
        }

        val logs = logRepository.forDate(date)
        assertEquals(1, logs.size)
        assertEquals(LogStatus.COMPLETED, logs[0].status)
    }

    @Test
    fun upsert_overwrites_status_instead_of_duplicating() = runTest {
        val (groupId, itemIds) = insertSampleGroup()

        logRepository.setItemStatus(date, groupId, itemIds[0], LogStatus.COMPLETED)
        logRepository.setItemStatus(date, groupId, itemIds[0], LogStatus.SKIPPED)

        val logs = logRepository.forDate(date)
        assertEquals(1, logs.size)
        assertEquals(LogStatus.SKIPPED, logs[0].status)
    }

    @Test
    fun passing_null_status_clears_the_entry() = runTest {
        val (groupId, itemIds) = insertSampleGroup()

        logRepository.setItemStatus(date, groupId, itemIds[0], LogStatus.COMPLETED)
        assertEquals(1, logRepository.forDate(date).size)

        logRepository.setItemStatus(date, groupId, itemIds[0], null)
        assertEquals(0, logRepository.forDate(date).size)
    }

    @Test
    fun complete_group_writes_all_items_atomically() = runTest {
        val (groupId, itemIds) = insertSampleGroup()

        logRepository.completeGroup(date, groupId, itemIds)

        val logs = logRepository.forDate(date)
        assertEquals(2, logs.size)
        assertEquals(2, logRepository.countForDate(date))
    }

    @Test
    fun complete_group_twice_does_not_duplicate() = runTest {
        val (groupId, itemIds) = insertSampleGroup()

        logRepository.completeGroup(date, groupId, itemIds)
        logRepository.completeGroup(date, groupId, itemIds)

        assertEquals(2, logRepository.countForDate(date))
    }

    // ------------------------------------------------------------------ R2

    @Test
    fun deleting_group_cascades_items_but_keeps_logs() = runTest {
        val (groupId, itemIds) = insertSampleGroup()
        logRepository.completeGroup(date, groupId, itemIds)
        assertEquals(2, logRepository.countForDate(date))

        planRepository.deleteGroup(groupId)

        assertEquals(0, planRepository.itemsOfGroup(groupId).size)
        // 历史必须保留：删计划不能抹掉已经打过的卡
        assertEquals(2, logRepository.countForDate(date))
    }

    @Test
    fun clearing_one_group_does_not_touch_another() = runTest {
        val (groupIdA, itemIdsA) = insertSampleGroup()
        val (groupIdB, itemIdsB) = insertSampleGroup()

        logRepository.completeGroup(date, groupIdA, itemIdsA)
        logRepository.completeGroup(date, groupIdB, itemIdsB)
        assertEquals(4, logRepository.countForDate(date))

        logRepository.clearGroup(date, groupIdA)

        assertEquals(2, logRepository.countForDate(date))
        assertEquals(0, logRepository.forDate(date).count { it.groupId == groupIdA })
    }

    @Test
    fun logs_are_scoped_to_their_own_date() = runTest {
        val (groupId, itemIds) = insertSampleGroup()
        logRepository.completeGroup(date, groupId, itemIds)

        assertEquals(0, logRepository.countForDate(date.plusDays(1)))
        assertEquals(2, logRepository.countForDate(date))
    }

    @Test
    fun range_query_returns_only_the_requested_window() = runTest {
        val (groupId, itemIds) = insertSampleGroup()
        logRepository.completeGroup(date, groupId, itemIds)
        logRepository.completeGroup(date.plusDays(5), groupId, itemIds)

        val range = logRepository.range(date, date.plusDays(2))
        assertEquals(2, range.size)
    }

    // --------------------------------------------------- 计划写入的事务性

    @Test
    fun upsert_group_replaces_items_instead_of_appending() = runTest {
        val (groupId, _) = insertSampleGroup()
        val existing = planRepository.groupById(groupId)!!

        planRepository.upsertGroupWithItems(
            existing,
            listOf(PlanItemEntity(groupId = groupId, name = "只留一个")),
        )

        val items = planRepository.itemsOfGroup(groupId)
        assertEquals(1, items.size)
        assertEquals("只留一个", items[0].name)
        assertEquals(0, items[0].sortOrder)
    }

    @Test
    fun sort_order_follows_list_position() = runTest {
        val groupId = planRepository.upsertGroupWithItems(
            PlanGroupEntity(
                name = "训练",
                kind = PlanKind.WORKOUT,
                timeOfDay = 1140,
                recurrenceType = RecurrenceType.WEEKDAYS,
                weekdaysMask = 0b0011111,
                anchorDate = "2026-01-01",
            ),
            listOf(
                PlanItemEntity(groupId = 0L, name = "第一个"),
                PlanItemEntity(groupId = 0L, name = "第二个"),
                PlanItemEntity(groupId = 0L, name = "第三个"),
            ),
        )
        val names = planRepository.itemsOfGroup(groupId).map { it.name }
        assertEquals(listOf("第一个", "第二个", "第三个"), names)
    }

    @Test
    fun disabling_group_keeps_it_out_of_enabled_query() = runTest {
        val (groupId, _) = insertSampleGroup()
        planRepository.setEnabled(groupId, false)

        assertEquals(0, planRepository.enabledGroups().size)
        assertEquals(1, planRepository.allGroups().size)
    }

    @Test
    fun group_by_id_returns_null_for_missing_id() = runTest {
        assertNull(planRepository.groupById(99999L))
    }
}
