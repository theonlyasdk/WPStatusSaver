@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo   USB Reverse Tethering (PC Internet -> Phone)
echo ===================================================
echo.
echo Sharing your PC's internet connection over USB cable...
echo (The phone will receive full high-speed internet without Wi-Fi)
echo.

:: Ensure ADB is in PATH
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools" (
    set "PATH=%PATH%;%LOCALAPPDATA%\Android\Sdk\platform-tools"
)

set "GNIREHTET_DIR=%~dp0tools\gnirehtet\gnirehtet-rust-win64"
cd /d "%GNIREHTET_DIR%"

"%GNIREHTET_DIR%\gnirehtet.exe" run

pause
