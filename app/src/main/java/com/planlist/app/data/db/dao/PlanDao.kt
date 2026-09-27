package com.planlist.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.planlist.app.data.db.entity.PlanGroupEntity
import com.planlist.app.data.db.entity.PlanItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDao {

    @Query("SELECT * FROM plan_group ORDER BY timeOfDay ASC, sortOrder ASC, id ASC")
    fun observeAllGroups(): Flow<List<PlanGroupEntity>>

    @Query("SELECT * FROM plan_group ORDER BY timeOfDay ASC, sortOrder ASC, id ASC")
    suspend fun allGroups(): List<PlanGroupEntity>

    @Query("SELECT * FROM plan_group WHERE enabled = 1 ORDER BY timeOfDay ASC, sortOrder ASC, id ASC")
    suspend fun enabledGroups(): List<PlanGroupEntity>

    @Query("SELECT * FROM plan_group WHERE id = :id")
    suspend fun groupById(id: Long): PlanGroupEntity?

    @Query("SELECT * FROM plan_item ORDER BY groupId ASC, sortOrder ASC, id ASC")
    fun observeAllItems(): Flow<List<PlanItemEntity>>

    @Query("SELECT * FROM plan_item WHERE groupId = :groupId ORDER BY sortOrder ASC, id ASC")
    fun observeItemsOfGroup(groupId: Long): Flow<List<PlanItemEntity>>

    @Query("SELECT * FROM plan_item WHERE groupId = :groupId ORDER BY sortOrder ASC, id ASC")
    suspend fun itemsOfGroup(groupId: Long): List<PlanItemEntity>

    @Query("SELECT * FROM plan_item ORDER BY groupId ASC, sortOrder ASC, id ASC")
    suspend fun allItems(): List<PlanItemEntity>

    @Insert
    suspend fun insertGroup(group: PlanGroupEntity): Long

    @Update
    suspend fun updateGroup(group: PlanGroupEntity)

    @Query("DELETE FROM plan_group WHERE id = :id")
    suspend fun deleteGroupById(id: Long)

    @Insert
    suspend fun insertItem(item: PlanItemEntity): Long

    @Insert
    suspend fun insertItems(items: List<PlanItemEntity>)

    @Update
    suspend fun updateItem(item: PlanItemEntity)

    @Delete
    suspend fun deleteItem(item: PlanItemEntity)

    @Query("DELETE FROM plan_item WHERE groupId = :groupId")
    suspend fun deleteItemsOfGroup(groupId: Long)

    @Query("SELECT COUNT(*) FROM plan_group")
    suspend fun countGroups(): Int
}
