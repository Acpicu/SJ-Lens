@echo off
cd /d "%~dp0"
title SJ FollowClip - Build and Install
echo ================================================
echo   SJ FollowClip  -  Build and Install to Phone
echo ================================================
echo.
echo [1/3] Checking device connection...
adb devices
echo.
echo [2/3] Building debug APK (first run is slow, then ~30s)...
call gradlew.bat assembleDebug --console=plain
if errorlevel 1 goto fail
echo.
echo [3/3] Installing to phone (tap ALLOW on your phone if asked)...
adb install -r "app\build\outputs\apk\debug\app-debug.apk"
if errorlevel 1 goto fail
echo.
echo ================================================
echo   DONE. Open "SJ FollowClip" on your phone.
echo ================================================
pause
exit /b 0

:fail
echo.
echo ================================================
echo   FAILED. Common causes:
echo   1. No device   - check "adb devices" above
echo   2. Not allowed - tap ALLOW USB debugging on phone
echo   3. vivo block  - Settings - Developer options -
echo                    enable "USB debugging (Security)"
echo ================================================
pause
exit /b 1
