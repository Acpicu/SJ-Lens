@echo off
rem ---------------------------------------------------------------
rem  Build release APK and put it in THIS project folder only.
rem  No Desktop, no C: drive - the artifact always lands next to
rem  this script, named SJ-Lens-v<version>.apk
rem  Keep this file ASCII-only: cmd.exe mis-parses UTF-8 Chinese.
rem ---------------------------------------------------------------
setlocal enabledelayedexpansion
cd /d "%~dp0"

if not exist "gradlew.bat" (
    echo [ERROR] Run this from the project root ^(gradlew.bat not found^).
    pause
    exit /b 1
)

rem --- read versionName from gradle.properties ---
set "VER=unknown"
for /f "tokens=2 delims==" %%v in ('findstr /b "VERSION_NAME=" gradle.properties') do set "VER=%%v"

echo ============================================
echo   SJ Lens %VER%  -  build release APK
echo ============================================

call gradlew.bat assembleRelease --console=plain
if errorlevel 1 (
    echo.
    echo [ERROR] Build failed. See the output above.
    pause
    exit /b 1
)

set "APK="
for %%f in ("app\build\outputs\apk\release\*.apk") do set "APK=%%~f"
if "%APK%"=="" (
    echo [ERROR] No APK found under app\build\outputs\apk\release
    pause
    exit /b 1
)

rem ????????????????
set "OUT=%~dp0SJ-Lens-v%VER%.apk"
copy /y "%APK%" "%OUT%" >nul
if errorlevel 1 (
    echo [ERROR] Copy failed: %OUT%
    pause
    exit /b 1
)

echo.
echo ============================================
echo   Done
echo   %OUT%
echo ============================================
pause
exit /b 0