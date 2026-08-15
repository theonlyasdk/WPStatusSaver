@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo   WP Status Saver - Building Release APK
echo ===================================================
echo.

:: Detect Java Home if not set
if "%JAVA_HOME%"=="" (
    if exist "C:\Program Files\Android\Android Studio\jbr" (
        set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
        echo [INFO] Using Android Studio Embedded JDK: !JAVA_HOME!
    ) else (
        echo [WARNING] JAVA_HOME is not set. Using system default Java if available.
    )
) else (
    echo [INFO] Using JAVA_HOME: %JAVA_HOME%
)

echo.
echo [1/3] Cleaning previous release builds...
call gradlew.bat clean

echo.
echo [2/3] Compiling and assembling Release APK...
call gradlew.bat assembleRelease

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ===================================================
    echo   [ERROR] Build Failed! Please check the errors above.
    echo ===================================================
    pause
    exit /b %ERRORLEVEL%
)

set "APK_SRC=app\build\outputs\apk\release\app-release.apk"
set "OUTPUT_DIR=build_output"
set "DEST_APK=%OUTPUT_DIR%\WPStatusSaver-v1.0-release.apk"

if exist "%APK_SRC%" (
    if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"
    copy /y "%APK_SRC%" "%DEST_APK%" >nul
    echo.
    echo [3/3] Release APK generated successfully!
    echo ===================================================
    echo   BUILD SUCCESSFUL!
    echo   Output APK: %CD%\%DEST_APK%
    echo ===================================================

    :: Check if ADB is available and an active device is connected
    where adb >nul 2>nul
    if !ERRORLEVEL! EQU 0 (
        for /f "skip=1 tokens=1,2" %%A in ('adb devices') do (
            if "%%B"=="device" (
                echo.
                echo [ADB] Connected device found: %%A
                echo [ADB] Auto-installing with all permissions granted (-r -g)...
                adb -s %%A install -r -g "%DEST_APK%"
                if !ERRORLEVEL! EQU 0 (
                    echo [ADB] Successfully installed on %%A!
                )
            )
        )
    )
) else (
    echo.
    echo [WARNING] Could not locate APK at %APK_SRC%
)

echo.
pause
