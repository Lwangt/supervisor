<#
.SYNOPSIS
    编译 PlanList APK。
.EXAMPLE
    .\tools\build-apk.ps1                 # debug APK + 单元测试
.EXAMPLE
    .\tools\build-apk.ps1 -Variant release
.EXAMPLE
    .\tools\build-apk.ps1 -SkipTests      # 只编译，快速迭代
#>
[CmdletBinding()]
param(
    [string]$JdkRoot = 'D:\Android\jdk-17',
    [string]$SdkRoot = 'D:\Android\Sdk',
    [ValidateSet('debug', 'release')]
    [string]$Variant = 'debug',
    [switch]$SkipTests,
    [switch]$Clean
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $ProjectRoot 'settings.gradle.kts'))) { $ProjectRoot = $PSScriptRoot }

function Die($m) { Write-Host ('[X] ' + $m) -ForegroundColor Red; exit 1 }

if (-not (Test-Path (Join-Path $JdkRoot 'bin\java.exe'))) {
    Die ('未找到 JDK 17: ' + $JdkRoot + ' —— 请先运行 .\tools\setup-toolchain.ps1')
}
if (-not (Test-Path (Join-Path $SdkRoot 'platform-tools'))) {
    Write-Host ('[!] 未找到 Android SDK: ' + $SdkRoot + ' —— 请先运行 .\tools\setup-toolchain.ps1') -ForegroundColor Yellow
}

$env:JAVA_HOME = $JdkRoot
$env:ANDROID_HOME = $SdkRoot
$env:ANDROID_SDK_ROOT = $SdkRoot
$env:PATH = (Join-Path $JdkRoot 'bin') + ';' + $env:PATH

$gradlew = Join-Path $ProjectRoot 'gradlew.bat'
if (-not (Test-Path $gradlew)) {
    Die '未找到 gradlew.bat —— 请先运行 .\tools\setup-toolchain.ps1 生成 wrapper'
}

$tasks = @()
if ($Clean) { $tasks += 'clean' }
if (-not $SkipTests) { $tasks += 'testDebugUnitTest' }
if ($Variant -eq 'release') { $tasks += 'assembleRelease' } else { $tasks += 'assembleDebug' }

Push-Location $ProjectRoot
try {
    Write-Host ('JDK: ' + $JdkRoot) -ForegroundColor DarkGray
    Write-Host ('任务: ' + ($tasks -join ' ')) -ForegroundColor Cyan
    & $gradlew @tasks
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($code -ne 0) {
    Write-Host ''
    Write-Host ('[X] 构建失败 (exit ' + $code + ')。常见原因:') -ForegroundColor Red
    Write-Host '    - 单元测试未通过 : 看上方 FAILED 的测试类'
    Write-Host '    - 依赖下载失败   : 检查网络/代理'
    Write-Host '    - SDK 组件缺失   : 重新运行 tools/setup-toolchain.ps1'
    exit $code
}

$apkDir = Join-Path $ProjectRoot ('app\build\outputs\apk\' + $Variant)
$apk = Get-ChildItem -Path $apkDir -Filter '*.apk' -ErrorAction SilentlyContinue |
       Sort-Object LastWriteTime -Descending | Select-Object -First 1

Write-Host ''
if ($apk) {
    Write-Host ('[OK] APK: ' + $apk.FullName) -ForegroundColor Green
    Write-Host ('     大小: ' + [math]::Round($apk.Length / 1MB, 2) + ' MB')
    Write-Host ''
    Write-Host '安装到手机: .\tools\install-to-phone.ps1'
} else {
    Write-Host '[!] 构建成功但未找到 APK，请检查 ' $apkDir -ForegroundColor Yellow
}

$testReport = Join-Path $ProjectRoot 'app\build\reports\tests\testDebugUnitTest\index.html'
if (Test-Path $testReport) { Write-Host ('测试报告: ' + $testReport) }
