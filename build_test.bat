@echo off
set "JAVA_HOME=C:\Program Files\Java\jdk-21.0.10+7"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call gradlew.bat build -x test
echo EXIT_CODE=%ERRORLEVEL%
