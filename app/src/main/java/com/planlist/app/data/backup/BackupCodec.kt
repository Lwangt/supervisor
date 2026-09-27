package com.planlist.app.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** 备份相关的可读异常：一律用于直接展示给用户，不暴露栈信息。 */
class BackupException(message: String) : Exception(message)

@Serializable
data class BackupFile(
    val format: String = BackupCodec.FORMAT,
    val version: Int = BackupCodec.CURRENT_VERSION,
    val exportedAt: Long = 0L,
    val settings: BackupSettings = BackupSettings(),
    val groups: List<BackupGroup> = emptyList(),
    val logs: List<BackupLog> = emptyList(),
)

@Serializable
data class BackupSettings(
    val catchUpDays: Int = 1,
    val remindersEnabled: Boolean = true,
    val showMacros: Boolean = true,
    val themeMode: String = "DARK",
    val snoozeMinutes: Int = 10,
)

@Serializable
data class BackupGroup(
    val name: String,
    val kind: String,
    val timeOfDay: Int,
    val recurrenceType: String,
    val intervalDays: Int = 1,
    val weekdaysMask: Int = 0,
    val monthDays: String = "",
    val anchorDate: String,
    val endDate: String? = null,
    val enabled: Boolean = true,
    val reminderOffsetMinutes: Int = 0,
    val items: List<BackupItem> = emptyList(),
)

@Serializable
data class BackupItem(
    val name: String,
    val amountText: String = "",
    val calories: Int? = null,
    val proteinG: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val sets: Int? = null,
    val reps: String? = null,
)

/**
 * 日志用"组名 + 条目名"作为业务键，而不是自增 id。
 * 这样备份文件在另一台设备/重装后导入时才有意义（自增 id 换库就变了）。
 */
@Serializable
data class BackupLog(
    val groupName: String,
    val itemName: String,
    val date: String,
    val status: String,
    val loggedAt: Long,
)

/**
 * 备份编解码。
 *
 * 核心约束：**解析失败必须变成可读异常，绝不允许崩溃**。
 * 用户的备份文件可能被微信/网盘改坏，一个 JSON 异常不该让 App 挂掉。
 */
object BackupCodec {

    const val FORMAT = "planlist-backup"
    const val CURRENT_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): BackupFile {
        val parsed = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: Exception) {
            throw BackupException("备份文件无法解析，可能已损坏。(" + (e.message ?: "未知原因") + ")")
        }
        if (parsed.format != FORMAT) {
            throw BackupException("这不是「计划清单」的备份文件。")
        }
        if (parsed.version > CURRENT_VERSION) {
            throw BackupException(
                "备份文件版本为 " + parsed.version + "，比当前 App 支持的 " +
                    CURRENT_VERSION + " 更新，请先升级 App 再导入。"
            )
        }
        return parsed
    }
}
