<#
.SYNOPSIS
    给 tools/ 下的 PowerShell 脚本补上 UTF-8 BOM，并做语法检查。
.DESCRIPTION
    Windows PowerShell 5.1 在没有 BOM 时会按系统 ANSI 代码页（中文系统为 GBK）
    读取 .ps1 文件，导致脚本里的中文全部变成乱码、字符串未闭合，直接语法错误。

    任何工具（编辑器、代码生成、git 检出）如果以「无 BOM 的 UTF-8」重写了这些脚本，
    都必须重新跑一次本脚本。首次克隆仓库后也建议跑一次。
.EXAMPLE
    .\tools\fix-ps1-encoding.ps1
#>
[CmdletBinding()]
param(
    [string]$Path = (Join-Path (Split-Path -Parent $PSScriptRoot) 'tools')
)

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$utf8Bom   = New-Object System.Text.UTF8Encoding($true)

$files = @(Get-ChildItem -Path $Path -Filter '*.ps1' -File -Recurse)
if ($files.Count -eq 0) {
    Write-Host ('未找到 .ps1 文件: ' + $Path) -ForegroundColor Yellow
    exit 0
}

foreach ($file in $files) {
    $bytes = [System.IO.File]::ReadAllBytes($file.FullName)
    $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
    if ($hasBom) {
        Write-Host ('  已是 UTF-8 BOM: ' + $file.Name) -ForegroundColor DarkGray
        continue
    }
    $text = [System.IO.File]::ReadAllText($file.FullName, $utf8NoBom)
    [System.IO.File]::WriteAllText($file.FullName, $text, $utf8Bom)
    Write-Host ('  已补 BOM: ' + $file.Name) -ForegroundColor Green
}

Write-Host ''
Write-Host '语法检查:'
foreach ($file in $files) {
    $errors = $null
    [System.Management.Automation.Language.Parser]::ParseFile($file.FullName, [ref]$null, [ref]$errors) | Out-Null
    if ($errors -and $errors.Count -gt 0) {
        Write-Host ('  [X] ' + $file.Name + ' 有 ' + $errors.Count + ' 处语法错误') -ForegroundColor Red
        $errors | ForEach-Object { Write-Host ('      行 ' + $_.Extent.StartLineNumber + ': ' + $_.Message) }
    } else {
        Write-Host ('  [OK] ' + $file.Name) -ForegroundColor Green
    }
}
