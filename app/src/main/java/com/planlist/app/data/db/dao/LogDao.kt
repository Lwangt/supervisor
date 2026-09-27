package com.planlist.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.planlist.app.data.db.entity.ItemLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {

    @Query("SELECT * FROM item_log WHERE date = :date")
    fun observeForDate(date: String): Flow<List<ItemLogEntity>>

    @Query("SELECT * FROM item_log WHERE date BETWEEN :from AND :to")
    fun observeRange(from: String, to: String): Flow<List<ItemLogEntity>>

    @Query("SELECT * FROM item_log WHERE date = :date")
    suspend fun forDate(date: String): List<ItemLogEntity>

    @Query("SELECT * FROM item_log WHERE date BETWEEN :from AND :to")
    suspend fun range(from: String, to: String): List<ItemLogEntity>

    @Query("SELECT * FROM item_log")
    suspend fun all(): List<ItemLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: ItemLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(logs: List<ItemLogEntity>)

    @Query("DELETE FROM item_log WHERE date = :date AND itemId = :itemId")
    suspend fun clearOne(date: String, itemId: Long)

    @Query("DELETE FROM item_log WHERE date = :date AND groupId = :groupId")
    suspend fun clearGroup(date: String, groupId: Long)

    @Query("SELECT COUNT(*) FROM item_log WHERE date = :date")
    suspend fun countForDate(date: String): Int

    @Query("DELETE FROM item_log")
    suspend fun clearAll()
}
