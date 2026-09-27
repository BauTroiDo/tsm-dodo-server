@echo off
chcp 65001 >nul
title TSM_DODO Launcher
echo ============================================
echo   TSM_DODO Launcher (Portable)
echo   Tu dong do JDK 21 / Python / ADB
echo ============================================
echo.

set "ROOT=%~dp0"
cd /d "%ROOT%"

rem ============================================================
rem 1. Tu do tim JDK 21
rem ============================================================
set "JAVA_HOME="

rem 1a. Kiem tra JAVA_HOME da co
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" goto :java_found
)

rem 1b. Kiem tra cac duong dan pho bien
for %%J in (
    "C:\Program Files\Java\jdk-21"
    "C:\Program Files\Java\jdk-21.0.11"
    "C:\Program Files\Java\jdk-21.0.10"
    "C:\Program Files\Java\jdk-21.0.8"
    "C:\Program Files\Eclipse Adoptium\jdk-21"
    "C:\Program Files\Microsoft\jdk-21"
    "C:\Program Files\Zulu\zulu-21"
    "C:\Program Files\BellSoft\LibericaJDK-21"
    "C:\Program Files\Amazon Corretto\jdk-21"
    "D:\Program Files\Java\jdk-21"
    "D:\Program Files\Eclipse Adoptium\jdk-21"
    "D:\Program Files\Microsoft\jdk-21"
    "E:\Soft\AndroidStudio\jbr"
    "D:\Soft\AndroidStudio\jbr"
    "C:\Soft\AndroidStudio\jbr"
) do (
    if not defined JAVA_HOME if exist "%%~J\bin\java.exe" set "JAVA_HOME=%%~J"
)

rem 1c. Neu van chua tim thay, quet cac o dia
if not defined JAVA_HOME (
    for %%D in (C D E F G) do (
        if not defined JAVA_HOME if exist "%%D:\Program Files\Java" (
            for /d %%P in ("%%D:\Program Files\Java\jdk-21*") do (
                if not defined JAVA_HOME if exist "%%P\bin\java.exe" set "JAVA_HOME=%%P"
            )
        )
    )
    if not defined JAVA_HOME for %%D in (C D E F G) do (
        if not defined JAVA_HOME if exist "%%D:\Soft\AndroidStudio\jbr\bin\java.exe" set "JAVA_HOME=%%D:\Soft\AndroidStudio\jbr"
    )
)

:java_found
if not defined JAVA_HOME (
    echo [LOI] Khong tim thay JDK 21!
    echo Vui long cai JDK 21 hoac chinh sua JAVA_HOME trong file nay.
    echo Tai JDK 21: https://adoptium.net/temurin/releases/?version=21
    pause
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
echo [OK] JDK: %JAVA%

rem ============================================================
rem 2. Kiem tra Python
rem ============================================================
where python >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [LOI] Khong tim thay Python!
    echo Vui long cai Python 3.x: https://www.python.org/downloads/
    pause
    exit /b 1
)
echo [OK] Python da co san

rem ============================================================
rem 3. Kiem tra MySQL
rem ============================================================
set "DB_HOST=127.0.0.1"
set "DB_PORT=3307"
set "DB_USER=root"
set "DB_PASS="
set "DB_NAME=tsm_dodo"

if exist "launcher_config.json" (
    for /f "tokens=1* delims==" %%A in ('python -c "import json; c=json.load(open('launcher_config.json', encoding='utf-8')); [print(f'{k}={v}') for k, v in [('DB_HOST', c.get('mysql_host','127.0.0.1')), ('DB_PORT', c.get('mysql_port',3307)), ('DB_USER', c.get('mysql_user','root')), ('DB_PASS', c.get('mysql_password','')), ('DB_NAME', c.get('mysql_database','tsm_dodo'))]]" 2^>nul') do set "%%A=%%B"
)

netstat -ano | findstr ":%DB_PORT%" | findstr "LISTENING" >nul 2>nul
if %ERRORLEVEL% neq 0 (
    netstat -ano | findstr ":3306" | findstr "LISTENING" >nul 2>nul
    if %ERRORLEVEL% neq 0 (
        echo [CANH BAO] MySQL khong chay tren port %DB_PORT% hoac 3306.
        echo Vui long bat XAMPP / MySQL Service truoc khi vao game.
        echo.
    ) else (
        echo [OK] MySQL dang chay tren port 3306.
        set "DB_PORT=3306"
    )
) else (
    echo [OK] MySQL dang chay tren port %DB_PORT%.
)

rem ============================================================
rem 4. Khoi dong cac server
rem ============================================================
echo.
echo [1/3] Starting Nox ADB reverse watcher...
start "Nox ADB Reverse" cmd /k "chcp 65001 >nul && cd /d "%ROOT%" && adb_reverse_nox_127001.bat"

echo [2/3] Starting CDN server (port 8080)...
start "CDN Server" cmd /k "chcp 65001 >nul && cd /d "%ROOT%" && python cdn_server.py --http --port 8080"

echo [3/3] Starting game server (port 6613)...
start "Game Server" cmd /k "chcp 65001 >nul && cd /d "%ROOT%" && start_game_server.bat"

echo.
echo ============================================
echo Da mo cac cua so:
echo   - Nox ADB Reverse watcher
echo   - CDN Server  (port 8080)
echo   - Game Server (port 6613, build 15-30 giay)
echo ============================================
echo Giu cac cua so nay mo khi choi.
echo Nhan phim bat ky de dong cua so launcher nay...
pause >nul
