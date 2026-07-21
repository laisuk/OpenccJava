@echo off
setlocal

rem Use UTF-8 for Unicode command-line arguments and console output.
chcp 65001 >nul

set "JRE=java"
set "LIB=lib\*"

rem Find the newest CLI JAR by modification time.
for /f "delims=" %%F in ('dir /b /a-d /o-d "openccjavacli\build\libs\openccjavacli-*.jar" 2^>nul') do (
    set "JAR=openccjavacli\build\libs\%%F"
    goto :found
)

echo Error: No JAR found in openccjni-cli\build\libs
endlocal
exit /b 1

:found
"%JRE%" ^
  -Dfile.encoding=UTF-8 ^
  -Dsun.jnu.encoding=UTF-8 ^
  -cp "%JAR%;%LIB%" ^
  openccjavacli.Main %*

set "EXIT_CODE=%ERRORLEVEL%"
endlocal & exit /b %EXIT_CODE%