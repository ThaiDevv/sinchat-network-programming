@echo off
title SinChat Client - Railway Mode
color 0E

echo ===================================================
echo   STARTING SINCHAT TCP CLIENT (RAILWAY / REMOTE MODE)
echo ===================================================
echo.

:: Default Railway TCP proxy. Override with: set TCP_HOST=... & set TCP_PORT=...
if not defined TCP_HOST set "TCP_HOST=acela.proxy.rlwy.net"
if not defined TCP_PORT set "TCP_PORT=45139"

echo [Mode] Remote TCP host mode: %TCP_HOST%:%TCP_PORT%
echo        Client will connect directly to the remote server.
echo        To override, set TCP_HOST / TCP_PORT before running this script.
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
echo [2/2] Launching JavaFX Client (Remote: %TCP_HOST%:%TCP_PORT%)...
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
