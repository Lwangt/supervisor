# 提醒引擎与 HyperOS 适配

> 这是本项目的**最高风险区**。功能"能跑"很容易，"连续 3 天都准时响"才是真正的验收标准。

## 1. 为什么完全不用 WorkManager

WorkManager 有两条路都走不通：

1. **做精确提醒**：周期任务最小间隔 15 分钟，且受 Doze 影响可被推迟数小时，做不到"12:30 准时响"
2. **做兜底补齐**：它会在合并 manifest 时带进 `ACCESS_NETWORK_STATE` 与
   `FOREGROUND_SERVICE` 两个权限。对一个明确宣称"纯本地不联网"的应用来说，
   权限列表里出现"查看网络连接"是没必要的噪声与信任成本

因此本项目：

- 精确提醒 → `AlarmManager.setExactAndAllowWhileIdle`
- 窗口兜底 → `DailyTopUpReceiver`：每天 00:05 的一次性精确闹钟，
  触发时既补满 7 天窗口，**也把明天的自己排上**（自续期链）。
  即使连续几周不打开 App，滚动窗口也不会断。

代价是这条链依赖系统不清除闹钟。用户手动「强行停止」仍会清掉它，
这时靠"下次打开 App 自动重排"恢复。

## 2. 排程算法

```kotlin
fun scheduleWindow(days: Int = 7, now: LocalDateTime = clock.now()) {
    for (offset in 0 until days) {
        val date = now.toLocalDate().plusDays(offset.toLong())
        for (group in planRepo.enabledGroups()) {
            if (!RecurrenceCalculator.occursOn(group, date)) continue
            val triggerAt = date.atTime(
                group.timeOfDay / 60, group.timeOfDay % 60
            ).minusMinutes(group.reminderOffsetMinutes.toLong())
            if (!triggerAt.isAfter(now)) continue          // 只排未来
            alarmManager.setExactAndAllowWhileIdle(
                RTC_WAKEUP, triggerAt.toInstant(zone).toEpochMilli(), pi(group, date)
            )
        }
    }
}

private fun pi(group: PlanGroupEntity, date: LocalDate): PendingIntent {
    val intent = Intent(ctx, ReminderReceiver::class.java).apply {
        data = "planlist://group/${group.id}/date/$date".toUri()   // data 参与 PendingIntent 去重
        putExtra(EXTRA_GROUP_ID, group.id)
        putExtra(EXTRA_DATE, date.toString())
    }
    // requestCode 由 (groupId, date) 稳定推导 → 重复排程自动覆盖
    return PendingIntent.getBroadcast(
        ctx, requestCode(group.id, date), intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
```

**关键点**

1. **`data` URI 必须唯一**：只靠 `requestCode` 去重是不够的，Intent 相等性还比较 `data`。
   否则同一组不同日期的 PendingIntent 会互相覆盖（一个经典且极难排查的 bug）。
2. **幂等**：重复调用 `scheduleWindow` 不产生重复闹钟。
3. **`RTC_WAKEUP`** 而非 `ELAPSED_REALTIME`：用户改系统时间后仍按挂钟时刻触发。
4. **不排过去时刻**：避免 App 启动瞬间补发一堆历史通知。
5. **窗口 7 天 + 12h 补齐**：兼顾"系统随时可能清闹钟"与"不要排 14 天导致状态陈旧"。

## 3. 降级链（绝不静默失败）

```
canScheduleExactAlarms()?
  ├─ true  → setExactAndAllowWhileIdle   （精确，预期 ±10 秒）
  └─ false → setAndAllowWhileIdle        （宽松，预期 ±数分钟）
             + 设置页常驻黄色提示条 + 一键跳转授权
```

Android 12+ 需 `SCHEDULE_EXACT_ALARM`；应用未获授权时 `setExact*` 会抛 `SecurityException`，
**必须 try/catch 兜底**，否则一次异常会中断整个窗口的排程。

## 4. 触发链路

