package com.planlist.app.data.repo

import androidx.room.withTransaction
import com.planlist.app.data.db.PlanListDatabase
import com.planlist.app.data.db.entity.PlanGroupEntity
import com.planlist.app.data.db.entity.PlanItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** 计划组 + 其条目。 */
data class GroupWithItems(
    val group: PlanGroupEntity,
    val items: List<PlanItemEntity>,
)

/**
 * 计划读写。
 *
 * "组 + 条目"的写入必须在一个事务里完成：先删旧条目再插新条目，
 * 中途失败若不留事务边界，会得到"半个计划"这种极难排查的状态。
 */
class PlanRepository(private val db: PlanListDatabase) {

    private val planDao = db.planDao()

    fun observeGroupsWithItems(): Flow<List<GroupWithItems>> =
        combine(
            planDao.observeAllGroups(),
            planDao.observeAllItems(),
        ) { groups, items ->
            val byGroup = items.groupBy { it.groupId }
            groups.map { GroupWithItems(it, byGroup[it.id].orEmpty()) }
        }

    fun observeAllItems(): Flow<List<PlanItemEntity>> = planDao.observeAllItems()

    suspend fun allGroups(): List<PlanGroupEntity> = planDao.allGroups()

    suspend fun enabledGroups(): List<PlanGroupEntity> = planDao.enabledGroups()

    suspend fun allItems(): List<PlanItemEntity> = planDao.allItems()

    suspend fun groupById(id: Long): PlanGroupEntity? = planDao.groupById(id)

    suspend fun itemsOfGroup(groupId: Long): List<PlanItemEntity> = planDao.itemsOfGroup(groupId)

    suspend fun groupsWithItemsOnce(): List<GroupWithItems> {
        val items = planDao.allItems()
        val byGroup = items.groupBy { it.groupId }
        return planDao.allGroups().map { GroupWithItems(it, byGroup[it.id].orEmpty()) }
    }

    /**
     * 新增或更新一个组及其全部条目。
     *
     * 条目一律以"全量替换"方式写入，避免逐条 diff 带来的排序/删除遗漏。
     * 传入条目的 id 会被忽略（总是重新分配），因为它们的稳定身份是 sortOrder。
     *
     * @return 组 id
     */
    suspend fun upsertGroupWithItems(group: PlanGroupEntity, items: List<PlanItemEntity>): Long =
        db.withTransaction {
            val groupId = if (group.id == 0L) {
                planDao.insertGroup(group)
            } else {
                planDao.updateGroup(group)
                group.id
            }
            planDao.deleteItemsOfGroup(groupId)
            planDao.insertItems(
                items.mapIndexed { index, item ->
                    item.copy(id = 0L, groupId = groupId, sortOrder = index)
                }
            )
            groupId
        }

    /** 删除组；条目因外键 CASCADE 一并删除，历史日志刻意保留。 */
    suspend fun deleteGroup(id: Long) = db.withTransaction {
        planDao.deleteGroupById(id)
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        val group = planDao.groupById(id) ?: return
        planDao.updateGroup(group.copy(enabled = enabled))
    }

    suspend fun countGroups(): Int = planDao.countGroups()
}
