# Dot-source this file in PowerShell to use the toolchain installed beside the repository.
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$workspaceRoot = Split-Path $repoRoot -Parent
$toolchainRoot = Join-Path $workspaceRoot 'work\toolchains'
$jdkRoot = Get-ChildItem (Join-Path $toolchainRoot 'jdk17') -Directory | Select-Object -First 1
if (-not $jdkRoot) { throw "Workspace JDK 17 was not found under $toolchainRoot" }

$sdkRoot = $env:ANDROID_HOME
if (-not $sdkRoot) {
    $localProperties = Join-Path $repoRoot 'local.properties'
    if (Test-Path $localProperties) {
        $sdkLine = Get-Content $localProperties | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
        if ($sdkLine) { $sdkRoot = ($sdkLine -replace '^sdk\.dir=', '').Replace('/', '\') }
    }
}
if (-not $sdkRoot) { $sdkRoot = Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk' }
if (-not (Test-Path (Join-Path $sdkRoot 'platform-tools\adb.exe'))) {
    throw "Android SDK platform-tools were not found under $sdkRoot"
}

$env:JAVA_HOME = $jdkRoot.FullName
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_USER_HOME = Join-Path $workspaceRoot 'work\android-user'
$env:ANDROID_EMULATOR_HOME = Join-Path $workspaceRoot 'work\android-user'
$env:ANDROID_AVD_HOME = Join-Path $workspaceRoot 'work\android-avd'
$env:GRADLE_USER_HOME = Join-Path $workspaceRoot 'work\gradle-home'
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null
$env:JAVA_TOOL_OPTIONS = '-Djavax.net.ssl.trustStoreType=Windows-ROOT'
$env:PATH = "$env:JAVA_HOME\bin;$sdkRoot\platform-tools;$sdkRoot\emulator;$sdkRoot\cmdline-tools\latest\bin;$env:PATH"

Write-Host "Workspace Java: $env:JAVA_HOME"
Write-Host "Android SDK:    $env:ANDROID_HOME"
Write-Host "Gradle cache:   $env:GRADLE_USER_HOME"
Write-Host "AVD storage:    $env:ANDROID_AVD_HOME"
