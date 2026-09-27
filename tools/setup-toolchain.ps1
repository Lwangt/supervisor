<#
.SYNOPSIS
    一键安装 PlanList 的 Android 构建工具链：JDK 17 + Android SDK + Gradle 8.9。
.DESCRIPTION
    本机现状：系统 JAVA_HOME 指向 JDK 16.0.1（AGP 8.x 要求 JDK 17+），
    且没有 Android SDK / Gradle。本脚本把这些装到 D 盘，不动系统环境变量，
    避免影响你机器上其他项目。

    约需下载 5-6 GB，视网速 10-40 分钟。脚本可重复运行，已装的部分会跳过。
.EXAMPLE
    .\tools\setup-toolchain.ps1
.EXAMPLE
    .\tools\setup-toolchain.ps1 -SdkRoot 'E:\Android\Sdk'
#>
[CmdletBinding()]
param(
    [string]$SdkRoot    = 'D:\Android\Sdk',
    [string]$JdkRoot    = 'D:\Android\jdk-17',
    [string]$GradleRoot = 'D:\Android\gradle-8.9',
    [string]$CompileSdk = '35',
    [string]$BuildTools = '35.0.0',
    [switch]$SkipJdk,
    [switch]$SkipSdk,
    [switch]$SkipGradle
)

# 刻意用 Continue 而不是 Stop：
# PowerShell 5.1 会把「原生命令写到 stderr」当成 terminating error，
# 而 sdkmanager / java / gradle 全都会往 stderr 写正常信息。
# 因此改由每一处显式检查 $LASTEXITCODE 来把关。
$ErrorActionPreference = 'Continue'
$ProgressPreference    = 'SilentlyContinue'

# PowerShell 5.1 默认可能只启用 TLS 1.0，会让 dl.google.com / services.gradle.org 直接拒绝连接
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
} catch {
    Write-Host '[!] 无法设置 TLS 1.2，若下载失败请考虑安装 PowerShell 7' -ForegroundColor Yellow
}

$ProjectRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $ProjectRoot 'settings.gradle.kts'))) {
    $ProjectRoot = $PSScriptRoot
}

function Write-Step2($m) { Write-Host ''; Write-Host ('=== ' + $m + ' ===') -ForegroundColor Cyan }
function Write-Ok($m)    { Write-Host ('  [OK] ' + $m) -ForegroundColor Green }
function Write-Warn2($m) { Write-Host ('  [!]  ' + $m) -ForegroundColor Yellow }
function Stop-WithError($m) { Write-Host ('  [X]  ' + $m) -ForegroundColor Red; exit 1 }

function Get-RemoteFile {
    param([string]$Url, [string]$Dest)
    if ((Test-Path $Dest) -and ((Get-Item $Dest).Length -gt 1MB)) {
        Write-Ok ('已缓存，跳过下载: ' + (Split-Path -Leaf $Dest))
        return
    }
    Write-Host ('  下载: ' + $Url)
    $dir = Split-Path -Parent $Dest
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    # 用 WebClient 而不是 Invoke-WebRequest：后者在 5.1 上会把整个响应读进内存，200MB 级文件极慢
    $client = New-Object System.Net.WebClient
    $client.Headers.Add('User-Agent', 'PlanList-Setup')
    try {
        $client.DownloadFile($Url, $Dest)
    } finally {
        $client.Dispose()
    }
    Write-Ok ('完成 (' + [math]::Round((Get-Item $Dest).Length / 1MB) + ' MB)')
}

$DownloadDir = Join-Path $env:TEMP 'planlist-toolchain'
New-Item -ItemType Directory -Path $DownloadDir -Force | Out-Null

Write-Host ''
Write-Host 'PlanList 构建工具链安装' -ForegroundColor White
Write-Host ('  工程目录    : ' + $ProjectRoot)
Write-Host ('  Android SDK : ' + $SdkRoot)
Write-Host ('  JDK 17      : ' + $JdkRoot)
Write-Host ('  Gradle 8.9  : ' + $GradleRoot)

