<#
.SYNOPSIS
  MzDKPlayer 编译脚本 (Windows PowerShell 版)
.DESCRIPTION
  基于 build.sh 移植的 Windows PowerShell 编译脚本，支持自定义构建选项。
.PARAMETER Type
  构建类型: debug | release (默认: release)
.PARAMETER Abi
  目标架构: all | arm64 | arm32 | x86 (默认: arm64)
.PARAMETER Split
  启用 ABI 拆分 (按架构生成独立 APK)
.PARAMETER Output
  输出目录 (默认: app/build/outputs/apk)
.PARAMETER VersionName
  覆盖版本名 (如 1.16.0)
.PARAMETER VersionCode
  覆盖版本号 (如 100)
.PARAMETER NoSign
  跳过签名 (仅 debug 有效)
.EXAMPLE
  .\build.ps1
  # 默认 release arm64 架构
.EXAMPLE
  .\build.ps1 -Type debug -Abi arm64
  # debug 仅 arm64
.EXAMPLE
  .\build.ps1 -Type release -Abi arm64 -Split
  # release 拆分 APK
.EXAMPLE
  .\build.ps1 -Type release -VersionName 2.0.0 -VersionCode 100
  # 自定义版本号
.EXAMPLE
  .\build.ps1 -Type release -Abi arm64 -Output C:\apks
  # 指定输出目录
#>

param(
    [Alias('t')]
    [ValidateSet('debug', 'release')]
    [string]$Type = 'release',

    [Alias('a')]
    [ValidateSet('all', 'arm64', 'arm32', 'x86')]
    [string]$Abi = 'arm64',

    [Alias('s')]
    [switch]$Split,

    [Alias('o')]
    [string]$Output = '',

    [Alias('v')]
    [string]$VersionName = '',

    [Alias('c')]
    [string]$VersionCode = '',

    [Alias('n')]
    [switch]$NoSign,

    [Alias('h')]
    [switch]$Help
)

$ErrorActionPreference = 'Stop'

# ── 路径 ──────────────────────────────────────────────
$ProjectDir = $PSScriptRoot

# ── 调试签名 keystore 默认配置 ────────────────────────
$KeystoreName     = 'debug.keystore'
$KeystoreAlias    = 'mzdk-debug'
$KeystorePassword = 'mzdk123456'
$KeyPassword      = 'mzdk123456'
$KeystoreValidity = 36500    # 100年

# ── 颜色输出 ──────────────────────────────────────────
function Write-Info  ([string]$Msg) { Write-Host "[INFO] $Msg" -ForegroundColor Cyan }
function Write-Warn  ([string]$Msg) { Write-Host "[WARN] $Msg" -ForegroundColor Yellow }
function Write-Err   ([string]$Msg) { Write-Host "[ERROR] $Msg" -ForegroundColor Red }

# ── 帮助 ──────────────────────────────────────────────
if ($Help) {
    Get-Help $MyInvocation.MyCommand.Path -Detailed
    exit 0
}

# ── 校验 ──────────────────────────────────────────────
$SignConfig = if ($NoSign) { 'skip' } else { '' }

if ($SignConfig -eq 'skip' -and $Type -eq 'release') {
    Write-Warn 'release 构建不支持跳过签名，忽略 --no-sign'
    $SignConfig = ''
}

# ── 构建 Gradle 参数 ──────────────────────────────────
$TaskName = 'assemble' + $Type.Substring(0, 1).ToUpper() + $Type.Substring(1).ToLower()
$GradleArgs = @()

# ABI 过滤
if ($Abi -ne 'all') {
    $GradleArgs += "-PtargetAbi=$Abi"
}

# ABI 拆分
if ($Split) {
    $GradleArgs += '-Pandroid.injected.split.enabled=true'
}

# 版本号覆盖
if ($VersionName) { $GradleArgs += "-PversionName=$VersionName" }
if ($VersionCode) { $GradleArgs += "-PversionCode=$VersionCode" }

# ── 打印构建信息 ──────────────────────────────────────
Write-Host ''
Write-Info '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'
Write-Info ' MzDKPlayer 构建配置'
Write-Info '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'
Write-Info " 构建类型:  $Type"
Write-Info " 目标架构:  $Abi"
Write-Info " ABI 拆分:  $Split"
if ($VersionName) { Write-Info " 版本名:    $VersionName" }
if ($VersionCode) { Write-Info " 版本号:    $VersionCode" }
if ($Output)      { Write-Info " 输出目录:  $Output" }
Write-Info '━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'
Write-Host ''