```
AlarmManager ──(到点)──► ReminderReceiver
                            ├─ 查库：该组是否已被完成？是 → 静默退出（不响）
                            ├─ 查库：组是否已被删除/停用？是 → 静默退出
                            ├─ NotificationFactory.build(group, items, date)
                            ├─ NotificationManagerCompat.notify(groupId, n)
                            ├─ vibrate(pattern)          ← 冗余主动震动
                            └─ 立即排下一轮窗口（滚动续期）

用户点通知动作 ──► ReminderActionReceiver
                            ├─ COMPLETE : UPSERT 全部条目为 COMPLETED
                            │             + 取消该组当日剩余通知
                            └─ SNOOZE   : 排 now + snoozeMinutes 的一次性闹钟
```

**"查库后静默退出"很重要**：否则用户 12:00 提前标记了完成，12:30 还会被提醒一次。

## 5. 震动策略

通知渠道震动可能被"静音模式""勿扰模式"吞掉。因此**双通道**保证：

| 通道 | 实现 | 特点 |
|------|------|------|
| 系统通知震动 | 渠道 `enableVibration(true)` + `VibrationEffect.createWaveform(longArrayOf(0,400,200,400), -1)` | 用户可在系统设置里关掉 |
| 主动震动 | API 31+：`VibratorManager.getDefaultVibrator()`；26–30：`VIBRATOR_SERVICE`（需 `VIBRATE` 权限） | 与通知系统无关，更可靠 |

主动震动必须 try/catch（部分设备/权限下会抛异常），且**不得阻塞**通知显示。

## 6. HyperOS 3 适配清单（写入设置页引导）

| 项 | 路径 | 为什么必须 |
|----|------|-----------|
| 通知权限 | 系统弹窗 / 应用信息 → 通知 | Android 13+ 默认拒绝，否则通知完全不显示 |
| 精确闹钟 | 应用信息 → 闹钟和提醒 | 否则降级为模糊提醒 |
| 电池 → **无限制** | 应用信息 → 省电策略 → 无限制 | 否则息屏后被冻结，闹钟不触发 |
| **自启动** | 应用管理 → 权限管理 → 自启动 | HyperOS 特有，否则重启后闹钟不恢复 |
| 最近任务**加锁** | 多任务界面下拉卡片点锁 | 防止一键清理时被杀 |
| 关闭"省电模式"对该应用的限制 | 电池 → 省电模式 | 极端省电下会屏蔽后台闹钟 |

**可直接用 Intent 跳转的**：
- `Settings.ACTION_APP_NOTIFICATION_SETTINGS`（带 `EXTRA_APP_PACKAGE`）
- `Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`（带 `package:` URI）
- `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` / `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`

**无法用 Intent 跳转的**（HyperOS 私有页面）：自启动、任务加锁 → 只能在应用内**图文说明 + 手动跳应用详情页**。

## 7. 已知系统级限制（无法绕过，需向用户说明）

1. **用户手动「强行停止」应用** → 系统清除该应用全部闹钟，且 App 无法被自动唤醒。
   恢复方式：重新打开一次 App（自动重排）。应用内需说明这一点。
2. **设备重启后到用户首次解锁前**（Direct Boot 场景），凭据加密存储不可读。
   P0 不做 Direct Boot 支持（不自建 device-protected 存储），重启后首次解锁完成即由 `BOOT_COMPLETED` 重排。
   若实测发现 HyperOS 重启后提醒大面积丢失，再评估启用 `android:directBootAware`。
3. **系统极端省电/低电量模式**可能推迟所有非精确闹钟。此时精确闹钟权限 + 电池无限制是唯一解。

## 8. 真机排查命令

```powershell
# 查看应用已注册的闹钟（最有用的一条）
adb shell dumpsys alarm | Select-String -Pattern "planlist" -Context 3,3

# 查看通知渠道状态
adb shell dumpsys notification --noredact | Select-String -Pattern "planlist" -Context 2,8

# 查看被电池优化限制的应用
adb shell dumpsys deviceidle whitelist

# 把应用加入 Doze 白名单（调试用）
adb shell dumpsys deviceidle whitelist +com.planlist.app

# 查看是否申请过精确闹钟权限
adb shell cmd appops get com.planlist.app SCHEDULE_EXACT_ALARM

# 模拟 Doze（验证闹钟在 Doze 下仍触发）
adb shell dumpsys deviceidle force-idle
adb shell dumpsys deviceidle unforce

# 手动触发接收器（快速验证通知链路，无需等待）
adb shell am broadcast -a com.planlist.app.DEBUG_REMIND -n com.planlist.app/.reminder.ReminderReceiver --ei groupId 1
```
