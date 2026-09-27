package com.planlist.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.planlist.app.data.repo.AppSettings
import com.planlist.app.reminder.AlarmTopUpWorker
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
        // 每次回到前台补一次排程窗口：覆盖"系统清理 + 用户手动强停"后的恢复
        AlarmTopUpWorker.runOnce(this)
    }
}
