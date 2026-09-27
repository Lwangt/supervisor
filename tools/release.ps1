<#
.SYNOPSIS
    一次完成：改版本号 + 写 CHANGELOG + 提交 + 打 tag + 推送 + 编译 APK（可选发布 GitHub Release）。
.DESCRIPTION
    这是 AGENTS.md 里"每次变更都要同步 CHANGELOG、版本号与 git"这条规则的可执行版本。
    建议每次发版都走这个脚本，而不是手工敲一串命令。
.EXAMPLE
    .\tools\release.ps1 -Version 0.2.0 -Notes "支持自定义主题色"
.EXAMPLE
    .\tools\release.ps1 -Version 0.2.0 -Notes "修复提醒重复触发" -Publish
.NOTES
    -Publish 需要环境变量 GITHUB_TOKEN（GitHub Personal Access Token，需 repo 权限）
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][string]$Notes,
    [switch]$Publish,
    [switch]$SkipTests,
    [string]$Repo = 'Lwangt/supervisor',
    [string]$JdkRoot = 'D:\Android\jdk-17',
    [string]$SdkRoot = 'D:\Android\Sdk'
)

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'

$ProjectRoot = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $ProjectRoot 'settings.gradle.kts'))) { $ProjectRoot = $PSScriptRoot }
Push-Location $ProjectRoot

function Write-Step2($m) { Write-Host ''; Write-Host ('=== ' + $m + ' ===') -ForegroundColor Cyan }
function Write-Ok($m)    { Write-Host ('  [OK] ' + $m) -ForegroundColor Green }
function Stop-WithError($m) { Write-Host ('  [X]  ' + $m) -ForegroundColor Red; Pop-Location; exit 1 }

if ($Version -notmatch '^\d+\.\d+\.\d+$') {
    Stop-WithError ('版本号必须是 x.y.z 形式，收到: ' + $Version)
}
$tag = 'v' + $Version
$date = (Get-Date).ToString('yyyy-MM-dd')

# ---------------------------------------------------------------- 1. 版本号
Write-Step2 ('1/6 递增版本号 -> ' + $Version)
$gradleFile = Join-Path $ProjectRoot 'app\build.gradle.kts'
$content = [System.IO.File]::ReadAllText($gradleFile, (New-Object System.Text.UTF8Encoding($false)))

$codeMatch = [regex]::Match($content, 'versionCode\s*=\s*(\d+)')
if (-not $codeMatch.Success) { Stop-WithError '在 app/build.gradle.kts 里找不到 versionCode' }
$oldCode = [int]$codeMatch.Groups[1].Value
$newCode = $oldCode + 1

$content = [regex]::Replace($content, 'versionCode\s*=\s*\d+', 'versionCode = ' + $newCode)
$content = [regex]::Replace($content, 'versionName\s*=\s*"[^"]*"', 'versionName = "' + $Version + '"')
[System.IO.File]::WriteAllText($gradleFile, $content, (New-Object System.Text.UTF8Encoding($false)))
Write-Ok ('versionCode ' + $oldCode + ' -> ' + $newCode + '，versionName -> ' + $Version)

# ------------------------------------------------------------- 2. CHANGELOG
Write-Step2 '2/6 写入 CHANGELOG'
$changelogFile = Join-Path $ProjectRoot 'CHANGELOG.md'
$changelog = [System.IO.File]::ReadAllText($changelogFile, (New-Object System.Text.UTF8Encoding($false)))

$newSection = "## [$Version] - $date" + [Environment]::NewLine + [Environment]::NewLine + $Notes.Trim() + [Environment]::NewLine + [Environment]::NewLine + '---' + [Environment]::NewLine + [Environment]::NewLine
$marker = '## [未发布]'
$idx = $changelog.IndexOf($marker)
if ($idx -lt 0) { Stop-WithError 'CHANGELOG.md 里找不到 ## [未发布] 标记' }
$insertAt = $changelog.IndexOf([Environment]::NewLine, $idx) + [Environment]::NewLine.Length
$changelog = $changelog.Substring(0, $insertAt) + $newSection + $changelog.Substring($insertAt)

# 去掉「未发布」段落里原来的占位说明
$changelog = $changelog.Replace('（下一次发版前，把改动先写在这里）', '')
[System.IO.File]::WriteAllText($changelogFile, $changelog, (New-Object System.Text.UTF8Encoding($false)))
Write-Ok 'CHANGELOG 已追加新版本段落'

