@echo off
setlocal
set JAR=build\libs\OpenccJava-1.0.0.jar
set LIB=lib\*
java -cp "%JAR%;%LIB%" openccjavacli.Main %*
