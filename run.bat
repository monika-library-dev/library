@echo off
title Library Book Issue System
echo =======================================================
echo    Starting Library Book Issue Management System
echo =======================================================

REM Set Java 21 and Maven Environment
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
set "M2_HOME=C:\Program Files\apache-maven\apache-maven-3.9.16"
set "PATH=%JAVA_HOME%\bin;%M2_HOME%\bin;%PATH%"

cd /d "%~dp0"

echo Opening browser in 3 seconds...
start "" cmd /c "timeout /t 3 /nobreak >nul & start http://localhost:8080"

echo Starting server...
if exist "target\library-issue-management-1.0.0.jar" (
    java -jar "target\library-issue-management-1.0.0.jar"
) else (
    call mvn spring-boot:run
)
pause
