@echo off
REM Build & chay client tren Windows. JavaFX can dung mvn javafx:run de
REM auto-set module-path cho native libs.
setlocal
cd /d "%~dp0"

echo [1/2] Building client (mvn package -DskipTests)...
call mvn -pl shared,client -am -DskipTests package
if errorlevel 1 goto :error

echo [2/2] Starting JavaFX client via mvn javafx:run...
call mvn -pl client javafx:run
goto :eof

:error
echo BUILD FAILED.
exit /b 1
