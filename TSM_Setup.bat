@echo off
chcp 65001 >nul
title TSM_DODO — All-in-One Setup & Control Panel
color 0B
echo ================================================
echo   TSM_DODO — All-in-One Setup & Control Panel
echo   1 file duy nhat: Auto-Setup + Server Control
echo ================================================
echo.

python "%~dp0TSM_Setup.py" %*

if %ERRORLEVEL% neq 0 (
    echo.
    echo [!] Khong the chay. Kiem tra Python da cai.
    echo     Tai: https://www.python.org/downloads/
    echo.
    pause
)
