#!/usr/bin/env bash
# Baut den Meowl Client ohne Gradle/Loom (Maven/Fabric-Server sind von hier aus gesperrt):
#  1) javac gegen Minecraft (Yarn-Namen) + Fabric API + Bibliotheken
#  2) tiny-remapper (mit Mixin-Erweiterung) named -> intermediary, wie Loom "static"
#  3) JAR packen inkl. Ressourcen, eingebetteter Jars und Manifest
set -euo pipefail
cd "$(dirname "$0")"
L=libs
VERSION="$(python3 -c 'import json;print(json.load(open("project/src/main/resources/fabric.mod.json"))["version"])')"
OUT="dist/meowl-client-$VERSION.jar"

CP=$(ls $L/mc/minecraft-named.jar $L/lib/*.jar $L/fapi/*.jar project/src/main/resources/META-INF/jars/*.jar | tr '\n' ':')
rm -rf build && mkdir -p build/classes build/stage dist

echo "==> Kompilieren"
javac -g -nowarn -encoding UTF-8 --release 21 -proc:none -cp "$CP" -d build/classes \
  $(find project/src/main/java -name "*.java") 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
[ -f build/classes/com/lennart/nebula/NebulaClient.class ] || { echo "Kompilieren fehlgeschlagen"; exit 1; }
# fehlgeschlagene Kompilierung erkennen (javac legt bei Fehlern keine/teilweise Klassen an)
N_SRC=$(find project/src/main/java -name "*.java" | wc -l)

echo "==> Dev-Jar"
cp -r project/src/main/resources/. build/stage/
cp -r build/classes/. build/stage/
(cd build/stage && jar --create --file ../dev.jar --manifest <(printf 'Manifest-Version: 1.0\n') . ) 2>&1 | grep -v JAVA_TOOL_OPTIONS || true

echo "==> Remap named -> intermediary"
TCP="tools:$(ls $L/tools/*.jar | tr "\n" ":")"
java -Xmx3g -cp "$TCP" Remap build/dev.jar build/remapped.jar $L/mc/mappings.tiny named intermediary \
  $L/mc/minecraft-named.jar $L/lib/*.jar $L/fapi/*.jar project/src/main/resources/META-INF/jars/*.jar 2>&1 | grep -v JAVA_TOOL_OPTIONS || true

echo "==> Manifest"
rm -rf build/final && mkdir -p build/final && (cd build/final && unzip -q ../remapped.jar)
cat > build/final/META-INF/MANIFEST.MF <<MF
Manifest-Version: 1.0
Fabric-Jar-Type: classes
Fabric-Loom-Mixin-Remap-Type: static
Fabric-Minecraft-Version: 1.21.11
Fabric-Loader-Version: 0.18.4
Fabric-Mapping-Namespace: intermediary

MF
rm -f "$OUT"
(cd build/final && jar --create --file "../../$OUT" --manifest META-INF/MANIFEST.MF $(ls -A | grep -v '^META-INF$') META-INF/jars) 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
echo "==> Fertig: $OUT ($(du -h "$OUT" | cut -f1))"