# ---------------------------------------------------------------- 3. 提交
Write-Step2 '3/6 提交到 git'
& git add -A
if ($LASTEXITCODE -ne 0) { Stop-WithError 'git add 失败' }
$subject = 'release: ' + $tag
& git commit -m $subject -m $Notes.Trim()
if ($LASTEXITCODE -ne 0) { Write-Host '  [!] 没有需要提交的改动，或提交失败（继续打 tag）' -ForegroundColor Yellow }
Write-Ok ('已提交: ' + $subject)

# ---------------------------------------------------------------- 4. tag
Write-Step2 ('4/6 打 tag ' + $tag)
& git tag -f $tag
if ($LASTEXITCODE -ne 0) { Stop-WithError 'git tag 失败' }
Write-Ok $tag

# ---------------------------------------------------------------- 5. 推送
Write-Step2 '5/6 推送到远端'
& git push origin HEAD
if ($LASTEXITCODE -ne 0) { Stop-WithError 'git push 失败（检查凭据与网络）' }
& git push origin $tag --force
if ($LASTEXITCODE -ne 0) { Stop-WithError '推送 tag 失败' }
Write-Ok 'main 与 tag 均已推送'

# ---------------------------------------------------------------- 6. 编译
Write-Step2 '6/6 编译 release APK'
$buildArgs = @('-Variant', 'release')
if ($SkipTests) { $buildArgs += '-SkipTests' }
& (Join-Path $PSScriptRoot 'build-apk.ps1') @buildArgs
if ($LASTEXITCODE -ne 0) { Stop-WithError '编译失败，APK 未产出（git 已推送，可修复后重跑）' }

$apkDir = Join-Path $ProjectRoot 'app\build\outputs\apk\release'
$apk = Get-ChildItem -Path $apkDir -Filter '*.apk' -ErrorAction SilentlyContinue |
       Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $apk) { Stop-WithError '编译成功但找不到 APK' }
$apkName = 'PlanList-' + $Version + '.apk'
$apkOut = Join-Path $ProjectRoot ('dist\' + $apkName)
$distDir = Split-Path -Parent $apkOut
if (-not (Test-Path $distDir)) { New-Item -ItemType Directory -Path $distDir -Force | Out-Null }
Copy-Item -Path $apk.FullName -Destination $apkOut -Force
Write-Ok ('APK: ' + $apkOut)

# ------------------------------------------------------- 可选：发布 Release
if ($Publish) {
    Write-Step2 '发布 GitHub Release'
    $token = $env:GITHUB_TOKEN
    if ([string]::IsNullOrWhiteSpace($token)) {
        Write-Host '  [!] 未设置 GITHUB_TOKEN，跳过自动发布。' -ForegroundColor Yellow
        Write-Host '      手动发布：打开 https://github.com/' + $Repo + '/releases/new'
        Write-Host '      选择 tag ' + $tag + '，把上面的 APK 拖进去即可。'
    } else {
        $headers = @{
            Authorization = 'Bearer ' + $token
            Accept = 'application/vnd.github+json'
            'User-Agent' = 'PlanList-Release'
        }
        $body = @{
            tag_name = $tag
            name = $tag
            body = $Notes.Trim()
            draft = $false
            prerelease = $false
        } | ConvertTo-Json -Depth 4

        try {
            $release = Invoke-RestMethod -Method Post -Uri ('https://api.github.com/repos/' + $Repo + '/releases') -Headers $headers -Body $body -ContentType 'application/json'
            Write-Ok ('Release 已创建: ' + $release.html_url)

            $uploadUrl = $release.upload_url -replace '\{.*\}', ''
            $uploadUrl = $uploadUrl + '?name=' + $apkName
            $uploadHeaders = @{
                Authorization = 'Bearer ' + $token
                'Content-Type' = 'application/vnd.android.package-archive'
                'User-Agent' = 'PlanList-Release'
            }
            Invoke-RestMethod -Method Post -Uri $uploadUrl -Headers $uploadHeaders -InFile $apkOut | Out-Null
            Write-Ok ('APK 已上传: ' + $apkName)
            Write-Host ''
            Write-Host '手机安装: 用手机浏览器打开下面地址，下载 APK 后安装' -ForegroundColor Green
            Write-Host ('  ' + $release.html_url) -ForegroundColor Green
        } catch {
            Write-Host ('  [X] 发布失败: ' + $_.Exception.Message) -ForegroundColor Red
            Write-Host ('      可手动上传: https://github.com/' + $Repo + '/releases/new')
        }
    }
} else {
    Write-Host ''
    Write-Host '未发布 Release。加上 -Publish 可自动上传 APK 供手机下载。' -ForegroundColor DarkGray
}

Pop-Location
Write-Host ''
Write-Host ('发布完成: ' + $tag) -ForegroundColor Green
