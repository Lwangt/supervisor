# 计划清单 PlanList — 开发与测试计划（v1.0）

> 生成日期：2026-09-27
> 目标设备：小米 HyperOS 3（Android 16 底层）· 侧载自用 · 纯离线
> 对标产品：Diet & Workout Tracker: Macro7（com.ehmtech.macro）— 我们**只借鉴交互与视觉，不复制其收费限制**

---

## 0. 待你确认的 4 个决策（我已按推荐值先行初始化，改动成本均为 1 行）

| # | 决策点 | 我采用的默认值 | 改动成本 |
|---|--------|----------------|----------|
| D1 | 技术栈 | 原生 Kotlin 2.1 + Jetpack Compose (Material 3) | 高（需重建工程），建议不要改 |
| D2 | 构建工具链 | 未自动安装；已提供 `tools/setup-toolchain.ps1`（JDK17 + Android SDK，约 5–6 GB，装到 `D:\Android\Sdk`） | 低 |
| D3 | 功能范围 | **P0/P1 精简可跑版**：饮食+运动计划、5 种重复规则、精确提醒+震动、完成勾选、历史日历、JSON 备份。**不做**体重/体脂/睡眠/围度、Insights 趋势图、30 套主题 | 中（增量加，数据模型已预留） |
| D4 | 应用名/包名 | 显示名「计划清单」· `com.planlist.app` | 极低（`strings.xml` + `build.gradle.kts`） |

---

## 1. 需求拆解与验收标准

### 1.1 用户原始需求 → 工程化验收条件

| 需求 | 验收条件（可测） |
|------|------------------|
| ① 自定义每天饮食/运动计划 | 可创建「组」（早/午/晚/加餐/训练），组内含任意条「条目」；组支持 5 种重复规则；**组数与条目数无任何上限**（这是与 Macro7 的核心差异） |
| ② 到点消息+震动提醒 | 到达设定时刻（或提前 N 分钟）触发系统通知 + 震动；锁屏可见；点击通知内按钮可直接「完成」或「稍后 10 分钟」 |
| ③ 完成标记 + 历史查看 | 今日页可勾选条目/整组；历史页月历按天着色（全完成/部分/休息）；点任意历史日可查看并补勾 |
| ④ 纯本地不联网 | **APK 中不声明 `android.permission.INTERNET`**（最强保证：即使代码有网络请求也必然失败）；无账号、无遥测、无广告 SDK |
| ⑤ 安卓 / HyperOS 3 可用 | 真机 ADB 侧载安装成功；断电重启后提醒仍在；HyperOS 后台限制下连续 3 天提醒不丢 |
| ⑥ 简约美观，参考 Macro7 | Material 3 + 深色优先；圆角卡片、进度环、NOW/OVERDUE/UP NEXT 时间语义标签、长按 500ms 触感反馈完成 |

### 1.2 明确**不做**（避免范围蔓延）

- 食物数据库 / 条码扫描 / AI 拍照识别（Macro7 本身也没有）
- 云同步、多设备、账号体系（与需求④冲突）
- 社交、排行榜、订阅、内购
- 应用发布上架（需求⑤明确自用）

---

## 2. 竞品对标：Macro7 功能矩阵与我们的取舍

依据 EHM Tech 官网产品页实测描述整理：

| Macro7 能力 | 我们的做法 |
|-------------|------------|
| 自建重复餐次/训练（Daily / Every N Days / Weekdays / Month Days / Once 五种规则） | **全量实现**，五种规则 1:1 对齐 |
| 食物含宏量营养（热量/蛋白/碳水/脂肪），组内可选 | 实现；未填宏量时自动降级为「完成组数」计数（与 Macro7 同策略） |
| 训练支持组数×次数 | 实现（`sets` + `reps` 文本字段，可填 "8-12" 或时长） |
| Today 页 NOW / OVERDUE / UP NEXT 语义 + 相邻组预览 | 实现 |
| 长按 500ms 完成整组 + 触感反馈 | 实现（`detectTapGestures` + `HapticFeedbackType.LongPress`） |
| ±1 天补打卡（昨天的最后一组今天仍可补勾） | 实现 |
| 当天结束回顾 + 撒花动效 | P2（可延后） |
| 提醒：精确时刻或提前 30 分钟；滚动 14 天窗口调度 | 实现；**窗口改为滚动 7 天 + 12 小时补齐 Worker**（更省电，见 §5） |
| 体重/体脂/睡眠/围度记录、趋势图、7 日平均 | **P2 不做**（你的需求未提），数据模型预留 `body_metric` 表位 |
| 30 套 Material 3 主题 | **不做**，仅 1 套精调深/浅色 |
| 免费版限制计划条数、Pro 订阅（约 7 天试用） | **完全移除**：无条数上限、无功能门禁、无付费点 |

