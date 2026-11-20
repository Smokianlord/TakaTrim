# Builds the Android debug APK, runs the calculator unit tests, and copies the APK
# to TakaTrim.apk in the main folder.
# Uses the JDK + Android SDK installed under %LOCALAPPDATA%\Programs\TakaTrimBuildTools
# (or JAVA_HOME / ANDROID_HOME if you already have them, e.g. from Android Studio).
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$tools = Join-Path $env:LOCALAPPDATA "Programs\TakaTrimBuildTools"
if (-not $env:JAVA_HOME -and (Test-Path "$tools\jdk")) {
    $env:JAVA_HOME = (Get-ChildItem "$tools\jdk" -Directory | Select-Object -First 1).FullName
}
if (-not $env:ANDROID_HOME -and (Test-Path "$tools\android-sdk")) {
    $env:ANDROID_HOME = "$tools\android-sdk"
}
if (-not $env:JAVA_HOME) { throw "No JDK found. Install Android Studio or set JAVA_HOME." }
if (-not $env:ANDROID_HOME) { throw "No Android SDK found. Install Android Studio or set ANDROID_HOME." }

# Java's local sockets fail on Windows when the temp path has spaces or 8.3 short names.
$jtmp = "C:\Users\Public\jtmp"
New-Item -ItemType Directory -Force $jtmp | Out-Null
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$jtmp"

if (-not (Test-Path "TakaTrimMobile\local.properties")) {
    "sdk.dir=" + ($env:ANDROID_HOME -replace '\\', '/') | Out-File -Encoding ascii "TakaTrimMobile\local.properties"
}

Push-Location TakaTrimMobile
try {
    .\gradlew.bat --no-daemon testDebugUnitTest assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
}

Copy-Item "TakaTrimMobile\app\build\outputs\apk\debug\app-debug.apk" ".\TakaTrim.apk" -Force
Get-Item .\TakaTrim.apk | Select-Object FullName, Length, LastWriteTime
