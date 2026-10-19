@echo off
setlocal EnableDelayedExpansion
chcp 65001 >nul
title Survival-Server (Paper 26.3) auf VPS installieren / updaten
cd /d "%~dp0"
echo.
echo  ==========================================================
echo    Survival-Server (Paper 26.3, 35 Spieler)
echo    mit Voice Chat, Vanish und Beitritt auch mit 1.21.11
echo    auf deinem VPS installieren oder updaten.
echo    Der Meowl Server bleibt dabei unberührt.
echo  ==========================================================
echo.
if not exist "%~dp0minecraft-server-setup.sh" (
  echo FEHLER: minecraft-server-setup.sh fehlt in diesem Ordner.
  goto ende
)
where ssh >nul 2>nul
if errorlevel 1 (
  echo FEHLER: Windows-SSH fehlt.
  echo Einstellungen ^> Apps ^> Optionale Features ^> "OpenSSH-Client" hinzufügen, dann nochmal starten.
  goto ende
)
echo  Für einen Minecraft-Server musst du die Minecraft-EULA akzeptieren:
echo  https://aka.ms/MinecraftEULA
set "EULA="
set /p EULA=Akzeptierst du die EULA? (j/n): 
if /i not "%EULA%"=="j" (
  echo Ohne EULA kann der Server nicht gestartet werden.
  goto ende
)
echo.
set "IP="
set /p IP=IP-Adresse deines VPS (z.B. 123.45.67.89): 
if "%IP%"=="" goto ende
set "USR="
set /p USR=Benutzername (einfach Enter = root): 
if "%USR%"=="" set "USR=root"
echo.
echo  Spieler für die Whitelist, mit Komma getrennt.
echo  Der ERSTE Name wird Admin (OP) - also am besten deiner zuerst.
set "PLAYERS="
set /p PLAYERS=Spieler (z.B. DeinName,Freund1,Freund2): 
if not defined PLAYERS (
  echo Mindestens ein Spieler ist nötig - sonst kommt wegen der Whitelist niemand rein.
  goto ende
)
set "PLAYERS=%PLAYERS: =%"
set "PLAYERS=%PLAYERS:'=%"
set "MOTD="
set /p MOTD=Server-Beschreibung (einfach Enter = "Survival mit Freunden"): 
if defined MOTD set "MOTD=%MOTD:"=%"
if defined MOTD set "MOTD=%MOTD:'=%"
set "RUN=sudo bash"
if /i "%USR%"=="root" set "RUN=bash"
echo.
echo  Gleich wirst du ZWEIMAL nach dem Passwort deines VPS gefragt
echo  (einmal zum Hochladen, einmal zum Installieren). Beim Tippen
echo  sieht man nichts - das ist normal. Danach Enter.
echo.
pause
scp "%~dp0minecraft-server-setup.sh" %USR%@%IP%:
if errorlevel 1 (
  echo.
  echo FEHLER beim Hochladen. Stimmen IP, Benutzername und Passwort?
  goto ende
)
ssh -t %USR%@%IP% "%RUN% ~/minecraft-server-setup.sh --accept-eula '%PLAYERS%' '%MOTD%'"
if errorlevel 1 (
  echo.
  echo Die Installation meldet einen Fehler - die rote Meldung oben sagt warum.
  goto ende
)
echo.
echo  Fertig! Verbinde dich mit Minecraft 26.3 oder 1.21.11 mit der IP oben.
echo  Fuer Voice Chat: Mod "Simple Voice Chat" im Client installieren.
echo  Web-Panel: Adresse und Passwort stehen oben im Kasten.
echo  Nochmal starten = Paper-Update + neue Spieler auf die Whitelist.
:ende
echo.
pause