---

## 3. 技术选型与理由

| 层 | 选型 | 理由 |
|----|------|------|
| 语言 | Kotlin 2.1.0 | Compose 官方语言，空安全，`java.time` 在 minSdk 26 原生可用 |
| UI | Jetpack Compose + Material 3（Compose BOM 2024.12.01） | 还原 Macro7 卡片/进度环/手势最省力；无 XML 布局负担 |
| 架构 | 单模块 MVVM + Repository + 手动 DI（Application 级容器） | 个人自用项目，引入 Hilt 只增加 KSP/编译负担，无收益 |
| 持久化 | Room 2.6.1（KSP）+ DataStore Preferences | 计划/日志是强关系型+聚合查询，Room 合适；设置项用 DataStore |
| 调度 | AlarmManager `setExactAndAllowWhileIdle` + 每日自续期补齐闹钟 | 精确到分钟的本地提醒唯一可靠方案；自续期闹钟兜底 Doze/系统清理，且不引入额外权限 |
| 时间 | `java.time`（LocalDate/LocalDateTime/ZonedDateTime） | 纯函数、可注入 `Clock`，**提醒算法可 100% 单元测试** |
| 构建 | AGP 8.7.3 + Gradle 8.9 + JDK 17 + compileSdk/targetSdk 35 + minSdk 26 | 经充分验证的稳定组合；minSdk 26 保证 `java.time` 与通知渠道原生可用 |

> ⚠️ **当前机器阻塞点**：已安装的是 **JDK 16.0.1**（`C:\Program Files\Java\jdk-16.0.1`），而 AGP 8.x **强制要求 JDK 17+**；同时**没有 Android SDK / Gradle / adb / Flutter**。所以「装工具链」是执行阶段的第一步，见 §8。

---

## 4. 架构与代码结构

```
app/src/main/java/com/planlist/app/
├─ PlanListApp.kt                 # Application：手动 DI 容器，建通知渠道
├─ MainActivity.kt                # 单 Activity + Compose Navigation
├─ core/
│  ├─ Clock.kt                    # 可注入时间源（测试可控）
│  └─ TimeFmt.kt                  # 时间/日期格式化
├─ data/
│  ├─ db/PlanListDatabase.kt      # Room 数据库（导出 schema 供迁移测试）
│  ├─ db/entity/*.kt              # PlanGroupEntity / PlanItemEntity / ItemLogEntity
│  ├─ db/dao/PlanDao.kt, LogDao.kt
│  ├─ repo/PlanRepository.kt, LogRepository.kt, SettingsRepository.kt
│  └─ backup/BackupCodec.kt       # JSON 导入导出（本地文件，无网络）
├─ domain/
│  ├─ model/Recurrence.kt, TodayGroup.kt, DayStatus.kt
│  ├─ RecurrenceCalculator.kt     # ★纯函数：某计划是否落在某天 / 下一次发生时刻
│  ├─ MacroMath.kt                # ★纯函数：计划 vs 已吃的宏量汇总
│  ├─ StreakCalculator.kt         # ★纯函数：连续打卡天数、完成率
│  └─ TodayAssembler.kt           # 组合计划+日志 → 今日视图模型（NOW/OVERDUE/UP NEXT）
├─ reminder/
│  ├─ ReminderScheduler.kt        # 滚动 7 天窗口排程 / 取消 / 权限降级
│  ├─ ReminderReceiver.kt         # 闹钟触发 → 通知 + 震动
│  ├─ ReminderActionReceiver.kt   # 通知按钮：完成 / 稍后 10 分钟
│  ├─ RescheduleReceiver.kt       # 开机 / 更新 / 改时间 / 改时区 → 重排
│  ├─ DailyTopUpReceiver.kt       # 每日 00:05 自续期补齐窗口
│  └─ NotificationFactory.kt      # 渠道、震动模式、免打扰策略
└─ ui/
   ├─ theme/                      # Color / Type / Theme（深色优先）
   ├─ nav/AppNav.kt               # 底部 4 Tab：今日 / 计划 / 历史 / 设置
   ├─ today/TodayScreen.kt        # ★核心：进度环 + 语义标签 + 长按完成
   ├─ plan/PlanListScreen.kt, GroupEditScreen.kt
   ├─ history/HistoryScreen.kt    # 月历 + 日详情
   └─ settings/SettingsScreen.kt  # 权限引导 + HyperOS 适配 + 备份还原
```

