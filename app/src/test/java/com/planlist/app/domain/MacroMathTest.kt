package com.planlist.app.domain

import com.planlist.app.data.db.entity.PlanItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroMathTest {

    private fun item(
        name: String = "食物",
        calories: Int? = null,
        proteinG: Double? = null,
        carbsG: Double? = null,
        fatG: Double? = null,
    ) = PlanItemEntity(
        groupId = 1L,
        name = name,
        calories = calories,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
    )

    @Test
    fun sums_all_filled_values() {
        val items = listOf(
            item(calories = 200, proteinG = 30.0, carbsG = 10.0, fatG = 5.0),
            item(calories = 150, proteinG = 10.0, carbsG = 20.0, fatG = 2.0),
        )
        val result = MacroMath.sum(items)
        assertEquals(350, result.calories)
        assertEquals(40.0, result.proteinG, 0.001)
        assertEquals(30.0, result.carbsG, 0.001)
        assertEquals(7.0, result.fatG, 0.001)
    }

    @Test
    fun null_values_are_treated_as_zero_in_sum() {
        val items = listOf(item(calories = 200), item(calories = null))
        assertEquals(200, MacroMath.sum(items).calories)
    }

    @Test
    fun empty_list_sums_to_zero_and_has_no_macro() {
        assertEquals(Macros.ZERO, MacroMath.sum(emptyList()))
        assertFalse(MacroMath.hasAnyMacro(emptyList()))
    }

    @Test
    fun has_any_macro_is_false_when_all_null() {
        assertFalse(MacroMath.hasAnyMacro(listOf(item(), item())))
    }

    @Test
    fun has_any_macro_is_true_when_any_field_is_set() {
        assertTrue(MacroMath.hasAnyMacro(listOf(item(), item(proteinG = 10.0))))
    }

    @Test
    fun explicit_zero_calories_still_counts_as_filled() {
        // 用户明确填 0（例如黑咖啡）应进入宏量模式，而不是被当成"没填"
        assertTrue(MacroMath.hasAnyMacro(listOf(item(calories = 0))))
    }

    @Test
    fun decimals_accumulate_correctly() {
        val items = listOf(item(proteinG = 0.1), item(proteinG = 0.2))
        assertEquals(0.3, MacroMath.sum(items).proteinG, 0.0001)
    }

    @Test
    fun progress_uses_macros_when_planned_calories_positive() {
        val progress = MacroMath.progress(
            eaten = Macros(calories = 500),
            planned = Macros(calories = 1000),
            completedCount = 0,
            totalCount = 4,
            useMacroMode = true,
        )
        assertEquals(0.5f, progress, 0.001f)
    }

    @Test
    fun progress_falls_back_to_item_count_when_no_macros() {
        val progress = MacroMath.progress(
            eaten = Macros.ZERO,
            planned = Macros.ZERO,
            completedCount = 2,
            totalCount = 4,
            useMacroMode = false,
        )
        assertEquals(0.5f, progress, 0.001f)
    }

    @Test
    fun progress_never_returns_nan_when_denominator_is_zero() {
        val progress = MacroMath.progress(
            eaten = Macros.ZERO,
            planned = Macros.ZERO,
            completedCount = 0,
            totalCount = 0,
            useMacroMode = true,
        )
        assertFalse(progress.isNaN())
        assertEquals(0f, progress, 0.001f)
    }

    @Test
    fun progress_is_clamped_to_one_when_over_eaten() {
        val progress = MacroMath.progress(
            eaten = Macros(calories = 3000),
            planned = Macros(calories = 1000),
            completedCount = 5,
            totalCount = 4,
            useMacroMode = true,
        )
        assertEquals(1f, progress, 0.001f)
    }

    @Test
    fun macros_plus_operator_accumulates() {
        val a = Macros(100, 10.0, 20.0, 5.0)
        val b = Macros(50, 5.0, 10.0, 1.0)
        val sum = a + b
        assertEquals(150, sum.calories)
        assertEquals(15.0, sum.proteinG, 0.001)
        assertEquals(30.0, sum.carbsG, 0.001)
        assertEquals(6.0, sum.fatG, 0.001)
    }
}
