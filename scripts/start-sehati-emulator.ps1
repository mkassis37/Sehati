. (Join-Path $PSScriptRoot 'use-workspace-android-environment.ps1')

$emulator = Join-Path $env:ANDROID_HOME 'emulator\emulator.exe'
& $emulator -avd 'Sehati_API_36'
