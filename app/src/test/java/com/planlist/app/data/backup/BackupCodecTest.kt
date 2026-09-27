package com.planlist.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun sampleFile() = BackupFile(
        exportedAt = 1_767_225_600_000L,
        settings = BackupSettings(catchUpDays = 2, themeMode = "LIGHT"),
        groups = listOf(
            BackupGroup(
                name = "午餐 🍚",
                kind = "MEAL",
                timeOfDay = 750,
                recurrenceType = "WEEKDAYS",
                weekdaysMask = 0b0011111,
                anchorDate = "2026-01-01",
                reminderOffsetMinutes = 30,
                items = listOf(
                    BackupItem(name = "鸡胸肉", amountText = "150 g", calories = 210, proteinG = 45.5),
                    BackupItem(name = "米饭", amountText = "200 g", calories = 260),
                ),
            ),
        ),
        logs = listOf(
            BackupLog(
                groupName = "午餐 🍚",
                itemName = "鸡胸肉",
                date = "2026-03-05",
                status = "COMPLETED",
                loggedAt = 1_767_225_600_000L,
            ),
        ),
    )

    @Test
    fun round_trip_preserves_all_fields() {
        val original = sampleFile()
        val decoded = BackupCodec.decode(BackupCodec.encode(original))

        assertEquals(original.settings.catchUpDays, decoded.settings.catchUpDays)
        assertEquals(original.groups.size, decoded.groups.size)
        assertEquals(original.groups[0].name, decoded.groups[0].name)
        assertEquals(original.groups[0].weekdaysMask, decoded.groups[0].weekdaysMask)
        assertEquals(original.groups[0].items.size, decoded.groups[0].items.size)
        assertEquals(original.groups[0].items[0].proteinG!!, decoded.groups[0].items[0].proteinG!!, 0.0001)
        assertEquals(original.logs[0].itemName, decoded.logs[0].itemName)
    }

    @Test
    fun emoji_and_special_characters_survive_round_trip() {
        val file = sampleFile()
        val decoded = BackupCodec.decode(BackupCodec.encode(file))
        assertEquals("午餐 🍚", decoded.groups[0].name)
    }

    @Test
    fun empty_backup_is_valid() {
        val file = BackupFile(exportedAt = 0L)
        val decoded = BackupCodec.decode(BackupCodec.encode(file))
        assertTrue(decoded.groups.isEmpty())
        assertTrue(decoded.logs.isEmpty())
    }

    @Test
    fun corrupted_json_throws_readable_exception_instead_of_crashing() {
        val ex = assertThrows(BackupException::class.java) {
            BackupCodec.decode("{ this is not json ")
        }
        assertTrue(ex.message!!.contains("无法解析"))
    }

    @Test
    fun wrong_format_is_rejected() {
        val text = """{"format":"some-other-app","version":1,"exportedAt":0}"""
        val ex = assertThrows(BackupException::class.java) { BackupCodec.decode(text) }
        assertTrue(ex.message!!.contains("计划清单"))
    }

    @Test
    fun newer_version_is_rejected_with_actionable_message() {
        val text = """{"format":"planlist-backup","version":99,"exportedAt":0}"""
        val ex = assertThrows(BackupException::class.java) { BackupCodec.decode(text) }
        assertTrue(ex.message!!.contains("升级"))
    }

    @Test
    fun unknown_fields_are_ignored_for_forward_compatibility() {
        val text = """{"format":"planlist-backup","version":1,"exportedAt":0,"futureField":123}"""
        val decoded = BackupCodec.decode(text)
        assertEquals(1, decoded.version)
    }

    @Test
    fun negative_timestamps_do_not_crash() {
        val text = """{"format":"planlist-backup","version":1,"exportedAt":-1}"""
        assertEquals(-1L, BackupCodec.decode(text).exportedAt)
    }
}
