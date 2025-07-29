@echo off
setlocal
set JAR=openccjavacli\build\libs\openccjavacli-1.0.0.jar
set LIB=lib\*
java -cp "%JAR%;%LIB%" openccjavacli.Main %*
