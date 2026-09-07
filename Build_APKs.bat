@echo off
setlocal enabledelayedexpansion

cd /d "%~dp0"

:: Set path to Java and Android SDK tools
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "VERSION=1.0.0"

echo ========================================
echo   PixelTweaks Build Script (v%VERSION%)
echo ========================================

:: [1] Clean
echo [1/2] Cleaning old build artifacts...
call .\gradlew.bat clean
if %ERRORLEVEL% neq 0 goto :error

:: [2] Assemble All Variants
echo [2/2] Building all APK variants (Lite/Full x Debug/Release)...
call .\gradlew.bat assemble
if %ERRORLEVEL% neq 0 goto :error

echo.
echo ========================================
echo   BUILD SUCCESSFUL!
echo ========================================
echo.
echo Output APKs:
echo   [Lite Debug]   app\build\outputs\apk\lite\debug\pixel-tweaks-lite-v%VERSION%-debug.apk
echo   [Full Debug]   app\build\outputs\apk\full\debug\pixel-tweaks-full-v%VERSION%-debug.apk
echo   [Lite Release] app\build\outputs\apk\lite\release\pixel-tweaks-lite-v%VERSION%-release.apk (Signed)
echo   [Full Release] app\build\outputs\apk\full\release\pixel-tweaks-full-v%VERSION%-release.apk (Signed)
echo ========================================
exit /b 0

:error
echo.
echo ****************************************
echo   BUILD FAILED! Please check the logs.
echo ****************************************
exit /b 1
