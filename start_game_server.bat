@echo off
chcp 65001 >nul
title TSM_DODO Game Server (Portable)
set "ROOT=%~dp0"
cd /d "%ROOT%"

rem ============================================================
rem Tu do tim JDK 21
rem ============================================================
set "JAVA_HOME="

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" goto :java_found
)

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
    "E:\Soft\AndroidStudio\jbr"
    "D:\Soft\AndroidStudio\jbr"
    "C:\Soft\AndroidStudio\jbr"
) do (
    if not defined JAVA_HOME if exist "%%~J\bin\java.exe" set "JAVA_HOME=%%~J"
)

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
    echo Vui long cai JDK 21: https://adoptium.net/temurin/releases/?version=21
    pause
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
set "GAMEDATA_PATH=%ROOT%cdn\mirror\tsr\2.4.76\ResourcesPacked"
set "GAMEDATA_PATH=%GAMEDATA_PATH:\=/%"
set "SERVER_DATA_PATH=%ROOT%gamedata"
set "SERVER_DATA_PATH=%SERVER_DATA_PATH:\=/%"

set "DB_HOST=127.0.0.1"
set "DB_PORT=3307"
set "DB_USER=root"
set "DB_PASS="
set "DB_NAME=tsm_dodo"

if exist "launcher_config.json" (
    for /f "tokens=1* delims==" %%A in ('python -c "import json; c=json.load(open('launcher_config.json', encoding='utf-8')); [print(f'{k}={v}') for k, v in [('DB_HOST', c.get('mysql_host','127.0.0.1')), ('DB_PORT', c.get('mysql_port',3307)), ('DB_USER', c.get('mysql_user','root')), ('DB_PASS', c.get('mysql_password','')), ('DB_NAME', c.get('mysql_database','tsm_dodo'))]]" 2^>nul') do set "%%A=%%B"
)

set "DB_URL=jdbc:mysql://%DB_HOST%:%DB_PORT%/%DB_NAME%?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Taipei&characterEncoding=UTF-8"

rem 9-pet production profile: match the staging runtime that passed gameplay testing
set "NINE_PET_ENABLED=true"
set "NINE_PET_MAX_CARRIED=9"
set "NINE_PET_PROGRESSION_ENABLED=true"

echo [%date% %time%] Starting Game Server...
echo JAVA_HOME=%JAVA_HOME%
echo GAMEDATA_PATH=%GAMEDATA_PATH%
echo SERVER_DATA_PATH=%SERVER_DATA_PATH%
echo DB_URL=%DB_URL%
echo DB_USER=%DB_USER%
echo.
set "DB_PASSWORD=%DB_PASS%"
set "JAVA_TOOL_OPTIONS=-DDB_URL=%DB_URL% -DDB_USER=%DB_USER% -DDB_PASSWORD=%DB_PASS%"
gradlew.bat app:run
echo [%date% %time%] Game Server exited with code %ERRORLEVEL%
pause
