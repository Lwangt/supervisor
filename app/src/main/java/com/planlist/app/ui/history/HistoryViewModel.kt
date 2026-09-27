package com.planlist.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.planlist.app.AppContainer
import com.planlist.app.domain.DaySummary
import com.planlist.app.domain.HistoryAssembler
import com.planlist.app.domain.StreakCalculator
import com.planlist.app.domain.TodayAssembler
import com.planlist.app.domain.TodayGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class HistoryUiState(
    val month: YearMonth,
    val summaries: Map<LocalDate, DaySummary>,
    val streak: Int,
    val completionRate: Double,
    val today: LocalDate,
    val selectedDate: LocalDate?,
    val selectedGroups: List<TodayGroup>,
    val canEditSelected: Boolean,
) {
    companion object {
        fun empty(month: YearMonth, today: LocalDate) = HistoryUiState(
            month = month,
            summaries = emptyMap(),
            streak = 0,
            completionRate = 0.0,
            today = today,
            selectedDate = null,
            selectedGroups = emptyList(),
            canEditSelected = false,
        )
    }
}

class HistoryViewModel(private val container: AppContainer) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.from(container.time.today()))

    private val selectedDate = MutableStateFlow<LocalDate?>(null)

    val state: StateFlow<HistoryUiState> = combine(
        month,
        selectedDate,
        container.planRepository.observeGroupsWithItems(),
        container.logRepository.observeRange(
            container.time.today().minusDays(LOOKBACK_DAYS),
            container.time.today().plusDays(31),
        ),
    ) { selectedMonth, selected, groupsWithItems, logs ->
        val today = container.time.today()
        val now = container.time.now()
        val groups = groupsWithItems.map { it.group }
        val items = groupsWithItems.flatMap { it.items }

        val summaries = HistoryAssembler.summaries(
            from = selectedMonth.atDay(1),
            to = selectedMonth.atEndOfMonth(),
            now = now,
            groups = groups,
            items = items,
            logs = logs,
        )

        val recent = HistoryAssembler.summaries(
            from = today.minusDays(LOOKBACK_DAYS),
            to = today,
            now = now,
            groups = groups,
            items = items,
            logs = logs,
        )

        val catchUpDays = container.settingsRepository.current().catchUpDays
        val selectedGroups = if (selected == null) {
            emptyList()
        } else {
            TodayAssembler.assemble(
                date = selected,
                now = now,
                groups = groups,
                items = items,
                logs = logs,
            )
        }

        HistoryUiState(
            month = selectedMonth,
            summaries = summaries.associateBy { it.date },
            streak = StreakCalculator.currentStreak(recent, today),
            completionRate = StreakCalculator.completionRate(recent, today),
            today = today,
            selectedDate = selected,
            selectedGroups = selectedGroups,
            canEditSelected = selected != null &&
                !selected.isAfter(today) &&
                !selected.isBefore(today.minusDays(catchUpDays.toLong())),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = HistoryUiState.empty(
            YearMonth.from(container.time.today()),
            container.time.today(),
        ),
    )

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
        selectedDate.value = null
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
        selectedDate.value = null
    }

    fun goToCurrentMonth() {
        month.value = YearMonth.from(container.time.today())
        selectedDate.value = null
    }

    fun selectDate(date: LocalDate?) {
        selectedDate.value = if (selectedDate.value == date) null else date
    }

    /** 补打卡：受 catchUpDays 限制，超出范围静默忽略（UI 也已置灰）。 */
    fun completeGroupOn(date: LocalDate, groupId: Long) {
        viewModelScope.launch {
            val today = container.time.today()
            if (date.isAfter(today)) return@launch
            val catchUpDays = container.settingsRepository.current().catchUpDays
            if (date.isBefore(today.minusDays(catchUpDays.toLong()))) return@launch
            val items = container.planRepository.itemsOfGroup(groupId)
            if (items.isEmpty()) return@launch
            container.logRepository.completeGroup(date, groupId, items.map { it.id })
        }
    }

    fun clearGroupOn(date: LocalDate, groupId: Long) {
        viewModelScope.launch {
            container.logRepository.clearGroup(date, groupId)
        }
    }

    private companion object {
        /** 统计连续天数需要回看的历史窗口。 */
        const val LOOKBACK_DAYS = 120L
    }
}
