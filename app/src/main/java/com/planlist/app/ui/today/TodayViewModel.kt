package com.planlist.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.planlist.app.AppContainer
import com.planlist.app.data.db.entity.LogStatus
import com.planlist.app.data.repo.AppSettings
import com.planlist.app.domain.DaySummary
import com.planlist.app.domain.TodayAssembler
import com.planlist.app.domain.TodayGroup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TodayUiState(
    val date: LocalDate,
    val isToday: Boolean,
    val canEdit: Boolean,
    val groups: List<TodayGroup>,
    val summary: DaySummary,
    val settings: AppSettings,
) {
    val totalCaloriesPlanned: Int get() = groups.sumOf { it.planned.calories }
    val totalCaloriesEaten: Int get() = groups.sumOf { it.eaten.calories }
    val useMacroMode: Boolean get() = groups.any { it.useMacroMode }

    companion object {
        fun empty(date: LocalDate) = TodayUiState(
            date = date,
            isToday = true,
            canEdit = true,
            groups = emptyList(),
            summary = DaySummary(date, 0, 0, 0, 0),
            settings = AppSettings(),
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val container: AppContainer) : ViewModel() {

    private val selectedDate = MutableStateFlow(container.time.today())

    /** 每 30 秒重算一次"现在几点"，让 NOW / OVERDUE 标签自动翻转，无需用户手动刷新。 */
    private val ticker: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_INTERVAL_MILLIS)
        }
    }

    private val logsForDate = selectedDate.flatMapLatest { date ->
        container.logRepository.observeForDate(date)
    }

    val state: StateFlow<TodayUiState> = combine(
        container.planRepository.observeGroupsWithItems(),
        selectedDate,
        logsForDate,
        container.settingsRepository.settings,
        ticker,
    ) { groupsWithItems, date, logs, settings, _ ->
        val now = container.time.now()
        val groups = TodayAssembler.assemble(
            date = date,
            now = now,
            groups = groupsWithItems.map { it.group },
            items = groupsWithItems.flatMap { it.items },
            logs = logs,
        )
        val canEdit = canEdit(date, settings.catchUpDays)
        TodayUiState(
            date = date,
            isToday = date == container.time.today(),
            canEdit = canEdit,
            groups = groups,
            summary = TodayAssembler.daySummary(date, groups),
            settings = settings,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = TodayUiState.empty(container.time.today()),
    )

    /** 未来日期不可勾选；过去日期受 catchUpDays 限制（默认 ±1 天，与 Macro7 一致）。 */
    fun canEdit(date: LocalDate, catchUpDays: Int): Boolean {
        val today = container.time.today()
        if (date.isAfter(today)) return false
        return !date.isBefore(today.minusDays(catchUpDays.toLong()))
    }

    fun selectDate(date: LocalDate) { selectedDate.value = date }

    fun shiftDate(days: Long) { selectedDate.value = selectedDate.value.plusDays(days) }

    fun goToToday() { selectedDate.value = container.time.today() }

    /** 勾选/取消单个条目。 */
    fun toggleItem(groupId: Long, itemId: Long, completed: Boolean) {
        viewModelScope.launch {
            val state = state.value
            if (!state.canEdit) return@launch
            container.logRepository.setItemStatus(
                date = state.date,
                groupId = groupId,
                itemId = itemId,
                status = if (completed) LogStatus.COMPLETED else null,
            )
        }
    }

    /** 长按整组完成。 */
    fun finishGroup(groupId: Long) {
        viewModelScope.launch {
            val state = state.value
            if (!state.canEdit) return@launch
            val ids = state.groups.firstOrNull { it.group.id == groupId }?.items?.map { it.id }.orEmpty()
            if (ids.isEmpty()) return@launch
            container.logRepository.completeGroup(state.date, groupId, ids)
        }
    }

    fun clearGroup(groupId: Long) {
        viewModelScope.launch {
            val state = state.value
            if (!state.canEdit) return@launch
            container.logRepository.clearGroup(state.date, groupId)
        }
    }

    private companion object {
        const val TICK_INTERVAL_MILLIS = 30_000L
    }
}