# ---------------------------------------------------------------- 1. JDK 17
if (-not $SkipJdk) {
    Write-Step2 '1/5 安装 JDK 17 (Temurin)'
    $javaExe = Join-Path $JdkRoot 'bin\java.exe'
    if (Test-Path $javaExe) {
        Write-Ok ('已安装: ' + $JdkRoot)
    } else {
        $zip = Join-Path $DownloadDir 'jdk17.zip'
        Get-RemoteFile -Url 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk' -Dest $zip
        $tmp = Join-Path $DownloadDir 'jdk17-extract'
        if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
        Write-Host '  解压...'
        Expand-Archive -Path $zip -DestinationPath $tmp -Force -ErrorAction Stop
        $inner = Get-ChildItem -Path $tmp -Directory | Select-Object -First 1
        if (-not $inner) { Stop-WithError 'JDK 压缩包结构异常' }
        $parent = Split-Path -Parent $JdkRoot
        if (-not (Test-Path $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
        if (Test-Path $JdkRoot) { Remove-Item $JdkRoot -Recurse -Force }
        Move-Item -Path $inner.FullName -Destination $JdkRoot -ErrorAction Stop
        if (-not (Test-Path $javaExe)) { Stop-WithError ('JDK 安装失败，未找到 ' + $javaExe) }
        Write-Ok ('JDK 17 -> ' + $JdkRoot)
    }
    try { $v = (& $javaExe -version 2>&1 | Select-Object -First 1) } catch { $v = '(无法读取 java 版本)' }
    Write-Host ('  ' + $v)
} else { Write-Warn2 '跳过 JDK 安装' }

# ------------------------------------------------------------ 2. Android SDK
if (-not $SkipSdk) {
    Write-Step2 '2/5 安装 Android SDK 命令行工具'
    $sdkManager = Join-Path $SdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'
    if (Test-Path $sdkManager) {
        Write-Ok 'cmdline-tools 已安装'
    } else {
        $builds = @('13114758', '12700392', '12266719', '11076708')
        $url = $null
        foreach ($b in $builds) {
            $candidate = 'https://dl.google.com/android/repository/commandlinetools-win-' + $b + '_latest.zip'
            try {
                Invoke-WebRequest -Uri $candidate -Method Head -UseBasicParsing -TimeoutSec 45 | Out-Null
                $url = $candidate
                break
            } catch {
                Write-Host ('  版本 ' + $b + ' 不可用，尝试下一个...')
            }
        }
        if (-not $url) { Stop-WithError '无法找到可用的 commandlinetools 下载地址，请检查网络' }
        $zip = Join-Path $DownloadDir 'cmdline-tools.zip'
        Get-RemoteFile -Url $url -Dest $zip
        $tmp = Join-Path $DownloadDir 'cmdline-tools-extract'
        if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
        Write-Host '  解压...'
        Expand-Archive -Path $zip -DestinationPath $tmp -Force -ErrorAction Stop
        $inner = Join-Path $tmp 'cmdline-tools'
        if (-not (Test-Path $inner)) { Stop-WithError 'cmdline-tools 压缩包结构异常' }
        $target = Join-Path $SdkRoot 'cmdline-tools\latest'
        if (Test-Path $target) { Remove-Item $target -Recurse -Force }
        New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
        Move-Item -Path $inner -Destination $target
        Write-Ok ('cmdline-tools -> ' + $target)
    }

    Write-Step2 '3/5 安装 SDK 组件并接受许可'
    $env:JAVA_HOME = $JdkRoot
    $env:ANDROID_HOME = $SdkRoot
    $env:ANDROID_SDK_ROOT = $SdkRoot
    $env:PATH = (Join-Path $JdkRoot 'bin') + ';' + $env:PATH

    Write-Host '  接受许可协议...'
    $yes = 1..60 | ForEach-Object { 'y' }
    $yes | & $sdkManager --sdk_root=$SdkRoot --licenses | Out-Null
    if ($LASTEXITCODE -ne 0) { Stop-WithError '接受 SDK 许可失败' }
    Write-Ok '许可已接受'

    $packages = @('platform-tools', ('platforms;android-' + $CompileSdk), ('build-tools;' + $BuildTools))
    Write-Host ('  安装: ' + ($packages -join ', '))
    & $sdkManager --sdk_root=$SdkRoot $packages
    if ($LASTEXITCODE -ne 0) { Stop-WithError 'sdkmanager 安装组件失败' }
    Write-Ok 'SDK 组件安装完成'

    Write-Step2 '4/5 写入 local.properties'
    # 用正斜杠：Gradle 在 Windows 上同样接受，且省掉 Java Properties 的反斜杠转义地狱
    $sdkDir = $SdkRoot.Replace([char]92, [char]47)
    $lp = Join-Path $ProjectRoot 'local.properties'
    Set-Content -Path $lp -Value ('sdk.dir=' + $sdkDir) -Encoding ASCII
    Write-Ok ('sdk.dir=' + $sdkDir)
} else { Write-Warn2 '跳过 Android SDK 安装' }

# ---------------------------------------------------------------- 5. Gradle
if (-not $SkipGradle) {
    Write-Step2 '5/5 安装 Gradle 并生成 wrapper'
    $gradleBat = Join-Path $GradleRoot 'bin\gradle.bat'
    if (-not (Test-Path $gradleBat)) {
        $zip = Join-Path $DownloadDir 'gradle-8.9-bin.zip'
        Get-RemoteFile -Url 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' -Dest $zip
        $tmp = Join-Path $DownloadDir 'gradle-extract'
        if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
        Write-Host '  解压...'
        Expand-Archive -Path $zip -DestinationPath $tmp -Force -ErrorAction Stop
        $inner = Get-ChildItem -Path $tmp -Directory | Select-Object -First 1
        $parent = Split-Path -Parent $GradleRoot
        if (-not (Test-Path $parent)) { New-Item -ItemType Directory -Path $parent -Force | Out-Null }
        if (Test-Path $GradleRoot) { Remove-Item $GradleRoot -Recurse -Force }
        Move-Item -Path $inner.FullName -Destination $GradleRoot
        Write-Ok ('Gradle -> ' + $GradleRoot)
    } else {
        Write-Ok 'Gradle 已安装'
    }

    $env:JAVA_HOME = $JdkRoot
    Push-Location $ProjectRoot
    try {
        Write-Host '  生成 gradlew wrapper...'
        & $gradleBat wrapper --gradle-version 8.9 --distribution-type bin
        if ($LASTEXITCODE -ne 0) { Stop-WithError 'gradle wrapper 生成失败' }
        Write-Ok 'gradlew 已生成'
    } finally {
        Pop-Location
    }
} else { Write-Warn2 '跳过 Gradle 安装' }

Write-Host ''
Write-Host '工具链安装完成。' -ForegroundColor Green
Write-Host '下一步:'
Write-Host '  .\tools\build-apk.ps1            # 编译 debug APK（含单元测试）'
Write-Host '  .\tools\install-to-phone.ps1      # 安装到已连接的小米手机'
Write-Host ''
Write-Host ('注意: 本脚本没有修改系统 JAVA_HOME。若你直接运行 .\gradlew.bat，请先设置 JAVA_HOME=' + $JdkRoot) -ForegroundColor Yellow
