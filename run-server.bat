@echo off
REM Build & chay server tren Windows. Yeu cau JDK 17+ va Maven 3.6+ trong PATH.
setlocal
cd /d "%~dp0"

echo [1/2] Building server (mvn package)...
call mvn -pl shared,server -am -DskipTests package
if errorlevel 1 goto :error

echo [2/2] Starting AuctionServer...
java -jar server\target\server-1.0-SNAPSHOT.jar
goto :eof

:error
echo BUILD FAILED.
exit /b 1
