package com.planlist.app.ui.plan

import com.planlist.app.data.db.entity.PlanGroupEntity
import com.planlist.app.data.db.entity.PlanItemEntity
import com.planlist.app.data.db.entity.PlanKind
import com.planlist.app.data.db.entity.RecurrenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GroupFormTest {

    private val today = LocalDate.of(2026, 3, 5)

    private fun validForm() = GroupForm(
        name = "午餐",
        kind = PlanKind.MEAL,
        hour = 12,
        minute = 30,
        recurrenceType = RecurrenceType.DAILY,
        anchorDate = "2026-01-01",
        items = listOf(ItemForm(name = "鸡胸肉", calories = "210")),
    )

    @Test
    fun new_form_defaults_are_sane() {
        val form = GroupForm.new(today)
        assertEquals(LocalDate.of(2026, 3, 5).toString(), form.anchorDate)
        assertEquals(RecurrenceType.DAILY, form.recurrenceType)
        assertNotNull(form.nameError)
    }

    @Test
    fun valid_form_passes_validation() {
        assertTrue(validForm().isValid)
        assertNull(validForm().nameError)
        assertNull(validForm().recurrenceError)
        assertNull(validForm().itemsError)
    }

    @Test
    fun blank_name_is_rejected() {
        val form = validForm().copy(name = "   ")
        assertFalse(form.isValid)
        assertNotNull(form.nameError)
    }

    @Test
    fun weekdays_with_empty_mask_is_rejected() {
        val form = validForm().copy(recurrenceType = RecurrenceType.WEEKDAYS, weekdaysMask = 0)
        assertFalse(form.isValid)
        assertEquals("请至少选择一个星期", form.recurrenceError)
    }

    @Test
    fun month_days_with_empty_selection_is_rejected() {
        val form = validForm().copy(recurrenceType = RecurrenceType.MONTH_DAYS, monthDays = "")
        assertFalse(form.isValid)
        assertNotNull(form.recurrenceError)
    }

    @Test
    fun every_n_days_with_zero_interval_is_rejected() {
        val form = validForm().copy(recurrenceType = RecurrenceType.EVERY_N_DAYS, intervalDays = 0)
        assertFalse(form.isValid)
        assertNotNull(form.recurrenceError)
    }

    @Test
    fun empty_items_is_rejected() {
        val form = validForm().copy(items = emptyList())
        assertFalse(form.isValid)
        assertEquals("请至少添加一个条目", form.itemsError)
    }

    @Test
    fun blank_item_name_is_rejected() {
        val form = validForm().copy(items = listOf(ItemForm(name = "")))
        assertFalse(form.isValid)
        assertNotNull(form.itemsError)
    }

    @Test
    fun time_of_day_is_computed_from_hour_and_minute() {
        assertEquals(750, validForm().copy(hour = 12, minute = 30).timeOfDay)
        assertEquals(0, validForm().copy(hour = 0, minute = 0).timeOfDay)
        assertEquals(1439, validForm().copy(hour = 23, minute = 59).timeOfDay)
    }

    @Test
    fun out_of_range_time_is_clamped_not_thrown() {
        assertEquals(23 * 60 + 59, validForm().copy(hour = 99, minute = 99).timeOfDay)
    }

    @Test
    fun to_items_parses_numbers_and_ignores_garbage() {
        val form = validForm().copy(
            items = listOf(
                ItemForm(
                    name = "  鸡胸肉  ",
                    amountText = " 150 g ",
                    calories = "210",
                    proteinG = "45.5",
                    carbsG = "",
                    fatG = "abc",
                ),
            ),
        )
        val items = form.toItems()
        assertEquals(1, items.size)
        assertEquals("鸡胸肉", items[0].name)
        assertEquals("150 g", items[0].amountText)
        assertEquals(210, items[0].calories)
        assertEquals(45.5, items[0].proteinG!!, 0.0001)
        assertNull(items[0].carbsG)
        // 非法输入不能被当成 0，必须是 null（未填写）
        assertNull(items[0].fatG)
    }

    @Test
    fun to_entity_trims_name_and_normalizes_blank_end_date() {
        val entity = validForm().copy(name = "  午餐  ", endDate = "  ").toEntity()
        assertEquals("午餐", entity.name)
        assertNull(entity.endDate)
    }

    @Test
    fun to_entity_clamps_interval_for_every_n_days() {
        val entity = validForm().copy(
            recurrenceType = RecurrenceType.EVERY_N_DAYS,
            intervalDays = 0,
        ).toEntity()
        assertEquals(1, entity.intervalDays)
    }

    @Test
    fun from_round_trips_a_group_and_its_items() {
        val group = PlanGroupEntity(
            id = 7L,
            name = "训练",
            kind = PlanKind.WORKOUT,
            timeOfDay = 19 * 60,
            recurrenceType = RecurrenceType.WEEKDAYS,
            weekdaysMask = 0b0010100,
            anchorDate = "2026-01-01",
            endDate = "2026-12-31",
            reminderOffsetMinutes = 30,
        )
        val items = listOf(
            PlanItemEntity(groupId = 7L, name = "卧推", sets = 4, reps = "8-12", sortOrder = 0),
            PlanItemEntity(groupId = 7L, name = "划船", sets = 3, reps = "10", sortOrder = 1),
        )

        val form = GroupForm.from(group, items)

        assertEquals(7L, form.id)
        assertEquals("训练", form.name)
        assertEquals(PlanKind.WORKOUT, form.kind)
        assertEquals(19, form.hour)
        assertEquals(0, form.minute)
        assertEquals(0b0010100, form.weekdaysMask)
        assertEquals("2026-12-31", form.endDate)
        assertEquals(30, form.reminderOffsetMinutes)
        assertEquals(2, form.items.size)
        assertEquals("4", form.items[0].sets)
        assertEquals("8-12", form.items[0].reps)
        assertTrue(form.isValid)
    }

    @Test
    fun from_provides_one_empty_item_when_group_has_none() {
        val group = PlanGroupEntity(
            id = 1L,
            name = "空组",
            kind = PlanKind.MEAL,
            timeOfDay = 600,
            recurrenceType = RecurrenceType.DAILY,
            anchorDate = "2026-01-01",
        )
        val form = GroupForm.from(group, emptyList())
        assertEquals(1, form.items.size)
    }
}
