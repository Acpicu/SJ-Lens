@echo off
rem ---------------------------------------------------------------
rem  One click: build release APK, install to the connected phone,
rem  then launch it. Keep this file ASCII-only (cmd.exe + UTF-8).
rem ---------------------------------------------------------------
setlocal
cd /d "%~dp0"

set "VER=unknown"
for /f "tokens=2 delims==" %%v in ('findstr /b "VERSION_NAME=" gradle.properties') do set "VER=%%v"

echo ============================================
echo   SJ Lens %VER%  -  build + install
echo ============================================
echo.

echo [1/4] checking device...
adb devices
adb get-state >nul 2>&1
if errorlevel 1 (
    echo [ERROR] No device. Connect USB, enable USB debugging, tap ALLOW.
    pause
    exit /b 1
)

echo [2/4] building release APK...
call gradlew.bat assembleRelease --console=plain
if errorlevel 1 (
    echo [ERROR] Build failed. See output above.
    pause
    exit /b 1
)

set "APK="
for %%f in ("app\build\outputs\apk\release\*.apk") do set "APK=%%~f"
if "%APK%"=="" (
    echo [ERROR] No APK found.
    pause
    exit /b 1
)

echo.
echo [3/4] installing %APK% ...
adb install -r "%APK%"
if errorlevel 1 (
    echo [ERROR] Install failed. Common causes:
    echo   1. Locked screen - unlock the phone, then retry
    echo   2. Install confirmation dialog - tap ALLOW on phone
    echo   3. Signature mismatch - uninstall old app first
    pause
    exit /b 1
)

echo.
echo [4/4] launching...
adb shell am force-stop com.sj.followclip
adb shell am start -n com.sj.followclip/.MainActivity >nul 2>&1

echo.
echo ============================================
echo   Done. SJ Lens %VER% is on your phone.
echo ============================================
pause
exit /b 0