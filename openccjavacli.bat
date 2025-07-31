@echo off
setlocal
set JRE=java
set JAR=openccjavacli\build\libs\openccjavacli-1.0.0.jar
set LIB=lib\*
rem java -cp "%JAR%;%LIB%" openccjavacli.Main %*
%JRE% -cp "%JAR%;%LIB%" openccjavacli.Main %*
