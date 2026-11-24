@echo off
setlocal
echo ==========================================
echo   PixelTweaks v1.3.4 APK Build Script
echo ==========================================

set "VERSION=1.3.4"

echo.
echo [1/2] Building Release APK for v%VERSION%...
call ./gradlew :app:assembleRelease

echo.
echo [2/2] Building Debug APK for v%VERSION%...
call ./gradlew :app:assembleDebug

echo.
echo ==========================================
echo   Builds completed successfully!
echo   Outputs are located in app/build/outputs/apk/
echo ==========================================
pause
endlocal