**分层依赖方向**：`ui → domain ← data`，`reminder → domain + data`。`domain` 层不依赖 Android 框架，因此可跑纯 JVM 单元测试。

---

## 5. 提醒引擎设计（本项目最高风险区）

### 5.1 调度策略

1. **滚动 7 天窗口**：每次 App 启动 / 每个闹钟触发 / 每日 00:05 补齐闹钟唤醒时，调用 `ReminderScheduler.scheduleWindow(7)`。
2. 对窗口内每一天，用 `RecurrenceCalculator` 算出当天应有的组，按 `timeOfDay − reminderOffsetMinutes` 计算实际触发时刻，只对**未来**时刻注册精确闹钟。
3. 每个闹钟的 `requestCode` = `groupId` 与 `LocalDate` 的稳定哈希 → 保证重复注册自动覆盖、可单独取消。
4. `PendingIntent.FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`；`Intent` 里带 `EXTRA_GROUP_ID` 与 `EXTRA_DATE`，接收端直接查库，**不携带过期文案**。
5. **权限降级**：`AlarmManager.canScheduleExactAlarms() == false` 时自动改用 `setAndAllowWhileIdle`（宽松，±数分钟），并在设置页顶部显示黄色提示条 + 一键跳转授权页。
6. **不使用 WorkManager**：它的周期任务最小间隔 15 分钟且 Doze 下不可靠，做不了精确提醒；
   而它作为"补齐窗口"的兜底又会把 `ACCESS_NETWORK_STATE` 与 `FOREGROUND_SERVICE`
   合并进 manifest，对一个宣称纯本地离线的应用是没必要的噪声。改用一次性精确闹钟自续期。

### 5.2 通知与震动

- 渠道 `reminders`：`IMPORTANCE_HIGH`、`enableVibration(true)`、震动模式 `[0, 400, 200, 400]`、`setSound` 默认提示音。
- 通知使用 `NotificationCompat.Builder` + `setCategory(CATEGORY_REMINDER)` + `setVisibility(VISIBILITY_PUBLIC)`，锁屏可见。
- 内联动作：**完成整组**（`ReminderActionReceiver` → 写 `item_log` → 自动取消该组后续通知）、**稍后 10 分钟**（重排一次性闹钟）、**打开**。
- 额外主动震动：API 31+ 用 `VibratorManager.getDefaultVibrator()`，26–30 用 `VIBRATOR_SERVICE`，`VibrationEffect.createWaveform`。渠道震动可能被"静音模式"吞掉，主动震动作为冗余保证「到点必震」。
- 同刻多组：使用**不同 notificationId**（= groupId），保证互不覆盖。

### 5.3 HyperOS 3 适配（实测要点，写入设置页引导）

