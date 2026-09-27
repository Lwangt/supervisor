package com.planlist.app.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.planlist.app.AppContainer
import com.planlist.app.data.db.entity.PlanGroupEntity
import com.planlist.app.data.db.entity.PlanItemEntity
import com.planlist.app.data.db.entity.PlanKind
import com.planlist.app.data.db.entity.RecurrenceType
import com.planlist.app.data.db.entity.parseMonthDays
import com.planlist.app.data.repo.GroupWithItems
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 编辑表单里的一个条目。数字全部用字符串保存，避免输入中间态被强制转换。 */
data class ItemForm(
    val name: String = "",
    val amountText: String = "",
    val calories: String = "",
    val proteinG: String = "",
    val carbsG: String = "",
    val fatG: String = "",
    val sets: String = "",
    val reps: String = "",
)

/**
 * 计划编辑表单。
 *
 * 校验逻辑全部放在这里（纯数据，可单元测试），UI 只负责展示错误文案。
 * 对应用例见 docs/TEST_PLAN.md §2.6。
 */
data class GroupForm(
    val id: Long = 0L,
    val name: String = "",
    val kind: PlanKind = PlanKind.MEAL,
    val hour: Int = 12,
    val minute: Int = 0,
    val recurrenceType: RecurrenceType = RecurrenceType.DAILY,
    val intervalDays: Int = 2,
    val weekdaysMask: Int = WEEKDAYS_MON_TO_FRI,
    val monthDays: String = "1",
    val anchorDate: String = LocalDate.now().toString(),
    val endDate: String = "",
    val reminderOffsetMinutes: Int = 0,
    val enabled: Boolean = true,
    val items: List<ItemForm> = listOf(ItemForm()),
) {
    val nameError: String? get() = if (name.isBlank()) "请输入计划名称" else null

    val recurrenceError: String? get() = when {
        recurrenceType == RecurrenceType.WEEKDAYS && weekdaysMask == 0 -> "请至少选择一个星期"
        recurrenceType == RecurrenceType.MONTH_DAYS && parseMonthDays(monthDays).isEmpty() -> "请至少选择一个日期"
        recurrenceType == RecurrenceType.EVERY_N_DAYS && intervalDays < 1 -> "间隔天数至少为 1"
        else -> null
    }

    val itemsError: String? get() = when {
        items.isEmpty() -> "请至少添加一个条目"
        items.any { it.name.isBlank() } -> "条目名称不能为空"
        else -> null
    }

    val isValid: Boolean get() = nameError == null && recurrenceError == null && itemsError == null

    val timeOfDay: Int get() = hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59)

    fun toEntity(): PlanGroupEntity = PlanGroupEntity(
        id = id,
        name = name.trim(),
        kind = kind,
        timeOfDay = timeOfDay,
        recurrenceType = recurrenceType,
        intervalDays = intervalDays.coerceAtLeast(1),
        weekdaysMask = weekdaysMask,
        monthDays = monthDays,
        anchorDate = anchorDate,
        endDate = endDate.ifBlank { null },
        enabled = enabled,
        reminderOffsetMinutes = reminderOffsetMinutes,
    )

    fun toItems(): List<PlanItemEntity> = items.mapIndexed { index, form ->
        PlanItemEntity(
            groupId = id,
            name = form.name.trim(),
            amountText = form.amountText.trim(),
            calories = form.calories.trim().toIntOrNull(),
            proteinG = form.proteinG.trim().toDoubleOrNull(),
            carbsG = form.carbsG.trim().toDoubleOrNull(),
            fatG = form.fatG.trim().toDoubleOrNull(),
            sets = form.sets.trim().toIntOrNull(),
            reps = form.reps.trim().ifBlank { null },
            sortOrder = index,
        )
    }

    companion object {
        const val WEEKDAYS_MON_TO_FRI = 0b0011111

        fun new(today: LocalDate) = GroupForm(anchorDate = today.toString())

        fun from(group: PlanGroupEntity, items: List<PlanItemEntity>): GroupForm = GroupForm(
            id = group.id,
            name = group.name,
            kind = group.kind,
            hour = group.timeOfDay / 60,
            minute = group.timeOfDay % 60,
            recurrenceType = group.recurrenceType,
            intervalDays = group.intervalDays.coerceAtLeast(1),
            weekdaysMask = group.weekdaysMask,
            monthDays = group.monthDays,
            anchorDate = group.anchorDate,
            endDate = group.endDate.orEmpty(),
            reminderOffsetMinutes = group.reminderOffsetMinutes,
            enabled = group.enabled,
            items = items.sortedBy { it.sortOrder }.map { item ->
                ItemForm(
                    name = item.name,
                    amountText = item.amountText,
                    calories = item.calories?.toString().orEmpty(),
                    proteinG = item.proteinG?.toString().orEmpty(),
                    carbsG = item.carbsG?.toString().orEmpty(),
                    fatG = item.fatG?.toString().orEmpty(),
                    sets = item.sets?.toString().orEmpty(),
                    reps = item.reps.orEmpty(),
                )
            }.ifEmpty { listOf(ItemForm()) },
        )
    }
}

class PlanViewModel(private val container: AppContainer) : ViewModel() {

    val groups: StateFlow<List<GroupWithItems>> = container.planRepository.observeGroupsWithItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private val _form = MutableStateFlow<GroupForm?>(null)
    val form: StateFlow<GroupForm?> = _form.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun startCreate() { _form.value = GroupForm.new(container.time.today()) }

    fun startEdit(groupId: Long) {
        viewModelScope.launch {
            val group = container.planRepository.groupById(groupId) ?: return@launch
            val items = container.planRepository.itemsOfGroup(groupId)
            _form.value = GroupForm.from(group, items)
        }
    }

    fun closeForm() { _form.value = null }

    fun updateForm(transform: (GroupForm) -> GroupForm) {
        _form.value = _form.value?.let(transform)
    }

    fun updateItem(index: Int, transform: (ItemForm) -> ItemForm) {
        updateForm { current ->
            val updated = current.items.toMutableList()
            if (index in updated.indices) updated[index] = transform(updated[index])
            current.copy(items = updated)
        }
    }

    fun addItem() = updateForm { it.copy(items = it.items + ItemForm()) }

    fun removeItem(index: Int) = updateForm { current ->
        val updated = current.items.toMutableList()
        if (index in updated.indices) updated.removeAt(index)
        current.copy(items = updated)
    }

    /** @return 是否保存成功（校验不通过时为 false，UI 保持停留在表单页） */
    fun save(): Boolean {
        val current = _form.value ?: return false
        if (!current.isValid) {
            _message.value = current.nameError ?: current.recurrenceError ?: current.itemsError
            return false
        }
        viewModelScope.launch {
            val groupId = container.planRepository.upsertGroupWithItems(
                current.toEntity(),
                current.toItems(),
            )
            // 排程必须重算：改了时间/重复规则后旧闹钟还在，会出现"提醒了但今天没这项"
            runCatching { container.reminderScheduler.scheduleWindow() }
            _form.value = null
            _message.value = "已保存"
        }
        return true
    }

    fun delete(groupId: Long) {
        viewModelScope.launch {
            container.planRepository.deleteGroup(groupId)
            runCatching { container.reminderScheduler.cancelGroup(groupId) }
            _message.value = "已删除"
        }
    }

    fun setEnabled(groupId: Long, enabled: Boolean) {
        viewModelScope.launch {
            container.planRepository.setEnabled(groupId, enabled)
            runCatching { container.reminderScheduler.scheduleWindow() }
        }
    }

    fun clearMessage() { _message.value = null }
}
