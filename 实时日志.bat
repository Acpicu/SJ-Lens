@echo off
cd /d "%~dp0"
title SJ FollowClip - Live Log
echo ================================================
echo   Live app log. Press Ctrl+C to stop.
echo ================================================
echo.
adb logcat -v time | findstr /i "followclip AndroidRuntime"
