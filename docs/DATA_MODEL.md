# 数据模型设计

## 1. 设计原则

**日程不进数据库。** 某个计划组"是否落在 2026-03-05 这一天"完全由纯函数
`RecurrenceCalculator.occursOn(group, date)` 计算得出，不落库。

好处：
- 改规则立即对过去/未来全部生效，无需"重新生成日程"这类易错操作
- 历史数据不受规则变更污染（日志只记"做了什么"，不记"应该做什么"）
- 表结构小、无冗余、无并发写日程的竞态

代价：查询"某天的全部计划"需要把全表 group 读进内存再过滤。个人自用规模（数十条规则）下耗时可忽略，
且可加内存缓存。**若未来规则数超过约 500 条，再引入物化日程表 + 增量重建。**

## 2. 表结构

### 2.1 `plan_group` — 计划组

| 列 | 类型 | 说明 |
|----|------|------|
| `id` | INTEGER PK AUTOINCREMENT | |
| `name` | TEXT NOT NULL | 组名，如"午餐"、"推日训练" |
| `kind` | TEXT NOT NULL | `MEAL` / `WORKOUT`（TypeConverter 存字符串枚举名） |
| `timeOfDay` | INTEGER NOT NULL | 当天时刻，**零点起的分钟数**（12:30 → 750）。用 Int 而非时间类型，便于排序与运算 |
| `recurrenceType` | TEXT NOT NULL | `DAILY` / `EVERY_N_DAYS` / `WEEKDAYS` / `MONTH_DAYS` / `ONCE` |
| `intervalDays` | INTEGER NOT NULL DEFAULT 1 | 仅 `EVERY_N_DAYS` 使用，须 ≥ 1 |
| `weekdaysMask` | INTEGER NOT NULL DEFAULT 0 | 仅 `WEEKDAYS`：**bit0=周一 … bit6=周日**（ISO 顺序，与 `DayOfWeek.value-1` 对齐） |
| `monthDays` | TEXT NOT NULL DEFAULT '' | 仅 `MONTH_DAYS`：逗号分隔，如 `"1,15,31"` |
| `anchorDate` | TEXT NOT NULL | ISO `yyyy-MM-dd`。作为 `EVERY_N_DAYS` 的相位基准、`ONCE` 的唯一日期，以及所有规则的**生效起点** |
| `endDate` | TEXT NULL | ISO 日期，含当天；NULL = 无限 |
| `enabled` | INTEGER NOT NULL DEFAULT 1 | 停用后不提醒、不进今日页，但历史保留 |
| `reminderOffsetMinutes` | INTEGER NOT NULL DEFAULT 0 | 提前分钟数：0=准点，30=提前 30 分钟 |
| `sortOrder` | INTEGER NOT NULL DEFAULT 0 | 同刻手排 |
| `createdAt` | INTEGER NOT NULL | epoch millis |

**索引**：`(enabled, timeOfDay)` — 今日页主要查询路径。
**约束**：`EVERY_N_DAYS` 且 `intervalDays < 1` 视为脏数据，读取时钳制为 1。

### 2.2 `plan_item` — 组内条目

| 列 | 类型 | 说明 |
|----|------|------|
| `id` | INTEGER PK AUTOINCREMENT | |
| `groupId` | INTEGER NOT NULL | FK → `plan_group.id`，`onDelete = CASCADE` |
| `name` | TEXT NOT NULL | "鸡胸肉"、"卧推" |
| `amountText` | TEXT NOT NULL DEFAULT '' | 自由文本："150 g"、"1 勺"、"3 组" |
| `calories` | INTEGER NULL | kcal |
| `proteinG` / `carbsG` / `fatG` | REAL NULL | 克 |
| `sets` | INTEGER NULL | 训练组数 |
| `reps` | TEXT NULL | "8-12" 或 "45 秒"（文本以兼容区间/时长） |
| `sortOrder` | INTEGER NOT NULL DEFAULT 0 | |

**宏量三态**：`null` = 未填。`MacroMath` 的降级策略见 §4 —— 只要组内**任一**条目填了热量，
该组就按"宏量模式"渲染；全为 null 则整组降级为"完成计数模式"（与 Macro7 行为一致）。

**索引**：`groupId`。

### 2.3 `item_log` — 打卡日志

| 列 | 类型 | 说明 |
|----|------|------|
| `id` | INTEGER PK AUTOINCREMENT | |
| `date` | TEXT NOT NULL | ISO `yyyy-MM-dd`，**本地时区的归属日**（关键：跨天补打卡靠它落对日期） |
| `groupId` | INTEGER NOT NULL | 冗余存储，便于按天/按组聚合，避免 join |
| `itemId` | INTEGER NOT NULL | |
| `status` | TEXT NOT NULL | `COMPLETED` / `SKIPPED` |
| `loggedAt` | INTEGER NOT NULL | epoch millis，记录真实操作时刻（与 `date` 可能不同天） |

