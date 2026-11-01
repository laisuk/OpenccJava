
@echo off
setlocal
cd /d "%~dp0"

set "PORT=8080"
set "WEB_DIR=openccjavawasm\build\generated\teavm"

echo ========================================
echo OpenccJava TeaVM Browser Demo
echo ========================================
echo.

echo [1/3] Building JavaScript and Wasm GC...
call gradlew.bat :openccjavawasm:browserJavaScript :openccjavawasm:browserWasmGC
if errorlevel 1 (
    echo.
    echo ERROR: Gradle build failed.
    pause
    exit /b 1
)

echo.
echo [2/3] Starting HTTP server...
start "OpenccJava Demo Server" cmd /k python -m http.server %PORT% --directory "%WEB_DIR%"

echo.
echo [3/3] Opening browser...
timeout /t 2 /nobreak >nul
start "" "http://localhost:%PORT%/?backend=wasm"

echo.
echo Demo: http://localhost:%PORT%/
echo JavaScript: http://localhost:%PORT%/?backend=js
echo Wasm GC: http://localhost:%PORT%/?backend=wasm
echo.
echo Close the server window to stop the demo.

endlocal
