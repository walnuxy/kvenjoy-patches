@echo off
setlocal
powershell.exe -NoProfile -Sta -ExecutionPolicy Bypass -File "%~dp0run-font-patcher.ps1" %*
set "patch_exit=%ERRORLEVEL%"
if not "%patch_exit%"=="0" (
    echo The patcher did not finish. See the error dialog.
    pause
)
exit /b %patch_exit%
