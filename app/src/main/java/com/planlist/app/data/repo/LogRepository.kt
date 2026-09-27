package com.planlist.app.data.repo

import androidx.room.withTransaction
import com.planlist.app.data.db.PlanListDatabase
import com.planlist.app.data.db.entity.ItemLogEntity
import com.planlist.app.data.db.entity.LogStatus
import com.planlist.app.core.TimeSource
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 打卡日志读写。
 *
 * 所有写操作都走 UPSERT（唯一索引 date+itemId），因此天然幂等：
 * 通知按钮被点两次、闹钟重复投递、用户手抖连点，结果都一致。
 */
class LogRepository(
    private val db: PlanListDatabase,
    private val time: TimeSource,
) {

    private val logDao = db.logDao()

    fun observeForDate(date: LocalDate): Flow<List<ItemLogEntity>> =
        logDao.observeForDate(date.toString())

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<ItemLogEntity>> =
        logDao.observeRange(from.toString(), to.toString())

    suspend fun forDate(date: LocalDate): List<ItemLogEntity> = logDao.forDate(date.toString())

    suspend fun range(from: LocalDate, to: LocalDate): List<ItemLogEntity> =
        logDao.range(from.toString(), to.toString())

    suspend fun all(): List<ItemLogEntity> = logDao.all()

    /**
     * 设置某个条目的状态；[status] 传 null 表示取消打卡。
     *
     * @param date 归属日（跨天补打卡时与今天不同）
     */
    suspend fun setItemStatus(
        date: LocalDate,
        groupId: Long,
        itemId: Long,
        status: LogStatus?,
    ) {
        if (status == null) {
            logDao.clearOne(date.toString(), itemId)
        } else {
            logDao.upsert(
                ItemLogEntity(
                    date = date.toString(),
                    groupId = groupId,
                    itemId = itemId,
                    status = status,
                    loggedAt = time.millis(),
                )
            )
        }
    }

    /** 一键完成整组（通知按钮与长按手势共用）。 */
    suspend fun completeGroup(date: LocalDate, groupId: Long, itemIds: List<Long>) =
        db.withTransaction {
            val now = time.millis()
            val dateStr = date.toString()
            logDao.upsertAll(
                itemIds.map { itemId ->
                    ItemLogEntity(
                        date = dateStr,
                        groupId = groupId,
                        itemId = itemId,
                        status = LogStatus.COMPLETED,
                        loggedAt = now,
                    )
                }
            )
        }

    /** 取消整组打卡。 */
    suspend fun clearGroup(date: LocalDate, groupId: Long) =
        logDao.clearGroup(date.toString(), groupId)

    suspend fun countForDate(date: LocalDate): Int = logDao.countForDate(date.toString())
}
