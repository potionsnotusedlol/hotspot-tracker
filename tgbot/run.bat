@echo off
chcp 65001 >nul
cd /d "%~dp0"

rem --- Ищем Java: JAVA_HOME, затем PATH, затем JDK, скачанные средой разработки ---
set "JAVA_CMD=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
where java >nul 2>nul
if errorlevel 1 (
    for /d %%J in ("%USERPROFILE%\.jdks\*") do if exist "%%J\bin\java.exe" set "JAVA_CMD=%%J\bin\java.exe"
)

if not exist "target\telegram-bot.jar" (
    echo Файл target\telegram-bot.jar не найден. Сначала соберите проект ^(см. README.md^).
    pause
    exit /b 1
)

if "%BOT_TOKEN%"=="" (
    set /p BOT_TOKEN=Введите токен бота от @BotFather:
)

"%JAVA_CMD%" -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar target\telegram-bot.jar
pause
