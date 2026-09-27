package com.planlist.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 打卡日志。
 *
 * 唯一索引 (date, itemId) 是**幂等性的核心**：
 * 通知动作重复触发、用户连点、闹钟重复投递，都只会 UPSERT 同一行，
 * 不可能产生重复打卡记录。
 *
 * 刻意不设到 plan_item 的外键：计划被删除后历史必须保留
 * （"删了计划就抹掉历史"对打卡类应用是不可接受的）。
 */
@Entity(
    tableName = "item_log",
    indices = [
        Index(value = ["date", "itemId"], unique = true),
        Index(value = ["date"]),
        Index(value = ["date", "groupId"]),
    ],
)
data class ItemLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** ISO yyyy-MM-dd，本地时区下的归属日（跨天补打卡靠它落对日期）。 */
    val date: String,
    val groupId: Long,
    val itemId: Long,
    val status: LogStatus,
    /** 真实操作时刻，可能与 date 不同天。 */
    val loggedAt: Long,
)
