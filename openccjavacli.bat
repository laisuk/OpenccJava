@echo off
setlocal
set JRE=C:\Java\zulu11.82.19-ca-jdk11.0.28-win_x64\bin\java.exe
set JAR=openccjavacli\build\libs\openccjavacli-1.0.0.jar
set LIB=lib\*
rem java -cp "%JAR%;%LIB%" openccjavacli.Main %*
%JRE% -cp "%JAR%;%LIB%" openccjavacli.Main %*
