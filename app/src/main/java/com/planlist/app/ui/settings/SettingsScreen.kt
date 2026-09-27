package com.planlist.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.planlist.app.AppContainer
import com.planlist.app.data.repo.AppSettings
import com.planlist.app.data.repo.ThemeMode
import com.planlist.app.reminder.NotificationFactory
import com.planlist.app.reminder.Permissions
import com.planlist.app.ui.theme.Danger
import com.planlist.app.ui.theme.Primary
import com.planlist.app.ui.theme.Warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val settings by container.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = AppSettings())

    var notificationGranted by remember { mutableStateOf(Permissions.hasNotificationPermission(context)) }
    var exactAlarmGranted by remember {
        mutableStateOf(container.reminderScheduler.canScheduleExactAlarms())
    }
    var batteryOptimized by remember {
        mutableStateOf(Permissions.isBatteryOptimizationIgnored(context))
    }
    var status by remember { mutableStateOf<String?>(null) }

    // 从系统设置页返回时刷新权限状态
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationGranted = Permissions.hasNotificationPermission(context)
                exactAlarmGranted = container.reminderScheduler.canScheduleExactAlarms()
                batteryOptimized = Permissions.isBatteryOptimizationIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    val text = container.backupRepository.export()
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri)
                            ?: error("无法写入所选文件")
                        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                    }
                    text.length
                }
                status = result.fold(
                    onSuccess = { "已导出备份（" + it + " 字符）" },
                    onFailure = { "导出失败：" + (it.message ?: "未知错误") },
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    val text = withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openInputStream(uri)
                            ?: error("无法读取所选文件")
                        stream.bufferedReader().use { it.readText() }
                    }
                    container.backupRepository.import(text)
                }
                status = result.fold(
                    onSuccess = {
                        runCatching { container.reminderScheduler.scheduleWindow() }
                        "导入完成：新建 " + it.groupsCreated + " 个计划，更新 " +
                            it.groupsUpdated + " 个，恢复 " + it.logsImported + " 条打卡记录"
                    },
                    onFailure = { "导入失败：" + (it.message ?: "未知错误") },
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text("设置", style = MaterialTheme.typography.headlineLarge)

        // ------------------------------------------------ 权限
        SectionTitle("提醒权限（不授权就收不到提醒）")

        StatusCard(
            title = "通知权限",
            ok = notificationGranted,
            okText = "已授予",
            badText = "未授予 —— 到点提醒不会显示",
            actionLabel = "去授权",
            onAction = { SettingsIntents.openNotificationSettings(context) },
        )
        StatusCard(
            title = "精确闹钟",
            ok = exactAlarmGranted,
            okText = "已授予 —— 提醒可精确到分钟",
            badText = "未授予 —— 只能模糊提醒，可能晚数分钟",
            actionLabel = "去授权",
            onAction = { SettingsIntents.requestExactAlarmPermission(context) },
        )
        StatusCard(
            title = "电池优化",
            ok = batteryOptimized,
            okText = "已豁免 —— 息屏后提醒仍会触发",
            badText = "未豁免 —— 息屏后提醒可能被推迟",
            actionLabel = "去设置",
            onAction = { SettingsIntents.requestIgnoreBatteryOptimization(context) },
        )

        TextButton(
            onClick = {
                scope.launch {
                    runCatching {
                        container.reminderScheduler.scheduleWindow()
                        container.reminderScheduler.scheduleDailyTopUp()
                    }
                    status = "已按当前计划重排全部提醒"
                }
            },
        ) {
            Text("立即重排所有提醒")
        }

        // ------------------------------------------------ HyperOS 引导
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = Warning.copy(alpha = 0.10f),
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "小米 HyperOS 必做两项",
                    style = MaterialTheme.typography.titleMedium,
                    color = Warning,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "HyperOS 会清理后台应用。下面两项系统不允许 App 自动跳转，必须手动开：",
                    style = MaterialTheme.typography.bodyMedium,
                )
                GuideStep(1, "设置 → 应用设置 → 应用管理 → 计划清单 → 打开「自启动」")
                GuideStep(2, "多任务界面下拉「计划清单」卡片 → 点击锁图标加锁")
                GuideStep(3, "（可选）电池 → 省电模式 → 不要限制本应用")
                OutlinedButton(onClick = { SettingsIntents.openAppDetails(context) }) {
                    Text("打开应用详情页")
                }
            }
        }

        // ------------------------------------------------ 行为设置
        SectionTitle("行为")

        SettingRow(title = "提醒总开关", subtitle = "关闭后不再排任何闹钟") {
            Switch(
                checked = settings.remindersEnabled,
                onCheckedChange = { value ->
                    scope.launch {
                        container.settingsRepository.setRemindersEnabled(value)
                        if (value) {
                            runCatching { container.reminderScheduler.scheduleWindow() }
                        } else {
                            runCatching { container.reminderScheduler.cancelAllReminders() }
                        }
                    }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Primary),
            )
        }

        SettingRow(title = "今日页显示宏量", subtitle = "关闭后只显示完成组数") {
            Switch(
                checked = settings.showMacros,
                onCheckedChange = { value ->
                    scope.launch { container.settingsRepository.setShowMacros(value) }
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Primary),
            )
        }

        Text("可补打卡天数", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 1, 2, 3).forEach { days ->
                FilterChip(
                    selected = settings.catchUpDays == days,
                    onClick = { scope.launch { container.settingsRepository.setCatchUpDays(days) } },
                    label = { Text(if (days == 0) "不可补" else "±" + days + " 天") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary.copy(alpha = 0.25f),
                    ),
                )
            }
        }

        Text("稍后提醒间隔", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 30).forEach { minutes ->
                FilterChip(
                    selected = settings.snoozeMinutes == minutes,
                    onClick = { scope.launch { container.settingsRepository.setSnoozeMinutes(minutes) } },
                    label = { Text(minutes.toString() + " 分钟") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary.copy(alpha = 0.25f),
                    ),
                )
            }
        }

        Text("主题", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ThemeMode.DARK to "深色",
                ThemeMode.LIGHT to "浅色",
                ThemeMode.SYSTEM to "跟随系统",
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { scope.launch { container.settingsRepository.setThemeMode(mode) } },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary.copy(alpha = 0.25f),
                    ),
                )
            }
        }

        // ------------------------------------------------ 备份
        SectionTitle("备份与恢复")
        Text(
            "备份是完全本地的 JSON 文件，不经过任何服务器。换手机或重装前建议导出一份。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    exportLauncher.launch("planlist-backup.json")
                },
            ) { Text("导出备份") }
            OutlinedButton(
                onClick = {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
            ) { Text("导入备份") }
        }

        status?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
            )
        }

        // ------------------------------------------------ 关于
        SectionTitle("关于")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("计划清单 1.0", style = MaterialTheme.typography.titleMedium)
                Text(
                    "本应用不申请联网权限（APK 中没有 INTERNET 权限），" +
                        "所有计划、打卡记录、备份文件都只存在本机。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "数据位置：应用私有目录下的 planlist.db（Android/data 受系统保护）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = {
                        SettingsIntents.openAppNotificationChannel(
                            context,
                            NotificationFactory.CHANNEL_REMINDERS,
                        )
                    },
                ) { Text("调整提醒通知的响铃与震动") }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun StatusCard(
    title: String,
    ok: Boolean,
    okText: String,
    badText: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (ok) Primary else Danger),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (ok) okText else badText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else Danger,
                )
            }
            if (!ok) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
    }
}

@Composable
private fun GuideStep(index: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = Warning,
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Warning.copy(alpha = 0.20f))
                .padding(top = 3.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
