@echo off
chcp 65001 >nul
title TS Mobile Online - Lua Crypt Tool GUI
cd /d "%~dp0"

echo Dang khoi dong giao dien Lua Crypt Tool...
python lua_crypt_gui.py
if %errorlevel% neq 0 (
    echo.
    echo [LOI] Khong the khoi chay GUI. Vui long kiem tra Python da duoc cai dat.
    pause
)
