@echo off
chcp 65001 >nul
title Meowl Client bauen
cd /d "%~dp0"
echo.
echo  ==========================================================
echo    Meowl Client bauen
echo    Version steht in project\src\main\resources\fabric.mod.json
echo  ==========================================================
echo.
call gradlew.bat build -x test --console=plain
if errorlevel 1 (
  echo.
  echo FEHLER beim Bauen - die Meldung oben sagt warum.
  goto ende
)
echo.
echo  Server-Skript bauen ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server\build-server.ps1"
if errorlevel 1 (
  echo FEHLER beim Bauen des Servers.
  goto ende
)
set "VPS=%USERPROFILE%\Downloads\Meowl-VPS"
if exist "%VPS%" copy /y "%~dp0server\dist\meowl-server-setup.sh" "%VPS%\" >nul
for %%F in ("%~dp0dist\meowl-client-*.jar") do set "JAR=%%~fF"
for /f "delims=" %%F in ('dir /b /o:d "%~dp0dist\meowl-client-*.jar"') do set "JAR=%~dp0dist\%%F"
if exist "%VPS%" (
  copy /y "%JAR%" "%VPS%\" >nul
  echo.
  echo  Fertig: %JAR%
  echo  Kopiert nach %VPS%
  echo.
  echo  Veröffentlichen: im Ordner Meowl-VPS
  echo  "Meowl-Server-auf-VPS-installieren.bat" starten.
) else (
  echo.
  echo  Fertig: %JAR%
)
:ende
echo.
pause