| 风险 | 现象 | 对策 |
|------|------|------|
| 后台清理 | 息屏一夜后提醒全部不响 | 设置页「一键引导」：① 电池 → 应用 → 计划清单 → **无限制**；② 应用管理 → **自启动**允许；③ 最近任务列表**加锁**；④ 关闭「省电模式」对后台的限制 |
| 精确闹钟权限 | Android 14+ 默认拒绝，改为模糊提醒 | `SCHEDULE_EXACT_ALARM` + `canScheduleExactAlarms()` 检测 + 跳 `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` |
| 通知权限 | Android 13+ 默认关闭，提醒静默 | 首启请求 `POST_NOTIFICATIONS`，拒绝则设置页常驻提示 |
| 「强行停止」 | 用户手动强停后所有闹钟被系统清除，且不会自动恢复 | 应用内说明；下次打开 App 自动重排（已实现） |
| 免打扰 / 静音 | 有通知无震动 | 主动震动冗余 + 渠道说明 |
| 时区/时间被改 | 提醒时刻漂移 | `ACTION_TIME_CHANGED`/`TIMEZONE_CHANGED` 广播 → 全量重排 |
| 重启 | 闹钟丢失 | `BOOT_COMPLETED` + 小米 `QUICKBOOT_POWERON` 广播 → 重排 |

---

## 6. 数据模型（详见 `docs/DATA_MODEL.md`）

三张核心表 + 一张设置表：

- `plan_group`：计划组。字段 `id, name, kind(MEAL|WORKOUT), timeOfDay(分钟), recurrenceType, intervalDays, weekdaysMask(位0=周一), monthDays(csv), anchorDate, endDate, enabled, reminderOffsetMinutes, sortOrder`
- `plan_item`：组内条目。字段 `id, groupId(FK), name, amountText, calories, proteinG, carbsG, fatG, sets, reps, sortOrder`
- `item_log`：打卡日志。字段 `id, date(yyyy-MM-dd), groupId, itemId, status(COMPLETED|SKIPPED), loggedAt`，**唯一索引 `(date, itemId)`** —— 天然幂等，重复点击/通知重复触发不会产生脏数据
- `app_settings`：DataStore（主题、单位、补打卡天数、提醒总开关）

**关键设计决策**：**不存储"某天应有哪些组"**。日程是纯函数 `(计划规则, 日期) → 应做项`，因此：改规则立即对历史/未来生效、无需迁移历史数据、无"生成失败"状态。日志仅记录"做了什么"，不记录"应该做什么"。

---

## 7. UI 规格（参考 Macro7，详见 `docs/UI_SPEC.md`）

**4 个底部 Tab**：

1. **今日**：顶部日期 + 进度环（中心显示 `已吃/计划 kcal` 或 `已完成 x/y 组`）；下方按时间排列组卡片，带语义标签 **NOW**（高亮描边）/ **OVERDUE**（红色）/ **UP NEXT**（灰）/ **DONE**（绿+勾）；点卡片展开条目清单，条目左侧圆形复选框；卡片右侧「长按 500ms 完成整组」。
2. **计划**：分组列表 + FAB 新建；编辑页含名称、类型、时间选择器（Material3 TimePicker）、5 种重复规则选择器（选 Weekdays 出周一~周日胶囊；选 Month Days 出 1–31 网格；选 Every N Days 出间隔输入 + 起始日期）、提醒提前量（0/10/30 分钟）、条目增删改。
3. **历史**：月历网格，每格按当天完成度着色（全完成/部分/未完成/无计划）；左右切换月份；点某天进入日详情，可补勾（受 `allowCatchUpDays` 设置限制，默认 ±1 天，与 Macro7 一致）。
4. **设置**：权限状态卡（通知/精确闹钟/电池优化，未授权显示红色 + 一键跳转）、HyperOS 适配引导清单、补打卡天数、导入/导出 JSON、关于。

**视觉规范**：深色为默认；背景 `#101214`，卡片 `#1A1D21`，圆角 20–28dp，主色 `#4ADE80`（完成）/ `#F87171`（逾期）/ `#60A5FA`（当前）；标题 28sp SemiBold，数值 40sp Bold；间距栅格 4/8/16/24。

---

## 8. 里程碑与排期

