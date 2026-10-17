@echo off
cd /d "%~dp0"
title SJ FollowClip - Screenshot
echo Capturing phone screen...
if not exist "screenshots" mkdir "screenshots"
for /f "tokens=1-3 delims=/: " %%a in ("%date%") do set D=%%a%%b%%c
for /f "tokens=1-3 delims=:. " %%a in ("%time%") do set T=%%a%%b%%c
adb exec-out screencap -p > "screenshots\shot-%D%-%T%.png"
if errorlevel 1 (
  echo FAILED. Make sure the phone is connected and authorized.
  pause
  exit /b 1
)
echo Saved to the screenshots folder.
start "" "screenshots"
pause