**唯一索引**：`(date, itemId)` —— 这是幂等性的核心。
通知动作、重复点击、闹钟重复投递都只会 `UPSERT` 同一行，不可能产生重复打卡记录。

**索引**：`date`（今日/历史按天查）、`(date, groupId)`（日历着色聚合）。

> 不设 FK 到 `plan_item`：计划条目被删除后，历史日志必须保留。
> 这是**有意的反范式**，"删了计划 = 抹掉历史"对打卡类 App 是不可接受的。

### 2.4 `app_settings`（DataStore Preferences，非 Room 表）

| key | 类型 | 默认 | 说明 |
|-----|------|------|------|
| `catchUpDays` | Int | 1 | 允许补打卡的天数（±N 天），与 Macro7 的 ±1 天一致 |
| `remindersEnabled` | Boolean | true | 总开关 |
| `showMacros` | Boolean | true | 今日页是否显示宏量环（关闭则显示组数） |
| `themeMode` | String | `DARK` | `DARK` / `LIGHT` / `SYSTEM` |
| `snoozeMinutes` | Int | 10 | "稍后提醒"间隔 |
| `onboarded` | Boolean | false | 是否已走完权限引导 |

## 3. 领域模型（不入库，运行时构造）

```kotlin
enum class DayStatus { DONE, PARTIAL, MISSED, REST, FUTURE, NO_PLAN }

data class TodayGroup(
    val group: PlanGroupEntity,
    val items: List<PlanItemEntity>,
    val itemLogs: Map<Long, LogStatus>,   // itemId -> 状态
    val dueAt: LocalDateTime,
    val slot: TimeSlot,                   // NOW / OVERDUE / UP_NEXT / DONE
) {
    val completedCount get() = itemLogs.count { it.value == LogStatus.COMPLETED }
    val isComplete get() = items.isNotEmpty() && completedCount == items.size
    val plannedMacros: Macros get() = MacroMath.sum(items)
    val eatenMacros: Macros get() = MacroMath.sum(items.filter { itemLogs[it.id] == LogStatus.COMPLETED })
}
```

`TimeSlot` 判定（`TodayAssembler`，纯函数）：以"当前时刻"为基准，
- `isComplete` → `DONE`
- `dueAt <= now` 且未完成 → `OVERDUE`
- 在所有未完成组中 `dueAt` 最小且 `dueAt > now` → `NOW`（Macro7 语义："当前该做的那一组"）
- 其余 → `UP_NEXT`

## 4. 聚合规则（`MacroMath`）

```
Macros(calories: Int, protein: Double, carbs: Double, fat: Double)

sum(items) = items.fold(ZERO) { acc, i ->
    acc + Macros(i.calories ?: 0, i.proteinG ?: 0.0, i.carbsG ?: 0.0, i.fatG ?: 0.0)
}

hasAnyMacro(items) = items.any { it.calories != null || it.proteinG != null
                              || it.carbsG != null || it.fatG != null }
```

- **未填 ≠ 0**：显示层用 `hasAnyMacro` 决定模式，一旦进入宏量模式，未填条目按 0 计入
  （并在 UI 上以"~"前缀提示数值偏低，避免用户误以为精确）。
- 进度环完成度：宏量模式 = `eaten.calories / planned.calories`；无宏量模式 = `完成条目数 / 总条目数`。
- `planned.calories == 0` 时**不得除零**，退回条目数模式。

## 5. 备份格式（`BackupCodec`，kotlinx.serialization）

```json
{
  "format": "planlist-backup",
  "version": 1,
  "exportedAt": 1767225600000,
  "settings": { "catchUpDays": 1, "showMacros": true, "themeMode": "DARK" },
  "groups": [ { "id": 1, "name": "午餐", "items": [ ... ] } ],
  "logs": [ { "date": "2026-03-05", "groupId": 1, "itemId": 2, "status": "COMPLETED", "loggedAt": 1767225600000 } ]
}
```

- 导入采用**按业务键合并**而非按自增 id 覆盖：`plan_group` 以 `(name, kind, timeOfDay)` 匹配复用，
  `item_log` 走 `(date, itemId)` UPSERT。避免"导入两次产生两套重复计划"。
- `version` 高于当前支持值 → 明确报错提示升级 App，**不静默丢弃数据**。
- 解析异常必须被捕获并转为用户可读错误，**绝不允许崩溃**。

## 6. 迁移策略

- Room `version = 1`，`exportSchema = true`，schema JSON 提交到 `app/schemas/`（KSP 参数 `room.schemaLocation`）。
- 每次改表必须新增 `Migration(N, N+1)` 并补 `MigrationTestHelper` 测试。
- **禁止使用 `fallbackToDestructiveMigration()`** —— 对打卡类 App 而言丢历史数据是灾难。
- 预留扩展位（P2 才做，现在不动表）：`body_metric(date, weightKg, bodyFatPct, sleepHours)`、`measurement(date, part, valueCm)`。
