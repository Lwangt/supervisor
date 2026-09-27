# 测试计划

## 1. 分层策略

| 层 | 位置 | 工具 | 覆盖目标 | 运行耗时 |
|----|------|------|----------|----------|
| L1 纯逻辑单元测试 | `app/src/test` | JUnit4 + `kotlinx-coroutines-test` | `domain/` 全部纯函数、备份编解码、ViewModel | 秒级 |
| L2 Android 集成测试 | `app/src/test`（Robolectric） | Robolectric 4.14.1 + Room in-memory | DAO 约束、AlarmManager 排程、通知渠道、权限降级分支 | 十秒级 |
| L3 Compose UI 测试 | `app/src/androidTest` | `createAndroidComposeRule` | 今日页三态渲染、勾选联动、长按手势、编辑页校验 | 需设备/模拟器 |
| L4 真机手工验收 | HyperOS 3 实机 | 人工 + `adb shell dumpsys alarm` | 系统级行为：Doze、重启、权限、DND | 跨天 |

**测试优先级**：把 L1 做厚（回归价值最高、成本最低），L2 覆盖"只有在 Android 上才成立"的约束，
L3 只测关键交互路径，L4 只测系统级行为（无法自动化的部分）。

## 2. L1 用例清单

### 2.1 `RecurrenceCalculatorTest` — 重复规则（约 34 例）

**DAILY**
1. 锚点当天 → true
2. 锚点之前 → false
3. 设置了 `endDate`，endDate 当天 → true（含端点）
4. endDate 次日 → false

**EVERY_N_DAYS**
5. N=2，锚点日 → true
6. N=2，锚点+1 → false
7. N=2，锚点+2 → true
8. N=2，锚点+100（偶数）→ true
9. N=3，锚点+99 → true；+100 → false
10. N=1 等价于 DAILY
11. N=0 或负数（脏数据）→ 钳制为 1，不崩溃
12. 锚点在"今天之后"（计划尚未开始）→ 早于锚点的日期一律 false

**WEEKDAYS**
13. mask 含周一，测试日为周一 → true
14. 同日 mask 不含周一 → false
15. mask = 全 7 位 → 每天 true
16. mask = 0（未选任何天）→ 从不 true，且 UI 应阻止保存
17. 周一=bit0 的序数验证（防止与 `Calendar.MONDAY=2` 混淆）——**回归防御重点**

**MONTH_DAYS**
18. `"1,15"` → 1 日/15 日 true，2 日 false
19. `"31"` 在 2 月 → **false，不崩溃**（无 31 日）
20. `"31"` 在 4 月 → false；在 1 月 → true
21. `"29"` 在平年 2 月 → false；闰年 2 月 → true
22. `""` 空串 → 从不 true，不崩溃
23. `"1, 15 ,31"` 含空格 → 正确解析（防御脏数据）
24. 非法文本 `"abc"` → 忽略非法项，不崩溃

**ONCE**
25. 等于 anchorDate → true，其余 false
26. 晚于 anchorDate → false

**通用**
27. `enabled = false` → 无论规则如何均 false
28. anchorDate 与 endDate 同一天
29. endDate 早于 anchorDate（脏数据）→ 不崩溃

### 2.2 `NextOccurrenceTest` — 下一次发生时刻（约 12 例）

30. DAILY，`from` 早于今日时刻 → 返回今日该时刻
31. DAILY，`from` 晚于今日时刻 → 返回**明日**该时刻（严格大于，不返回过去）
32. `from` 恰等于今日时刻 → 返回明日（避免立即重复触发）
33. 从 23:59 起算 DAILY 00:00 → 次日 00:00
34. 跨月：3/31 起算 `MONTH_DAYS="1"` → 4/1
35. 跨年：12/31 起算 `WEEKDAYS` 周一 → 次年正确日期
36. 闰年：2028/2/28 起算 `MONTH_DAYS="29"` → 2028/2/29
37. 平年：2027/2/28 起算 `MONTH_DAYS="29"` → 2027/3/29
38. 超过搜索上限（如 endDate 已过）→ 返回 null，不无限循环
39. **DST 时区**（`America/New_York`）跨春令时切换日 → 挂钟时刻保持 12:30 不变
40. **DST 时区** 跨冬令时切换日（重复小时）→ 不重复触发、不崩溃
41. `ZoneId` 注入验证：同一输入在 `Asia/Shanghai` 与 `America/New_York` 得到各自的本地时刻

### 2.3 `MacroMathTest`（约 10 例）

42. 全部填满 → 求和正确
43. 部分为 null → null 按 0 计
44. 全为 null → `hasAnyMacro = false`
45. 任一非 null → `hasAnyMacro = true`
46. 空列表 → ZERO，`hasAnyMacro = false`
47. `calories` 为 0（明确填 0）→ 仍算"已填"，进入宏量模式
48. 小数求和精度（0.1+0.2 类问题，用 BigDecimal 或容忍度断言）
49. 完成度分母为 0 → 返回 0 而非 NaN/崩溃
50. 只完成部分条目 → eaten 只统计已完成
51. 条目被标记 SKIPPED → 不计入 eaten，但计入分母

