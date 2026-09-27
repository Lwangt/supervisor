package com.planlist.app.domain

import com.planlist.app.data.db.entity.PlanItemEntity
import kotlin.math.roundToInt

/** 宏量营养汇总值。 */
data class Macros(
    val calories: Int = 0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
) {
    operator fun plus(other: Macros): Macros = Macros(
        calories = calories + other.calories,
        proteinG = proteinG + other.proteinG,
        carbsG = carbsG + other.carbsG,
        fatG = fatG + other.fatG,
    )

    companion object {
        val ZERO = Macros()
    }
}

/**
 * 宏量计算与"是否启用宏量模式"的判定。
 *
 * 关键语义：**未填 != 0**。
 * - 只要组内任一条目填了宏量，整组进入宏量模式，未填的按 0 参与求和
 *   （UI 会以 "~" 前缀提示数值偏低，避免用户误以为精确）。
 * - 全部未填则整组降级为"完成组数/条目数"计数模式，与 Macro7 行为一致。
 */
object MacroMath {

    /** 求和：null 按 0 计。 */
    fun sum(items: List<PlanItemEntity>): Macros = items.fold(Macros.ZERO) { acc, item ->
        Macros(
            calories = acc.calories + (item.calories ?: 0),
            proteinG = acc.proteinG + (item.proteinG ?: 0.0),
            carbsG = acc.carbsG + (item.carbsG ?: 0.0),
            fatG = acc.fatG + (item.fatG ?: 0.0),
        )
    }

    /** 组内是否存在任何已填写的宏量。显式填 0 也算"已填"。 */
    fun hasAnyMacro(items: List<PlanItemEntity>): Boolean = items.any {
        it.calories != null || it.proteinG != null || it.carbsG != null || it.fatG != null
    }

    /**
     * 进度环完成度，范围 0f..1f。
     *
     * 分母为 0 时返回 0f —— **绝不返回 NaN**（NaN 会让 Compose 进度环整块消失，
     * 这是"计划里还没填条目"时最常见的崩溃式观感问题）。
     */
    fun progress(
        eaten: Macros,
        planned: Macros,
        completedCount: Int,
        totalCount: Int,
        useMacroMode: Boolean,
    ): Float = when {
        useMacroMode && planned.calories > 0 ->
            (eaten.calories.toFloat() / planned.calories.toFloat()).coerceIn(0f, 1f)

        totalCount > 0 -> (completedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)

        else -> 0f
    }

    /** 展示用四舍五入。 */
    fun round(value: Double): Int = value.roundToInt()
}
