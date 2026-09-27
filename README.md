# 计划清单 PlanList

自用的**饮食 + 运动计划打卡 App**，纯离线、无条数限制、到点通知 + 震动提醒。
对标 Macro7，但移除其"计划条数上限 / 订阅收费"限制。

> ✅ **v0.1.0 已发布**，127 条单元测试通过，Release APK 仅 1.33 MB 且**无 INTERNET 权限**。
> 手机直接下载安装：<https://github.com/Lwangt/supervisor/releases/tag/v0.1.0>
> 当前进度与未验证项详见 [STATUS.md](STATUS.md)。

- 目标设备：小米 HyperOS 3（Android 16）
- 技术栈：Kotlin 2.1 + Jetpack Compose (Material 3) + Room
- 最低支持：Android 8.0（API 26）
- 网络：**APK 不声明 INTERNET 权限**，物理级离线

## 文档

| 文件 | 内容 |
|------|------|
| [STATUS.md](STATUS.md) | **当前进度与验证情况（先看这个）** |
| [PLAN.md](PLAN.md) | 开发与测试总计划、里程碑、竞品对标、风险 |
| [docs/TEST_PLAN.md](docs/TEST_PLAN.md) | 分层测试策略与完整用例清单 |
| [docs/DATA_MODEL.md](docs/DATA_MODEL.md) | 数据库表结构与设计决策 |
| [docs/REMINDER_AND_HYPEROS.md](docs/REMINDER_AND_HYPEROS.md) | 提醒引擎与小米 HyperOS 适配指南 |

## 快速开始

```powershell
# 1) 安装构建工具链（JDK17 + Android SDK + Gradle，约 5-6 GB，装到 D:\Android）
.\tools\setup-toolchain.ps1

# 2) 编译 debug APK
.\tools\build-apk.ps1

# 3) 手机开启 USB 调试后安装
.\tools\install-to-phone.ps1

# 4) 跑单元测试
.\gradlew.bat test
```

## 首次使用（手机端必做，否则提醒会被系统杀掉）

1. 打开 App → **设置** 页顶部会列出未授权项，逐项点击授权：
   - 通知权限（Android 13+ 必须）
   - 精确闹钟权限（Android 12+ 必须，否则只能模糊提醒）
   - 电池优化 → 选择「无限制」
2. 手动补充（HyperOS 特有，App 无法直接跳转）：
   - 设置 → 应用设置 → 应用管理 → 计划清单 → **自启动** 打开
   - 最近任务列表 → 下拉 App 卡片 → **加锁**
3. 建议先建一个「测试提醒」（1 分钟后），确认能弹通知 + 震动，再开始正常使用。

## 目录结构

```
app/src/main/java/com/planlist/app/
  core/       时间源与格式化
  data/       Room 实体/DAO/仓库/本地备份
  domain/     纯函数算法（可单元测试）：重复规则、宏量、连续打卡
  reminder/   闹钟排程、通知、震动、开机重排
  ui/         Compose 界面（今日/计划/历史/设置）
```
