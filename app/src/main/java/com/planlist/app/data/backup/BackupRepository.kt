package com.planlist.app.data.backup

import androidx.room.withTransaction
import com.planlist.app.core.TimeSource
import com.planlist.app.data.db.PlanListDatabase
import com.planlist.app.data.db.entity.ItemLogEntity
import com.planlist.app.data.db.entity.LogStatus
import com.planlist.app.data.db.entity.PlanGroupEntity
import com.planlist.app.data.db.entity.PlanItemEntity
import com.planlist.app.data.db.entity.PlanKind
import com.planlist.app.data.db.entity.RecurrenceType
import com.planlist.app.data.repo.SettingsRepository
import com.planlist.app.data.repo.ThemeMode
import kotlinx.coroutines.flow.first

/** 本地 JSON 备份的导入导出（纯本地文件，全程不联网）。 */
class BackupRepository(
    private val db: PlanListDatabase,
    private val settings: SettingsRepository,
    private val time: TimeSource,
) {

    data class ImportResult(
        val groupsCreated: Int,
        val groupsUpdated: Int,
        val logsImported: Int,
    )

    // ---------------------------------------------------------------- 导出

    suspend fun export(): String {
        val planDao = db.planDao()
        val groups = planDao.allGroups()
        val items = planDao.allItems()
        val logs = db.logDao().all()

        val itemsByGroup = items.groupBy { it.groupId }
        val groupNameById = groups.associate { it.id to it.name }
        val itemNameById = items.associate { it.id to it.name }
        val current = settings.settings.first()

        val file = BackupFile(
            exportedAt = time.millis(),
            settings = BackupSettings(
                catchUpDays = current.catchUpDays,
                remindersEnabled = current.remindersEnabled,
                showMacros = current.showMacros,
                themeMode = current.themeMode.name,
                snoozeMinutes = current.snoozeMinutes,
            ),
            groups = groups.map { group ->
                BackupGroup(
                    name = group.name,
                    kind = group.kind.name,
                    timeOfDay = group.timeOfDay,
                    recurrenceType = group.recurrenceType.name,
                    intervalDays = group.intervalDays,
                    weekdaysMask = group.weekdaysMask,
                    monthDays = group.monthDays,
                    anchorDate = group.anchorDate,
                    endDate = group.endDate,
                    enabled = group.enabled,
                    reminderOffsetMinutes = group.reminderOffsetMinutes,
                    items = itemsByGroup[group.id].orEmpty().map { item ->
                        BackupItem(
                            name = item.name,
                            amountText = item.amountText,
                            calories = item.calories,
                            proteinG = item.proteinG,
                            carbsG = item.carbsG,
                            fatG = item.fatG,
                            sets = item.sets,
                            reps = item.reps,
                        )
                    },
                )
            },
            // 计划已被删除的历史日志无法还原归属，导出时跳过（已在文档中说明）
            logs = logs.mapNotNull { log ->
                val groupName = groupNameById[log.groupId] ?: return@mapNotNull null
                val itemName = itemNameById[log.itemId] ?: return@mapNotNull null
                BackupLog(
                    groupName = groupName,
                    itemName = itemName,
                    date = log.date,
                    status = log.status.name,
                    loggedAt = log.loggedAt,
                )
            },
        )
        return BackupCodec.encode(file)
    }

    // ---------------------------------------------------------------- 导入

    /**
     * 导入备份。
     *
     * 采用**按业务键合并**而不是清库重建：
     * - 计划组以 (name, kind, timeOfDay) 匹配 → 命中则更新，未命中则新建
     * - 日志以 (date, 解析后的 itemId) UPSERT → 重复导入不会产生重复记录
     *
     * 这样"手滑点两次导入"不会得到两套一模一样的计划。
     */
    suspend fun import(text: String): ImportResult {
        val file = BackupCodec.decode(text)   // 解析失败会抛出可读的 BackupException

        var created = 0
        var updated = 0
        var logsImported = 0

        db.withTransaction {
            val planDao = db.planDao()
            val logDao = db.logDao()

            val groupKeyToId = mutableMapOf<String, Long>()
            val itemKeyToId = mutableMapOf<String, Long>()

            for (backupGroup in file.groups) {
                val kind = runCatching { PlanKind.valueOf(backupGroup.kind) }.getOrDefault(PlanKind.MEAL)
                val recurrence = runCatching { RecurrenceType.valueOf(backupGroup.recurrenceType) }
                    .getOrDefault(RecurrenceType.DAILY)

                val existing = planDao.allGroups().firstOrNull {
                    it.name == backupGroup.name && it.kind == kind && it.timeOfDay == backupGroup.timeOfDay
                }

                val entity = PlanGroupEntity(
                    id = existing?.id ?: 0L,
                    name = backupGroup.name,
                    kind = kind,
                    timeOfDay = backupGroup.timeOfDay,
                    recurrenceType = recurrence,
                    intervalDays = backupGroup.intervalDays,
                    weekdaysMask = backupGroup.weekdaysMask,
                    monthDays = backupGroup.monthDays,
                    anchorDate = backupGroup.anchorDate,
                    endDate = backupGroup.endDate,
                    enabled = backupGroup.enabled,
                    reminderOffsetMinutes = backupGroup.reminderOffsetMinutes,
                )

                val groupId = if (existing == null) {
                    created++
                    planDao.insertGroup(entity)
                } else {
                    updated++
                    planDao.updateGroup(entity)
                    existing.id
                }

                planDao.deleteItemsOfGroup(groupId)
                if (backupGroup.items.isNotEmpty()) {
                    planDao.insertItems(
                        backupGroup.items.mapIndexed { index, backupItem ->
                            PlanItemEntity(
                                groupId = groupId,
                                name = backupItem.name,
                                amountText = backupItem.amountText,
                                calories = backupItem.calories,
                                proteinG = backupItem.proteinG,
                                carbsG = backupItem.carbsG,
                                fatG = backupItem.fatG,
                                sets = backupItem.sets,
                                reps = backupItem.reps,
                                sortOrder = index,
                            )
                        }
                    )
                }

                groupKeyToId[backupGroup.name] = groupId
                val inserted = planDao.itemsOfGroup(groupId)
                backupGroup.items.forEachIndexed { index, backupItem ->
                    val newId = inserted.getOrNull(index)?.id
                    if (newId != null && newId > 0L) {
                        itemKeyToId[itemKey(backupGroup.name, backupItem.name)] = newId
                    }
                }
            }

            for (backupLog in file.logs) {
                val groupId = groupKeyToId[backupLog.groupName] ?: continue
                val itemId = itemKeyToId[itemKey(backupLog.groupName, backupLog.itemName)] ?: continue
                val status = runCatching { LogStatus.valueOf(backupLog.status) }
                    .getOrDefault(LogStatus.COMPLETED)

                logDao.upsert(
                    ItemLogEntity(
                        date = backupLog.date,
                        groupId = groupId,
                        itemId = itemId,
                        status = status,
                        loggedAt = backupLog.loggedAt,
                    )
                )
                logsImported++
            }
        }

        // DataStore 不在 Room 事务内，放在事务之后应用
        runCatching {
            settings.setCatchUpDays(file.settings.catchUpDays)
            settings.setRemindersEnabled(file.settings.remindersEnabled)
            settings.setShowMacros(file.settings.showMacros)
            settings.setSnoozeMinutes(file.settings.snoozeMinutes)
            runCatching { ThemeMode.valueOf(file.settings.themeMode) }
                .getOrNull()
                ?.let { settings.setThemeMode(it) }
        }

        return ImportResult(created, updated, logsImported)
    }

    /** 业务键：组名 + 条目名。故意不用 id，因为重装后自增 id 会变。 */
    private fun itemKey(groupName: String, itemName: String): String = groupName + "||" + itemName
}