### 2.4 `StreakCalculatorTest`（约 8 例）

52. 连续 3 天全完成 → streak = 3
53. 中间断 1 天 → 只算到断点
54. 今天未完成但昨天完成 → streak 仍从昨天起算（今天不打断连续性）
55. 无计划日（REST）不打断 streak
56. 部分完成的一天是否计入（产品决策：**不计入**，但也不重置——即"跳过"）
57. 全部无计划 → streak = 0
58. 完成率为 4 天中 3 天 → 0.75
59. 未来日期不参与统计

### 2.5 `BackupCodecTest`（约 8 例）

60. 导出 → 导入 → 数据等价（round-trip）
61. version 高于支持 → 抛可读异常
62. 损坏 JSON → 抛可读异常，不崩溃
63. 空 groups/logs → 合法
64. 重复导入同一备份 → 不产生重复计划（按业务键合并）
65. 导入时 `item_log` 与已有冲突 → UPSERT 取备份值
66. 特殊字符/emoji 名称 → 往返一致
67. 超大时间戳 / 负时间戳 → 不崩溃

### 2.6 ViewModel 测试（约 8 例）

68. `TodayViewModel` 切换日期 → 数据随 `date` 重新组合
69. 勾选条目 → 进度环数值更新
70. 超过 `catchUpDays` 的日期不可编辑
71. 保存组时名称为空 → 校验失败
72. `WEEKDAYS` 类型未选任何天 → 校验失败
73. `intervalDays < 1` → 校验失败
74. 删除组 → 其条目级联删除，但 `item_log` 保留
75. 提醒总开关关闭 → 不调用 `ReminderScheduler`

## 3. L2 Robolectric 用例

| # | 用例 | 断言 |
|---|------|------|
| R1 | 同一 `(date, itemId)` 连续 UPSERT 3 次 | `item_log` 仍只有 1 行，status 为最后一次 |
| R2 | 删除 `plan_group` | `plan_item` 级联删除；`item_log` 行数不变 |
| R3 | 批量写入中途抛异常 | 事务回滚，无半写状态 |
| R4 | `scheduleWindow(7)` | `ShadowAlarmManager` 中排程数 = 7 天内应发生组数；触发时刻 = 设置值 |
| R5 | `canScheduleExactAlarms() = false` | 全部改用宽松 API，**不抛异常、不静默丢失** |
| R6 | `cancelGroup(groupId)` | 该组所有 PendingIntent 被清除，其他组不受影响 |
| R7 | 通知渠道创建 | `reminders` 渠道存在，importance = HIGH，震动开启 |
| R8 | 通知动作 `COMPLETE_GROUP` | 触发后日志写入、通知被取消 |
| R9 | `RescheduleReceiver` 收到 `BOOT_COMPLETED` | 重新排程数 > 0 |
| R10 | `scheduleDailyTopUp()` | 每天 00:05 排下唯一的补齐闹钟，重复调用不堆叠；与普通提醒闹钟共存 |
| R11 | 数据库迁移（`MigrationTestHelper`） | v1 schema 与实体一致（迁移测试骨架，后续加表时启用） |

## 4. L3 Compose UI 用例

| # | 用例 | 断言 |
|---|------|------|
| C1 | 今日页渲染 3 组（NOW/OVERDUE/UP_NEXT） | 三个语义标签文本各出现一次，节点唯一 |
| C2 | 勾选一个条目 | 进度环进度值变化；该条目复选框 `isOn = true` |
| C3 | 长按组卡片 500ms | `onFinishGroup` 回调触发一次，触感反馈被调用 |
| C4 | 短按（100ms）组卡片 | `onFinishGroup` **不**触发（防误触核心用例） |
| C5 | 无计划的一天 | 显示空状态文案，不显示进度环 |
| C6 | 编辑页名称为空点保存 | 显示错误提示，不导航返回 |
| C7 | 选择 WEEKDAYS 但未选天 | 保存被阻止 |
| C8 | 历史月历 | 给定 3 天不同状态日志，对应格子语义标签正确 |
| C9 | 设置页权限未授予 | 权限卡显示"未授权"红色状态与跳转按钮 |

## 5. 质量门禁

- 每个里程碑：`gradlew test` 全绿
- M4 起：`gradlew assembleDebug` 无错误；L3 用例在真机上通过
- M6 起：真机验收矩阵（PLAN.md §10）相关行全绿才可进入下一里程碑
- 任何 `TODO`/`FIXME` 必须登记在当前里程碑或明确推迟

## 6. 如何运行

```powershell
# L1 + L2（无需手机）
.\gradlew.bat test --info

# 单个测试类
.\gradlew.bat test --tests "*RecurrenceCalculatorTest*"

# 覆盖率报告（可选）
.\gradlew.bat testDebugUnitTest jacocoTestReport

# L3（需连接设备，且已开启 USB 调试）
.\gradlew.bat connectedDebugAndroidTest
```
