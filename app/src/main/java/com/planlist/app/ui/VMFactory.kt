package com.planlist.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.planlist.app.AppContainer
import com.planlist.app.ui.history.HistoryViewModel
import com.planlist.app.ui.plan.PlanViewModel
import com.planlist.app.ui.today.TodayViewModel

/** 极简 ViewModel 工厂：够用、可读、无注解处理开销。 */
class VMFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(TodayViewModel::class.java) -> TodayViewModel(container) as T
        modelClass.isAssignableFrom(PlanViewModel::class.java) -> PlanViewModel(container) as T
        modelClass.isAssignableFrom(HistoryViewModel::class.java) -> HistoryViewModel(container) as T
        else -> throw IllegalArgumentException("未知的 ViewModel: " + modelClass.name)
    }
}
