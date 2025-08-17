@echo off
setlocal enabledelayedexpansion

set "JRE=java"
set "LIB=lib\*"

rem Find the newest JAR inside openccjavacli\build\libs
for /f "delims=" %%F in ('dir /b /o-d "openccjavacli\build\libs\openccjavacli-*.jar" 2^>nul') do (
    set "JAR=openccjavacli\build\libs\%%F"
    goto :found
)

echo Error: No JAR found in openccjavacli\build\libs
exit /b 1

:found
"%JRE%" -cp "%JAR%;%LIB%" openccjavacli.Main %*
