@echo off
setlocal
powershell.exe -NoProfile -Sta -ExecutionPolicy Bypass -File "%~dp0cumulative\run-update.ps1" %*
if errorlevel 1 pause
