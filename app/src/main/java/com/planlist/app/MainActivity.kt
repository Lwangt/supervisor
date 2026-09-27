package com.planlist.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.planlist.app.data.repo.AppSettings
import com.planlist.app.ui.AppRoot
import com.planlist.app.ui.theme.PlanListTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as PlanListApp).container

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            PlanListTheme(mode = settings.themeMode) {
                AppRoot(container = container)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 每次回到前台补一次排程窗口，并重启每日补齐链。
        // 这里才是"系统清理过闹钟 / 用户手动强停过"之后的主要恢复路径，
        // 而 Application.onCreate 不再承担这件事（它也会在闹钟冷启动时执行，见 PlanListApp 注释）。
        val container = (application as PlanListApp).container
        container.appScope.launch {
            runCatching {
                container.reminderScheduler.scheduleWindow()
                container.reminderScheduler.scheduleDailyTopUp()
            }
        }
    }
}
