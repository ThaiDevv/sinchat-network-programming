@echo off
title SinChat Client - LAN Auto-Discovery
color 0E
echo ===================================================
echo     STARTING SINCHAT TCP CLIENT (LAN DISCOVERY)     
echo ===================================================
echo.
echo [Mode] UDP LAN auto-discovery ENABLED (port 9999)
echo        Client will wait for a SinChat server beacon on the LAN.
echo        Set TCP_HOST env var to override with a fixed IP.
echo.

:: Navigate to Client directory
cd /d "%~dp0..\Code\Client"

echo [1/2] Cleaning and Compiling Client code...
call mvn clean compile
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
echo [2/2] Launching JavaFX Client (LAN auto-discovery)...
echo ===================================================
call mvn javafx:run
if %ERRORLEVEL% NEQ 0 (
    color 0C
    echo.
    echo [ERROR] Client stopped with an error code.
)
exit /b 0
