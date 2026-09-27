package com.planlist.app.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 组内条目：一份食物或一个动作。
 *
 * 宏量字段为 null 表示"未填写"，与显式填写 0 语义不同：
 * 只要一个组里**任一**条目填了宏量，该组就按宏量模式展示；
 * 全部为 null 则整组降级为"完成计数"模式。详见 docs/DATA_MODEL.md §4。
 */
@Entity(
    tableName = "plan_item",
    foreignKeys = [
        ForeignKey(
            entity = PlanGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["groupId"])],
)
data class PlanItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val groupId: Long,
    val name: String,
    val amountText: String = "",
    val calories: Int? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val sets: Int? = null,
    val reps: String? = null,
    val sortOrder: Int = 0,
)
