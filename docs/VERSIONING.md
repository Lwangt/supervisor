# 版本管理与发布流程

本文是 `AGENTS.md` 第一节的展开说明，供需要细节时查阅。

## 1. 单一事实来源

版本号只有一个来源：`app/build.gradle.kts` 中的 `versionCode` 与 `versionName`。

- `versionCode`：整数，**只能增加**，Android 用它判断"哪个更新"
- `versionName`：给人看的字符串，形如 `0.2.0`

`CHANGELOG.md` 与 git tag 必须与之对应（tag 形如 `v0.2.0`）。

## 2. 一次完整发布的步骤

```
1. 改代码
2. 跑 tools/build-apk.ps1（含单元测试）→ 必须全绿
3. 写 CHANGELOG：把「未发布」段落的改动整理成新版本段落
4. 递增 versionCode 与 versionName
5. commit（格式见 AGENTS.md 第三节）
6. git tag vX.Y.Z
7. git push origin main --tags
8. 编译 release APK
9. 创建 GitHub Release 并上传 APK   ← 手机端从这里下载安装
```

`tools/release.ps1` 把第 3~9 步自动化了。

## 3. 为什么要上传到 GitHub Release

需求是"开发完就能在手机上预览测试，不用装 Android Studio"。可行路径有三条：

| 方式 | 是否需要电脑 | 是否需 Android Studio | 评价 |
|------|-------------|---------------------|------|
| adb install（USB 连接电脑） | 需要 | 不需要 | 适合开发调试 |
| 手动拷贝 APK 到手机再安装 | 需要 | 不需要 | 最笨但最稳 |
| **GitHub Release 下载 APK** | 不需要 | 不需要 | **推荐**：手机浏览器直接下载安装 |

第三条让"改完代码 → 手机上装新版"变成：电脑推送 → 手机刷新 Release 页 → 下载安装。

## 4. 版本号与数据库迁移的联动

任何时候递增 Room 的 `@Database(version = N)`，都必须：

1. 写 `Migration(N-1, N)` 并加入 `Room.databaseBuilder(...).addMigrations(...)`
2. 在 `app/src/test` 里补一个迁移测试
3. 在 CHANGELOG 的「变更」里写明数据结构变化

**绝对禁止**用 `fallbackToDestructiveMigration()` 图省事 —— 那会静默清空用户全部打卡历史。

## 5. 备份格式版本

`BackupCodec.CURRENT_VERSION` 独立于 App 版本号：

- 新增可选字段（老备份仍能导入）→ **不递增**
- 删除或改语义字段（老备份无法正确导入）→ **递增**，并保证 `decode` 对老版本给出可读提示而不是崩溃
