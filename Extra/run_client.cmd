@echo off
title SinChat Client Launcher
color 0A
echo ===================================================
echo             STARTING SINCHAT TCP CLIENT            
echo ===================================================
echo.

:: Navigate to Client directory
cd /d "%~dp0..\Code\Client"

echo [1/2] Cleaning and Compiling Client code...
call mvn clean compile -q
if %ERRORLEVEL% NEQ 0 (
    color 0C
    echo.
    echo ===================================================
    echo [ERROR] Client compilation failed!
    echo        Make sure Maven ^(mvn^) is installed and in PATH.
    echo ===================================================
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/2] Launching JavaFX Client...
echo ===================================================
echo.
echo [Mode] Connect to localhost:3000 (default)
echo        Set TCP_HOST / TCP_PORT env vars to override.
echo.
call mvn javafx:run
if %ERRORLEVEL% NEQ 0 (
    color 0C
    echo.
    echo [ERROR] Client stopped with an error code.
    pause
)
exit /b 0
