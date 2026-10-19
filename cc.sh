#!/usr/bin/env bash
# Schnelle Kompilierprüfung: zeigt nur Fehler
cd /home/claude/meowl
CP=$(ls libs/mc/minecraft-named.jar libs/lib/*.jar libs/fapi/*.jar project/src/main/resources/META-INF/jars/*.jar | tr '\n' ':')
rm -rf /tmp/cc && mkdir -p /tmp/cc
javac -nowarn -encoding UTF-8 --release 21 -proc:none -Xmaxerrs 500 -cp "$CP" -d /tmp/cc $(find project/src/main/java -name "*.java") 2>&1 | grep -v "JAVA_TOOL_OPTIONS\|^Note:" 
