# 本仓库的协作约定（给 AI 编码助手 / 未来的自己）

> 这是一份**工程 skill**：任何在本仓库里改代码的人或 AI，都必须按这里的流程走。
> 违反流程的提交会被视为不合格。

## 一、最高优先级规则

**每一次代码变更，都必须同时完成下面四件事，缺一不可：**

1. **更新 `CHANGELOG.md`** —— 在文件顶部的 `## [未发布]` 段落里，按 `新增 / 变更 / 修复 / 移除 / 安全` 分类写清楚改了什么、为什么改
2. **递增版本号** —— 修改 `app/build.gradle.kts` 里的 `versionCode`（整数，必增）和 `versionName`
3. **提交到 git** —— commit message 格式见下方
4. **推送到远端** —— `git push origin main`，保持本地与远端一致

推荐直接用自动化脚本一次做完：`tools/release.ps1`（见第四节）。

## 二、版本号规则（语义化版本）

| 场景 | 版本号变化 | 例子 |
|------|-----------|------|
| 修 bug、调文案、改样式 | 修订号 +1 | 0.1.0 → 0.1.1 |
| 新增功能，向后兼容 | 次版本号 +1，修订号归零 | 0.1.1 → 0.2.0 |
| 破坏性变更（数据库结构、备份格式不兼容） | 主版本号 +1 | 0.2.0 → 1.0.0 |

- 主版本为 0 表示尚未正式发布，此时破坏性变更也可以只进次版本号，但**必须在 CHANGELOG 里写明**
- 数据库结构变更（Room `version` 递增）**必须**补 `Migration` 与迁移测试，**禁止** `fallbackToDestructiveMigration()`
- 备份格式变更时 **必须**递增 `BackupCodec.CURRENT_VERSION` 并保证旧版本备份仍可导入（或给出明确报错）

## 三、Commit message 格式

```
<类型>(<范围>): <一句话说明>

<可选的详细说明：为什么这么改、有什么取舍>
```

类型限定为：

| 类型 | 含义 |
|------|------|
| `feat` | 新功能 |
| `fix` | 修 bug |
| `docs` | 只改文档 |
| `refactor` | 重构，行为不变 |
| `test` | 只改测试 |
| `build` | 构建配置、依赖、工具链 |
| `chore` | 杂项 |

范围示例：`today`、`plan`、`history`、`reminder`、`data`、`backup`、`tools`。

示例：

```
feat(reminder): 支持在通知里直接完成整组

用户反馈打完卡还会被提醒。根因是 ReminderReceiver 只发通知不查库。
现在触发前先查 item_log，整组已完成则静默退出。
```

## 四、发布与分发流程

```powershell
# 打一个新版本：自动改版本号 + 写 CHANGELOG + 提交 + 打 tag + 推送 + 编译 APK
.\tools\release.ps1 -Version 0.2.0 -Notes "支持自定义主题色" -Publish
```

- `-Publish` 会调用 GitHub Release API 上传 APK，手机直接打开 Release 页面即可下载安装，无需 Android Studio
- 需要设置环境变量 `GITHUB_TOKEN`（GitHub Personal Access Token，勾选 `repo` 权限）
- 不加 `-Publish` 时，APK 只留在本地 `app/build/outputs/apk/release/`

## 五、修改 PowerShell 脚本时的坑（踩过一次）

Windows PowerShell 5.1 **没有 BOM 就会按系统 ANSI 代码页（中文系统是 GBK）读取 `.ps1`**，
导致脚本里的中文变成乱码、字符串未闭合、直接语法错误。

因此：

1. `tools/*.ps1` 必须保存为 **UTF-8 with BOM**
2. **每次用编辑器或脚本改过 `.ps1` 之后**，都要跑一次 `tools/fix-ps1-encoding.ps1` 补 BOM
3. 另外，`$ErrorActionPreference = 'Stop'` 会让原生命令（`java`、`sdkmanager`、`gradle`）
   写到 stderr 的正常输出被当成 terminating error。脚本里统一用 `'Continue'` +
   显式检查 `$LASTEXITCODE`。

## 六、其他约定

- **绝不引入 INTERNET 权限**。看到 AndroidManifest.xml 里出现 `android.permission.INTERNET` 就是严重回归
- 提醒相关改动必须在真机上验证：至少覆盖重启、改系统时间、连续 3 天不打开 App
- 领域层（`domain/`）必须保持纯函数、不依赖 Android 框架，这样才跑得动单元测试
- 改完跑 `tools/build-apk.ps1`（默认会跑单元测试），测试不通过不许提交

## 七、常用命令

```powershell
.\tools\setup-toolchain.ps1     # 首次：装 JDK17 + Android SDK + Gradle（约 5-6 GB）
.\tools\fix-ps1-encoding.ps1    # 改过 .ps1 之后补 BOM
.\tools\build-apk.ps1           # 编译 + 跑单元测试
.\tools\install-to-phone.ps1    # adb 安装到已连接手机
.\tools\release.ps1 -Version x.y.z -Notes "..." -Publish
```
