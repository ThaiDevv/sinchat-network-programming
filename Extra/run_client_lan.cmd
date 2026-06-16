@echo off
title SinChat Client - LAN Auto-Discovery
color 0E
echo ===================================================
echo     STARTING SINCHAT TCP CLIENT (LAN DISCOVERY)     
echo ===================================================
echo.
echo [Mode] TCP LAN auto-discovery ENABLED (probe port 9999)
echo        Client will scan the local subnet for a SinChat server.
echo        Set TCP_HOST / TCP_PORT env vars to override with a fixed IP.
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
echo [2/2] Launching JavaFX Client (LAN auto-discovery)...
echo ===================================================
echo.
call mvn javafx:run
if %ERRORLEVEL% NEQ 0 (
    color 0C
    echo.
    echo [ERROR] Client stopped with an error code.
    pause
)
exit /b 0