# ── 环境修正 ──────────────────────────────────────────
# AGP 9.x 要求 ANDROID_SDK_HOME 指向偏好设置目录的父目录（如 $HOME），
# 而非 SDK 路径本身。SDK 路径由 ANDROID_HOME 或 local.properties 指定。
if ($env:ANDROID_SDK_HOME -and $env:ANDROID_SDK_HOME -eq $env:ANDROID_HOME) {
    $env:ANDROID_SDK_HOME = $env:USERPROFILE
}

# ── 签名配置 ──────────────────────────────────────────
$LocalProps   = Join-Path $ProjectDir 'local.properties'
$KeystorePath = Join-Path $ProjectDir $KeystoreName

function Ensure-Keystore {
    # 检查 local.properties 中是否已有签名配置
    if (Test-Path $LocalProps) {
        $lines = Get-Content $LocalProps -ErrorAction SilentlyContinue
        $existing = $lines |
            Where-Object { $_ -match '^STORE_FILE=' } |
            ForEach-Object { ($_ -split '=', 2)[1] } |
            Select-Object -First 1
        if ($existing -and (Test-Path $existing)) {
            Write-Info "使用已有签名配置: $existing"
            return
        }
    }

    # 自动生成调试 keystore
    if (-not (Test-Path $KeystorePath)) {
        Write-Info '未找到签名 keystore，自动生成调试签名...'
        & keytool -genkeypair `
            -keystore $KeystorePath `
            -alias $KeystoreAlias `
            -storepass $KeystorePassword `
            -keypass $KeyPassword `
            -keyalg RSA `
            -keysize 2048 `
            -validity $KeystoreValidity `
            -dname 'CN=MzDKPlayer, OU=Dev, O=mz, L=Beijing, ST=Beijing, C=CN'
        if ($LASTEXITCODE -ne 0) {
            Write-Err '生成 keystore 失败，请检查 keytool 是否可用'
            exit 1
        }
        Write-Info "调试 keystore 已生成: $KeystorePath"
    }
    else {
        Write-Info "使用已有调试 keystore: $KeystorePath"
    }

    # 写入 local.properties
    $content = @(
        '',
        '# 自动生成的调试签名配置',
        "STORE_FILE=$KeystorePath",
        "STORE_PASSWORD=$KeystorePassword",
        "KEY_ALIAS=$KeystoreAlias",
        "KEY_PASSWORD=$KeyPassword"
    )
    Add-Content -Path $LocalProps -Value $content
    Write-Info '签名配置已写入 local.properties'
}

# release 构建时确保有签名配置
if ($Type -eq 'release' -and $SignConfig -ne 'skip') {
    Ensure-Keystore
}

# ── 执行构建 ──────────────────────────────────────────
Write-Info '开始构建...'
Set-Location $ProjectDir

& .\gradlew.bat $TaskName @GradleArgs --no-daemon
if ($LASTEXITCODE -ne 0) {
    Write-Err '构建失败!'
    exit 1
}

# ── 收集产物 ──────────────────────────────────────────
$ApkSourceDir = Join-Path $ProjectDir "app\build\outputs\apk\$Type"

if (-not (Test-Path $ApkSourceDir)) {
    Write-Err "未找到 APK 输出目录: $ApkSourceDir"
    exit 1
}

$Apks = @(Get-ChildItem -Path $ApkSourceDir -Filter *.apk -Recurse)
if ($Apks.Count -eq 0) {
    Write-Err '未找到 APK 文件'
    exit 1
}

# 复制到自定义输出目录
if ($Output) {
    New-Item -ItemType Directory -Force -Path $Output | Out-Null
    foreach ($apk in $Apks) {
        Copy-Item -Path $apk.FullName -Destination $Output -Force
        Write-Info "已复制: $($apk.Name)"
    }
    Write-Info "APK 已复制到: $Output"
    Write-Host ''
    Get-ChildItem -Path $Output -Filter *.apk |
        Format-Table Name, @{N = 'Size(KB)'; E = { [math]::Round($_.Length / 1KB, 1) } }, FullName -AutoSize
}
else {
    Write-Info "APK 输出目录: $ApkSourceDir"
    Write-Host ''
    $Apks |
        Format-Table Name, @{N = 'Size(KB)'; E = { [math]::Round($_.Length / 1KB, 1) } }, FullName -AutoSize
}

Write-Host ''
Write-Info "构建完成! 共 $($Apks.Count) 个 APK"
