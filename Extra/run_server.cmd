@echo off
title SinChat Server Launcher
color 0B
echo ===================================================
echo             STARTING SINCHAT TCP SERVER            
echo ===================================================
echo.

:: Navigate to Server directory
cd /d "%~dp0..\Code\Server"

echo [1/2] Compiling Server code...
call mvn clean compile
if %ERRORLEVEL% NEQ 0 (
    color 0C
    echo.
    echo ===================================================
    echo [ERROR] Server compilation failed!
    echo ===================================================
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [2/2] Running TCP Server on Port 3000...
echo ===================================================
call mvn exec:java -Dexec.mainClass="com.server.Main"
if %ERRORLEVEL% NEQ 0 (
    color 0C
    echo.
    echo [ERROR] Server stopped with an error code.
)
exit /b 0
