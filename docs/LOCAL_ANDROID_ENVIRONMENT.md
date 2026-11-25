# Local Android development environment

This checkout uses a workspace-local Gradle 8.9 cache and Eclipse Temurin JDK 17. The Android SDK and emulator binaries are installed on this Windows machine; this repository's ignored `local.properties` points Gradle to that SDK. The API 36 Pixel 7 virtual device keeps its data under the workspace's `work/android-avd` directory.

In PowerShell, from the repository root, initialize the current terminal and use the wrapper:

```powershell
. .\scripts\use-workspace-android-environment.ps1
.\gradlew.bat assembleDebug
```

To start the virtual device in the current PowerShell window:

```powershell
.\scripts\start-sehati-emulator.ps1
```

The toolchain archives, Gradle cache, Android user cache, and emulator data are kept in the workspace-level `work/` directory and are not committed to Git. Java uses the Windows trusted root certificate store for HTTPS downloads in this environment; certificate validation remains enabled.
