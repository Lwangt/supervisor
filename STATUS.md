# 项目状态

> 记录"什么已经验证过、什么还没有"。以事实为准，不把"写完了"当成"能跑"。

**当前版本：v0.1.0 —— 已发布，可直接在手机上安装预览。**

手机安装地址（不需要 Android Studio）：<https://github.com/Lwangt/supervisor/releases/tag/v0.1.0>

---

## 一、已验证 ✅

| 项目 | 结果 |
|------|------|
| 构建工具链 | JDK 17.0.20.1 + Android SDK (platform-35 / build-tools 35.0.0) + Gradle 8.9，装在 `D:\Android` |
| Kotlin 编译 | 通过，0 错误 |
| Room / KSP 代码生成 | 通过，schema 已导出到 `app/schemas/` |
| 单元测试 | **127 条全部通过**，0 失败（9 个测试类） |
| Debug APK | 17.22 MB，可安装 |
| **Release APK** | **1.33 MB**（R8 压缩后），versionCode=2 / versionName=0.1.0 |
| 权限审计 | 仅 7 项：VIBRATE、POST_NOTIFICATIONS、SCHEDULE_EXACT_ALARM、USE_EXACT_ALARM、RECEIVE_BOOT_COMPLETED、WAKE_LOCK、REQUEST_IGNORE_BATTERY_OPTIMIZATIONS |
| **无 INTERNET 权限** | 由 `aapt2 dump badging` 对二进制 APK 验证确认 |
| 无网络相关权限 | 已移除 WorkManager，因此 `ACCESS_NETWORK_STATE` / `FOREGROUND_SERVICE` 都已消失 |
| GitHub 发布 | tag `v0.1.0` + Release 资产 `PlanList-0.1.0.apk`，下载链接 HTTP 200 |

测试分布：

```
RecurrenceCalculatorTest   36    GroupFormTest            15
LogDaoTest                 13    ReminderSchedulerTest    13
MacroMathTest              12    StreakCalculatorTest     11
TodayAssemblerTest         11    HistoryAssemblerTest      8
BackupCodecTest             8
```

### 单元测试抓到的两个真 bug（已修）

1. **历史页每一天显示成同一种状态** —— `HistoryAssembler` 用同一份 `item_log` 状态表
   遍历所有日期，没有按日期分组，别的日期的打卡漏进了当天。
2. **"唯一一组只做了一半"被显示成完全没做** —— `DaySummary` 的 `isPartial`/`isMissed`
   只看整组完成数，此时 `completedGroups == 0`。

## 二、尚未验证 ⚠️

| 项目 | 状态 | 说明 |
|------|------|------|
| **真机提醒长期稳定性** | ❌ 未做 | 需要连续 3 天不打开 App 确认每天准时响。这是本应用**唯一真正的验收标准**，见 `PLAN.md` §10 |
| 真机安装与启动 | ❌ 未做 | 需要你手机上装一次 |
| 通知 / 震动 / 精确闹钟 | ❌ 未做 | 代码路径有 Robolectric 影子测试覆盖，但没有真机验证 |
| HyperOS 自启动 / 任务加锁 | ❌ 未做 | 系统私有设置，必须手动开 |
| Compose UI 测试 (L3) | ❌ 未写 | `docs/TEST_PLAN.md` 里 C1~C9 已设计但尚未落地 `app/src/androidTest` |
| 数据库迁移测试 | ❌ 未写 | 当前 version=1，加表时必须补 |
| Release 签名 | ⚠️ 用 debug 签名 | 自用足够；若要长期使用建议生成正式 keystore |

## 三、手机上怎么装

1. 手机浏览器打开 <https://github.com/Lwangt/supervisor/releases/tag/v0.1.0>
2. 下载 `PlanList-0.1.0.apk`
3. 安装（HyperOS 提示"未知来源"时允许）

或者用数据线：

```powershell
.\tools\install-to-phone.ps1     # 需要手机开启 USB 调试
```

### 装完必做（否则提醒会被 HyperOS 杀掉）

打开 App → 设置页，逐项授权：

- 通知权限
- 精确闹钟（不授权只能模糊提醒）
- 电池优化 → **无限制**

再手动开两项（系统不允许 App 跳转，设置页有图文引导）：

- 设置 → 应用管理 → 计划清单 → **自启动**
- 多任务界面下拉"计划清单"卡片 → 点锁图标**加锁**

**建议先建一条"1 分钟后"的测试提醒**，确认能弹通知 + 震动，再开始正式使用。

## 四、下一步

按优先级：

1. **真机验收**：装到手机上，跑 `PLAN.md` §10 的 20 项矩阵，重点是第 16 项（连续 3 天）
2. 补 Compose UI 测试（`docs/TEST_PLAN.md` C1~C9）
3. 按实际使用手感打磨 UI（字号、间距、长按时长）
4. 可选：生成正式 keystore；体重/围度记录（P2）

## 五、常用命令

```powershell
.\tools\setup-toolchain.ps1     # 首次装工具链（已装好，重装会自动跳过）
.\tools\fix-ps1-encoding.ps1    # 改过 .ps1 之后补 UTF-8 BOM（PS 5.1 必需）
.\tools\build-apk.ps1           # 编译 + 跑单元测试
.\tools\build-apk.ps1 -Variant release
.\tools\install-to-phone.ps1    # adb 安装到手机
.\tools\release.ps1 -Version 0.1.1 -Notes "..." -Publish
```

版本变更与提交规范见 `AGENTS.md` 与 `docs/VERSIONING.md`。
