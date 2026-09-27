@echo off
chcp 65001 >nul
title TSM Eve.emg Editor ^& Inspector
cd /d "%~dp0"
echo ========================================================
echo   KHOI DONG TSM EVE.EMG EDITOR & INSPECTOR
echo ========================================================
python tools\eve_tool.py
pause