| 里程碑 | 内容 | 交付物 / 验证方式 | 预估 |
|--------|------|-------------------|------|
| **M0 工具链** | 装 JDK 17（Temurin）+ Android SDK（cmdline-tools / platform-tools / build-tools;35.0.0 / platforms;android-35）+ Gradle 8.9，生成 `gradlew` | `.	ools\setup-toolchain.ps1` 跑通；`gradlew -v` 输出 JDK 17 | 0.5 天（下载时长取决于网速） |
| **M1 骨架跑通** | 工程/依赖/主题/空 4 Tab；真机安装空白 App 成功 | `gradlew assembleDebug` 产出 APK，`adb install` 成功并启动 | 0.5 天 |
| **M2 数据层** | Room 三表 + DAO + Repository + 备份编解码；**先写测试**再写实现 | `gradlew test` 绿：DAO 测试（Robolectric）+ 备份往返测试 | 1 天 |
| **M3 领域算法** | `RecurrenceCalculator` / `MacroMath` / `StreakCalculator` / `TodayAssembler` | `gradlew test` 绿：≥40 条边界用例（见 `docs/TEST_PLAN.md`） | 1 天 |
| **M4 计划配置 UI** | 计划列表 + 编辑页（5 种重复规则 + 条目编辑 + 校验） | 真机可增删改计划；`gradlew test` 含 ViewModel 测试 | 1.5 天 |
| **M5 今日打卡 UI** | 进度环、语义标签、勾选、长按完成整组、±1 天补勾 | Compose UI 测试 + 真机手工验证 | 1.5 天 |
| **M6 提醒引擎** | 排程/通知/震动/动作按钮/开机重排/12h 补齐/权限降级 | Robolectric 影子 AlarmManager 断言 + **真机连续 3 天实测** | 2 天 |
| **M7 历史与统计** | 月历着色、日详情、连续天数与完成率 | 单元测试 + 真机目视 | 1 天 |
| **M8 权限与 HyperOS 收尾** | 设置页权限卡、引导清单、深色打磨、安装包混淆与签名 | **§10 真机验收矩阵全绿** | 1 天 |
| **M9 自用版交付** | 生成签名 release APK（可选 debug）、README 使用手册 | `PlanList-1.0.apk` + 安装说明 | 0.5 天 |

合计约 **11.5 人日**（不含 M0 的下载等待）。建议按里程碑逐个验收，每个里程碑结束都产出可安装 APK。

---

## 9. 测试策略（完整用例见 `docs/TEST_PLAN.md`）

分四层，**测试优先落在纯函数层**（成本最低、回归价值最高）：

1. **纯 JVM 单元测试（`app/src/test`，主力）**
   - `RecurrenceCalculator`：5 种规则 × 锚点过去/未来/当天 × 结束日期 × 星期掩码 × 月末（`MONTH_DAYS=31` 在 2 月、29 日在平年）→ 是否发生
   - `nextOccurrence`：从 23:59、跨月、跨年、闰年 2/29 起算；DST 时区（`America/New_York`）下时间不漂移
   - `MacroMath`：部分条目缺宏量、全缺 → 降级为组数计数；除零；四舍五入
   - `StreakCalculator`：中间断档、今天未完成是否计入、无计划日是否算休息
   - `BackupCodec`：导出→导入往返等价；版本号不匹配；损坏 JSON 不崩溃
2. **Robolectric 集成测试**：DAO 唯一索引幂等性、事务回滚、AlarmManager 排程数量与时刻断言、通知渠道创建、`canScheduleExactAlarms=false` 的降级分支
3. **Compose UI 测试（`app/src/androidTest`）**：今日页三种语义标签渲染、勾选后进度环更新、长按 500ms 触发/短按不触发、计划编辑校验（名称空、时间为空）
4. **真机手工验收矩阵**：见 §10

**质量门禁**：每个里程碑必须 `gradlew test` 全绿 + `assembleDebug` 无警告级错误；M6 起追加真机实测。

---

## 10. 真机验收矩阵（HyperOS 3，逐项打勾）

