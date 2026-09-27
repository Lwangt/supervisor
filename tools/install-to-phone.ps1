<#
.SYNOPSIS
    把编译好的 APK 安装到已连接的小米手机（需开启 USB 调试）。
.EXAMPLE
    .\tools\install-to-phone.ps1
.EXAMPLE
    .\tools\install-to-phone.ps1 -ApkPath 'D:\PlanList.apk'
#>
[CmdletBinding()]
param(
    [string]$SdkRoot = 'D:\Android\Sdk',
    [string]$ApkPath,
    [ValidateSet('debug', 'release')]
    [string]$Variant = 'debug'
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $ProjectRoot 'settings.gradle.kts'))) { $ProjectRoot = $PSScriptRoot }

function Die($m) { Write-Host ('[X] ' + $m) -ForegroundColor Red; exit 1 }

$adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { Die ('未找到 adb: ' + $adb + ' —— 请先运行 .\tools\setup-toolchain.ps1') }

if (-not $ApkPath) {
    $dir = Join-Path $ProjectRoot ('app\build\outputs\apk\' + $Variant)
    $found = Get-ChildItem -Path $dir -Filter '*.apk' -ErrorAction SilentlyContinue |
             Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $found) { Die ('未找到 APK，请先运行 .\tools\build-apk.ps1') }
    $ApkPath = $found.FullName
}
if (-not (Test-Path $ApkPath)) { Die ('APK 不存在: ' + $ApkPath) }

Write-Host ('检测设备...') -ForegroundColor Cyan
& $adb start-server | Out-Null
& $adb devices -l
$devices = & $adb devices | Select-String -Pattern 'device$'
if (-not $devices) {
    Write-Host ''
    Write-Host '[X] 没有检测到已授权的设备。请检查:' -ForegroundColor Red
    Write-Host '    1. 手机 设置 -> 关于手机 -> 连点 MIUI 版本 7 次 开启开发者选项'
    Write-Host '    2. 开发者选项 -> USB 调试 打开；USB 安装 打开'
    Write-Host '    3. 数据线连接后，手机上弹出的「允许 USB 调试」要点允许'
    Write-Host '    4. HyperOS 若提示「USB 用途」，选择「传输文件」'
    exit 1
}

Write-Host ''
Write-Host ('安装: ' + $ApkPath) -ForegroundColor Cyan
& $adb install -r $ApkPath
if ($LASTEXITCODE -ne 0) { Die '安装失败（若提示签名冲突，先卸载手机上旧版本）' }

Write-Host ''
Write-Host '[OK] 安装完成。' -ForegroundColor Green
Write-Host '下一步（重要，否则提醒会被系统杀掉）:'
Write-Host '  1. 打开 App -> 设置页，逐项授权通知权限、精确闹钟、电池「无限制」'
Write-Host '  2. 系统设置 -> 应用管理 -> 计划清单 -> 自启动 打开'
Write-Host '  3. 最近任务列表下拉卡片 -> 加锁'
Write-Host ''
Write-Host '启动应用:'
& $adb shell monkey -p com.planlist.app -c android.intent.category.LAUNCHER 1 | Out-Null
