package com.planlist.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.planlist.app.AppContainer
import com.planlist.app.reminder.Permissions
import com.planlist.app.ui.history.HistoryScreen
import com.planlist.app.ui.history.HistoryViewModel
import com.planlist.app.ui.plan.PlanListScreen
import com.planlist.app.ui.plan.PlanViewModel
import com.planlist.app.ui.settings.SettingsScreen
import com.planlist.app.ui.today.TodayScreen
import com.planlist.app.ui.today.TodayViewModel

enum class Tab(val label: String) {
    TODAY("今日"),
    PLAN("计划"),
    HISTORY("历史"),
    SETTINGS("设置"),
}

@Composable
fun AppRoot(container: AppContainer) {
    val factory = remember(container) { VMFactory(container) }

    val todayViewModel: TodayViewModel = viewModel(factory = factory)
    val planViewModel: PlanViewModel = viewModel(factory = factory)
    val historyViewModel: HistoryViewModel = viewModel(factory = factory)

    var tab by remember { mutableStateOf(Tab.TODAY) }

    val context = LocalContext.current

    // Android 13+ 的通知权限：首次启动就请求，避免"建好了计划却从不提醒"
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { },
    )
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Permissions.hasNotificationPermission(context)
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = {
                            Icon(
                                imageVector = when (entry) {
                                    Tab.TODAY -> Icons.Filled.Checklist
                                    Tab.PLAN -> Icons.Filled.ListAlt
                                    Tab.HISTORY -> Icons.Filled.BarChart
                                    Tab.SETTINGS -> Icons.Filled.Settings
                                },
                                contentDescription = entry.label,
                            )
                        },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (tab) {
                Tab.TODAY -> {
                    val state by todayViewModel.state.collectAsStateWithLifecycle()
                    TodayScreen(
                        state = state,
                        onShiftDate = todayViewModel::shiftDate,
                        onGoToday = todayViewModel::goToToday,
                        onToggleItem = todayViewModel::toggleItem,
                        onFinishGroup = todayViewModel::finishGroup,
                        onClearGroup = todayViewModel::clearGroup,
                    )
                }

                Tab.PLAN -> {
                    val groups by planViewModel.groups.collectAsStateWithLifecycle()
                    val form by planViewModel.form.collectAsStateWithLifecycle()
                    val message by planViewModel.message.collectAsStateWithLifecycle()
                    PlanListScreen(
                        groups = groups,
                        form = form,
                        message = message,
                        today = container.time.today(),
                        onCreate = planViewModel::startCreate,
                        onEdit = planViewModel::startEdit,
                        onDelete = planViewModel::delete,
                        onToggleEnabled = planViewModel::setEnabled,
                        onFormChange = planViewModel::updateForm,
                        onItemChange = planViewModel::updateItem,
                        onAddItem = planViewModel::addItem,
                        onRemoveItem = planViewModel::removeItem,
                        onSave = planViewModel::save,
                        onCloseForm = planViewModel::closeForm,
                        onMessageShown = planViewModel::clearMessage,
                    )
                }

                Tab.HISTORY -> {
                    val state by historyViewModel.state.collectAsStateWithLifecycle()
                    HistoryScreen(
                        state = state,
                        onPreviousMonth = historyViewModel::previousMonth,
                        onNextMonth = historyViewModel::nextMonth,
                        onGoToCurrentMonth = historyViewModel::goToCurrentMonth,
                        onSelectDate = historyViewModel::selectDate,
                        onCompleteGroup = historyViewModel::completeGroupOn,
                        onClearGroup = historyViewModel::clearGroupOn,
                    )
                }

                Tab.SETTINGS -> SettingsScreen(container = container)
            }
        }
    }
}
