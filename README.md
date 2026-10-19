# Meowl Client – Minecraft 26.3

Dieser Branch enthält das **fertig portierte Projekt für Minecraft 26.3**.

Der Code wird nicht von Hand gepflegt, sondern aus dem Branch `1.21.11` erzeugt
(`port26\port.ps1` mit `port26\rules.txt` und den Dateien in `port26\overlay`).
Änderungen also bitte im Branch `1.21.11` machen und neu portieren.

Bauen: `gradlew.bat build -x test` (JDK 25) – die Jar landet in `dist\`.