| # | 场景 | 期望 |
|---|------|------|
| 1 | 新建「午餐 12:30」+「训练 19:00」，提醒提前 0 分钟 | 12:30:00±10s 通知弹出并震动 |
| 2 | 提前 30 分钟提醒 | 12:00 触发 |
| 3 | 锁屏 / 息屏状态 | 锁屏可见通知，震动正常 |
| 4 | 免打扰模式 | 通知静默但震动仍触发（主动震动兜底） |
| 5 | 点击通知「完成整组」 | 今日页该组变绿；该组当日后续提醒不再响 |
| 6 | 点击「稍后 10 分钟」 | 10 分钟后再次提醒 |
| 7 | 同刻 3 个组 | 3 条独立通知，互不覆盖 |
| 8 | 手机重启 | 提醒全部仍在 |
| 9 | 手动修改系统时间（+2h） | 重排后仍按计划时刻触发 |
| 10 | 修改时区 | 同上 |
| 11 | 电池优化未豁免 | 记录实际表现（预期可能延迟），豁免后必须精准 |
| 12 | App 从最近任务划掉 | 提醒仍在 |
| 13 | App 被「强行停止」 | 记录行为（系统级清除，属已知限制），重开 App 后恢复 |
| 14 | 关闭通知权限 | 设置页红色提示 + 跳转授权 |
| 15 | 拒绝精确闹钟权限 | 降级为宽松提醒 + 提示条 |
| 16 | 连续 3 天不打开 App | 每天提醒均准时（**最重要的稳定性验收**） |
| 17 | 跨天补打卡 | 昨天的最后一组今天可补勾，落在正确日期 |
| 18 | 月末计划（每月 31 日） | 2 月不触发、不崩溃 |
| 19 | 飞行模式全天 | 所有功能正常（证明离线） |
| 20 | 查看 APK 权限清单 | **不含 INTERNET 权限** |

---

## 11. 风险与对策

| 风险 | 等级 | 对策 |
|------|------|------|
| HyperOS 后台杀进程导致提醒丢失 | **高** | 三重保险：精确闹钟 + 每日自续期补齐 + 开机/改时间重排；设置页强引导白名单；M6 连续 3 天实测作为准入条件 |
| 系统「强行停止」清除闹钟 | 中 | 无法绕过（Android 限制）；应用内说明 + 重开 App 自动重排 |
| 精确闹钟权限被拒 | 中 | 自动降级 `setAndAllowWhileIdle` + 显式提示，绝不静默失败 |
| JDK 16 不兼容 AGP 8.x | 中 | M0 安装 JDK 17 并只对 Gradle 生效（`org.gradle.java.home`），不动系统 JAVA_HOME，避免影响你其他项目 |
| Room 后续加表（体重/围度） | 低 | P0 就开启 `exportSchema` 并写迁移测试，避免日后 `fallbackToDestructiveMigration` 丢数据 |
| 范围蔓延到 Macro7 全功能 | 中 | §1.2 明确不做清单；新需求一律进 P2 待办 |
| 首次签名与安装被 MIUI 拦截 | 低 | 使用 debug 签名自用；或生成 release keystore 并在手机端允许未知来源 |

---

## 12. 交付物清单

1. 完整可编译 Android 工程（本目录）
2. 本计划 `PLAN.md` + `docs/TEST_PLAN.md` + `docs/DATA_MODEL.md` + `docs/REMINDER_AND_HYPEROS.md`
3. `tools/setup-toolchain.ps1`（一键装 JDK17+SDK）、`tools/build-apk.ps1`、`tools/install-to-phone.ps1`
4. 单元测试与 UI 测试源码
5. 最终 `PlanList-1.0.apk` + 安装/使用说明

---

## 13. 下一步（需要你点头）

- **执行 M0**：是否授权我运行 `tools/setup-toolchain.ps1`（下载约 5–6 GB 到 `D:\Android\Sdk`）？授权后我可一路编译出 APK 并跑测试。
- 或：你已装 Android Studio / 其他 SDK，我扫描后复用（回复即可，我改 `local.properties`）。
- 另：D3 功能范围、D4 应用名如要调整，现在改成本最低。
