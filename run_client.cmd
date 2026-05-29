@echo off
title SinChat Client Launcher
color 0A
echo ===================================================
echo             STARTING SINCHAT TCP CLIENT            
echo ===================================================
echo.

:: Navigate to Client directory
cd /d "%~dp0Code\Client"

echo [1/2] Compiling Client code...
call mvn compile
if %ERRORLEVEL% NEQ 0 (
    color 0C
    echo.
    echo ===================================================
    echo [ERROR] Client compilation failed!
    echo ===================================================
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/2] Launching JavaFX Client...
echo ===================================================
call mvn javafx:run
if %ERRORLEVEL% NEQ 0 (
    color 0C
    echo.
    echo [ERROR] Client stopped with an error code.
)
exit /b 0
