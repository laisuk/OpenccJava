@echo off
setlocal enabledelayedexpansion

set "BASE=%~dp0.."
set "JRE=java"
set "LIB=%BASE%\lib\*"

rem Find the newest JAR inside openccjavacli\build\libs
for /f "delims=" %%F in ('dir /b /o-d "%BASE%\openccjavacli\build\libs\openccjavacli-*.jar" 2^>nul') do (
    set "JAR=%BASE%\openccjavacli\build\libs\%%F"
    goto :found
)

echo Error: No JAR found in %BASE%\openccjavacli\build\libs
exit /b 1

:found
"%JRE%" -cp "%JAR%;%LIB%" openccjavacli.Main %*