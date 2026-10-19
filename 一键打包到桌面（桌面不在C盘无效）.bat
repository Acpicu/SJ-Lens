@echo off
rem ---------------------------------------------------------------
rem  Build a release APK and copy it to the Desktop.
rem  Keep this file ASCII-only: cmd.exe mis-parses UTF-8 Chinese.
rem ---------------------------------------------------------------
setlocal
cd /d "%~dp0"

echo ============================================
echo   SJ Lens  -  build release APK
echo ============================================
echo.

if not exist "gradlew.bat" (
    echo [ERROR] gradlew.bat not found. Run this from the project root.
    pause
    exit /b 1
)

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

set "DESKTOP=%USERPROFILE%\Desktop"
if not exist "%DESKTOP%" set "DESKTOP=%USERPROFILE%\OneDrive\Desktop"
if not exist "%DESKTOP%" set "DESKTOP=%~dp0"

copy /y "%APK%" "%DESKTOP%\SJ-Lens.apk" >nul
if errorlevel 1 (
    echo [ERROR] Copy failed: %DESKTOP%
    pause
    exit /b 1
)

echo.
echo ============================================
echo   Done
echo   %DESKTOP%\SJ-Lens.apk
echo ============================================
echo.
pause
exit /b 0
