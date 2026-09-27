@echo off
chcp 65001 >nul
title TSM_DODO — Công cụ Quản trị Server
color 0D
echo ================================================
echo   TSM_DODO — Công cụ Quản trị Server (Tiếng Việt)
echo   Quản lý database, cấu hình, dò lỗi, người chơi
echo ================================================
echo.

python "%~dp0tsm_admin.py" %*

if %ERRORLEVEL% neq 0 (
    echo.
    echo [!] Không thể chạy. Kiểm tra Python đã cài.
    echo     Tải: https://www.python.org/downloads/
    echo.
    pause
)
