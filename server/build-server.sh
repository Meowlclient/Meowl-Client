#!/usr/bin/env bash
# Baut den Meowl Server und bettet ihn in dist/meowl-server-setup.sh ein.
set -euo pipefail
cd "$(dirname "$0")"
VERSION=$(grep -o 'VERSION = "[0-9.]*"' src/com/lennart/meowlserver/MeowlServer.java | head -1 | grep -o '[0-9.]*')
rm -rf build && mkdir -p build dist
javac -g -nowarn --release 21 -d build $(find src -name "*.java") 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
[ -f build/com/lennart/meowlserver/MeowlServer.class ] || { echo "Kompilieren fehlgeschlagen"; exit 1; }
printf 'Manifest-Version: 1.0\nMain-Class: com.lennart.meowlserver.MeowlServer\n' > build/MANIFEST.MF
(cd build && jar --create --file ../dist/meowl-server.jar --manifest MANIFEST.MF com) 2>&1 | grep -v JAVA_TOOL_OPTIONS || true
sed "s/^MEOWL_VERSION=\".*\"/MEOWL_VERSION=\"$VERSION\"/" setup-head.sh > dist/meowl-server-setup.sh
base64 -w 76 dist/meowl-server.jar >> dist/meowl-server-setup.sh
cat setup-tail.sh >> dist/meowl-server-setup.sh
chmod +x dist/meowl-server-setup.sh
echo "==> Fertig: dist/meowl-server-setup.sh (Server $VERSION)"
