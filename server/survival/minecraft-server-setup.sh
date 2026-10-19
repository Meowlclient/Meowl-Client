#!/usr/bin/env bash
# =====================================================================
#  Survival-Server (Paper 26.3) – Installation / Update auf dem VPS
#  Läuft komplett getrennt vom Meowl Server (eigener Ordner, Port, Dienst).
#  Plugins: Simple Voice Chat, SuperVanish, ViaVersion + ViaBackwards, OpenInv
#  (ältere Clients wie 1.21.11 können dadurch auch beitreten).
#
#  Benutzung (als root):
#     bash minecraft-server-setup.sh --accept-eula "Spieler1,Spieler2" "Beschreibung"
#  Nochmal ausführen = Paper-Update + neue Spieler auf die Whitelist (Welt bleibt erhalten)
# =====================================================================
set -euo pipefail

MC_VERSION="26.3"
# Falls Paper für 26.3 noch nichts veröffentlicht hat: nächstbeste neuere Version (nie älter als die Welt)
MC_FALLBACKS="26.2 26.1.2 26.1.1 26.1"
JAVA_FEATURE=25
DIR=/opt/minecraft
PORT=25565
VOICE_PORT=24454
RCON_PORT=25575
SERVICE=minecraft

c_ok()   { printf '\033[1;32m[OK]\033[0m %s\n' "$*"; }
c_step() { printf '\n\033[1;35m==> %s\033[0m\n' "$*"; }
c_warn() { printf '\033[1;33m[!]\033[0m %s\n' "$*"; }
fail()   { printf '\033[1;31m[FEHLER]\033[0m %s\n' "$*"; exit 1; }

EULA="${1:-}"
PLAYERS="${2:-}"
MOTD="${3:-}"
[ "$(id -u)" -eq 0 ] || fail "Bitte als root starten"
[ "$EULA" = "--accept-eula" ] || fail "Die Minecraft-EULA muss akzeptiert werden (https://aka.ms/MinecraftEULA)"
[ -r /etc/os-release ] && . /etc/os-release || fail "Unbekanntes Betriebssystem"
case "${ID:-}${ID_LIKE:-}" in
  *ubuntu*|*debian*) ;;
  *) fail "Dieses Skript ist für Ubuntu/Debian. Gefunden: ${PRETTY_NAME:-?}" ;;
esac
[ -z "$MOTD" ] && MOTD="Survival mit Freunden"
MOTD="$(printf '%s' "$MOTD" | tr -d '\\\n\r' | cut -c1-59)"

export DEBIAN_FRONTEND=noninteractive

# ---------------------------------------------------------------- Pakete
c_step "Pakete"
# Eine früher angelegte, aber für dieses System nicht passende Adoptium-Paketquelle entfernen
if ! apt-get update -qq >/dev/null 2>&1; then
  for f in /etc/apt/sources.list.d/*; do
    [ -f "$f" ] && grep -q "packages.adoptium.net" "$f" && rm -f "$f" && echo "  entfernt: $f"
  done
  [ -f /etc/apt/sources.list ] && sed -i '/packages\.adoptium\.net/d' /etc/apt/sources.list
  apt-get update -qq || fail "Paketquellen fehlerhaft – siehe Meldung oben"
fi
apt-get install -y -qq curl ca-certificates gnupg python3 python3-yaml >/dev/null
c_ok "Grundpakete da"

# Paper 26.x verlangt ein offizielles Java 25 (keine "-ea"-Vorabversion wie bei manchen Ubuntu-Paketen).
# Darum bekommt der Survival-Server sein eigenes Temurin-Java – das System-Java (Meowl Server) bleibt, wie es ist.
java_line() { "$1" -version 2>&1 | sed -n 1p || true; }
# Offizielles Java 25+ (ohne „-ea“) – ohne grep -q in Pipes (pipefail würde sonst falsch melden)
ga_java() {
  [ -x "$1" ] || return 1
  local v; v="$(java_line "$1")"
  [[ "$v" =~ version\ \"([0-9]+) ]] || return 1
  [ "${BASH_REMATCH[1]}" -ge "$JAVA_FEATURE" ] || return 1
  [[ "$v" == *-ea* ]] && return 1
  return 0
}
JAVA_BIN=""
for cand in "$DIR/java/bin/java" /usr/lib/jvm/temurin-2[5-9]-*/bin/java; do ga_java "$cand" && JAVA_BIN="$cand" && break; done
if [ -z "$JAVA_BIN" ] && ga_java "$(command -v java 2>/dev/null || echo /nonexistent)"; then
  JAVA_BIN="$(readlink -f "$(command -v java)")"
fi
if [ -z "$JAVA_BIN" ]; then
  # Temurin direkt von Adoptium (unabhängig von der Linux-Version, kein apt, System-Java bleibt unverändert)
  c_step "Java $JAVA_FEATURE (Temurin) für den Survival-Server"
  case "$(uname -m)" in
    x86_64|amd64) ARCH=x64 ;;
    aarch64|arm64) ARCH=aarch64 ;;
    *) ARCH="" ;;
  esac
  if [ -n "$ARCH" ]; then
    JINFO="$(curl -fsSL "https://api.adoptium.net/v3/assets/latest/$JAVA_FEATURE/hotspot?architecture=$ARCH&image_type=jre&os=linux&vendor=eclipse" || true)"
    if [ -n "$JINFO" ]; then
      read -r J_URL J_SHA < <(printf '%s' "$JINFO" | python3 -c '
import json,sys
a=json.load(sys.stdin)[0]["binary"]["package"]
print(a["link"], a["checksum"])') || true
      if [ -n "${J_URL:-}" ]; then
        TMPJ="$(mktemp)"
        if curl -fsSL -o "$TMPJ" "$J_URL" && [ "$(sha256sum "$TMPJ" | cut -d" " -f1)" = "$J_SHA" ]; then
          rm -rf "$DIR/java.new" && mkdir -p "$DIR/java.new"
          tar -xzf "$TMPJ" -C "$DIR/java.new" --strip-components=1
          rm -rf "$DIR/java" && mv "$DIR/java.new" "$DIR/java"
        fi
        rm -f "$TMPJ"
      fi
    fi
  fi
  ga_java "$DIR/java/bin/java" && JAVA_BIN="$DIR/java/bin/java"
fi
EXTRA_FLAGS=""
if [ -z "$JAVA_BIN" ]; then
  fail "Java $JAVA_FEATURE ließ sich nicht installieren (api.adoptium.net nicht erreichbar?) – Minecraft 26.x läuft nur mit Java $JAVA_FEATURE"
fi
[[ "$(java_line "$JAVA_BIN")" == *-ea* ]] && EXTRA_FLAGS="-DPaper.IgnoreJavaVersion=true"
c_ok "Java für den Survival-Server: $(java_line "$JAVA_BIN")  ($JAVA_BIN)"

WAS_RUNNING=0
if systemctl is-active --quiet "$SERVICE" 2>/dev/null; then
  WAS_RUNNING=1
  c_step "Server wird für das Update gestoppt (Welt wird vorher gespeichert)"
  systemctl stop "$SERVICE"
fi
# ---------------------------------------------------------------- Speicher (gemessen, während der Survival-Server aus ist)
c_step "Arbeitsspeicher"
TOTAL_MB=$(( $(grep MemTotal /proc/meminfo | awk '{print $2}') / 1024 ))
# Freier Speicher, während der Survival-Server aus ist (Meowl Server und System laufen schon). Java braucht
# zusätzlich ~1,5 GB über -Xmx hinaus, dazu etwas Reserve – sonst beendet Linux den Server (oom-kill).
AVAIL_MB=$(( $(grep MemAvailable /proc/meminfo | awk '{print $2}') / 1024 ))
XMX=$(( AVAIL_MB - 1700 ))
[ "$XMX" -gt 4608 ] && XMX=4608
if [ "$XMX" -lt 1024 ]; then
  c_warn "Der VPS hat nur ${TOTAL_MB} MB RAM – für einen Minecraft-Server ist das sehr knapp."
  c_warn "Er läuft trotzdem mit 1024 MB, kann aber ruckeln. Mehr RAM beim Anbieter buchen hilft."
  XMX=1024
fi
# Spielerzahl nach Arbeitsspeicher: 20 nur, wenn der Server das sicher aushält
# 35 Plätze; der Arbeitsspeicher bestimmt nur, wie flüssig es bei vielen Spielern gleichzeitig läuft
MAX_PLAYERS=35
# Simulationsweite kostet am meisten Rechenzeit – klein halten, Sichtweite darf größer sein
if [ "$XMX" -ge 3500 ]; then VIEW=10; SIM=5; else VIEW=8; SIM=5; fi
c_ok "VPS: ${TOTAL_MB} MB gesamt, ${AVAIL_MB} MB frei → Survival-Server bekommt ${XMX} MB → bis zu ${MAX_PLAYERS} Spieler"
[ "$XMX" -lt 5000 ] && c_warn "Hinweis: Für 35 Spieler gleichzeitig wären ~7,5 GB freier RAM ideal (jetzt ${AVAIL_MB} MB frei). Bei sehr vielen Spielern kann es ruckeln." || true

# ---------------------------------------------------------------- Benutzer + Ordner
id -u minecraft >/dev/null 2>&1 || useradd --system --home "$DIR" --shell /usr/sbin/nologin minecraft
mkdir -p "$DIR" "$DIR/backups" "$DIR/plugins"


# ---------------------------------------------------------------- Paper laden
c_step "Paper $MC_VERSION herunterladen"
WANTED="$MC_VERSION"
INFO=""
for v in $MC_VERSION $MC_FALLBACKS; do
  INFO="$(curl -fsSL -A "meowl-setup" "https://fill.papermc.io/v3/projects/paper/versions/$v/builds/latest" 2>/dev/null || true)"
  if [[ "$INFO" == *'"server:default"'* ]]; then MC_VERSION="$v"; break; fi
  INFO=""
done
[ -n "$INFO" ] || fail "Kein Paper-Build für $WANTED gefunden (fill.papermc.io nicht erreichbar?)"
[ "$MC_VERSION" = "$WANTED" ] || c_warn "Paper hat für $WANTED noch keinen Build – nehme $MC_VERSION. Später einfach nochmal starten, dann kommt $WANTED."
read -r PAPER_NAME PAPER_URL PAPER_SHA PAPER_CHANNEL < <(printf '%s' "$INFO" | python3 -c '
import json,sys
j=json.load(sys.stdin)
d=j["downloads"]["server:default"]
print(d["name"], d["url"], d["checksums"]["sha256"], j.get("channel","STABLE"))')
[ "$PAPER_CHANNEL" = "STABLE" ] || c_warn "Paper $MC_VERSION ist noch als $PAPER_CHANNEL markiert (frühe Version – Backups sind an)."

# Versionswechsel: vorher die Welt sichern (Minecraft kann eine aufgewertete Welt nicht zurückstufen)
OLD_VERSION="$(cat "$DIR/.mc-version" 2>/dev/null || echo "")"
if [ -z "$OLD_VERSION" ] && [ -d "$DIR/world" ]; then OLD_VERSION="1.21.11"; fi
if [ -n "$OLD_VERSION" ] && [ "$OLD_VERSION" != "$MC_VERSION" ] && [ -d "$DIR/world" ]; then
  c_step "Welt-Sicherung vor dem Wechsel $OLD_VERSION → $MC_VERSION"
  B="$DIR/backups/vor-update_${OLD_VERSION}_$(date +%Y-%m-%d_%H-%M).tar.gz"
  tar -czf "$B" -C "$DIR" $(cd "$DIR" && ls -d world* 2>/dev/null) && c_ok "Gesichert: $B"
fi
if [ -f "$DIR/paper.jar" ] && [ "$(sha256sum "$DIR/paper.jar" | cut -d" " -f1)" = "$PAPER_SHA" ]; then
  c_ok "$PAPER_NAME ist schon aktuell"
else
  curl -fsSL -A "meowl-setup" -o "$DIR/paper.jar.new" "$PAPER_URL" || fail "Download fehlgeschlagen"
  [ "$(sha256sum "$DIR/paper.jar.new" | cut -d" " -f1)" = "$PAPER_SHA" ] || { rm -f "$DIR/paper.jar.new"; fail "Prüfsumme stimmt nicht – Download beschädigt"; }
  mv -f "$DIR/paper.jar.new" "$DIR/paper.jar"
  c_ok "$PAPER_NAME installiert"
fi
echo "$MC_VERSION" > "$DIR/.mc-version"

# ---------------------------------------------------------------- Plugins (von Modrinth, Prüfsumme wird geprüft)
c_step "Plugins: Voice Chat, Vanish, ältere Versionen, OpenInv, CrystalPvPDisabler"
mkdir -p "$DIR/plugins/.verwaltet"
install_plugin() {   # $1 = Modrinth-Projekt, $2 = Anzeigename
  python3 - "$DIR/plugins" "$1" "$2" "$MC_VERSION" <<'PY'
import hashlib, json, os, sys, urllib.parse, urllib.request
plugins, slug, label, mc = sys.argv[1:5]
UA = {"User-Agent": "meowl-setup (survival server installer)"}
def get(url):
    return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=20))
def versions(game):
    q = {"loaders": json.dumps(["paper", "spigot", "bukkit"])}
    if game:
        q["game_versions"] = json.dumps([game])
    return get(f"https://api.modrinth.com/v2/project/{slug}/version?" + urllib.parse.urlencode(q))
try:
    found = versions(mc)
    exact = bool(found)
    if not found:
        found = versions(None)
except Exception as e:
    print(f"  [!] {label}: Modrinth nicht erreichbar ({e}) – bleibt wie es ist"); sys.exit(0)
if not found:
    print(f"  [!] {label}: keine passende Version gefunden – übersprungen"); sys.exit(0)
best = next((v for v in found if v.get("version_type") == "release"), found[0])
f = next((x for x in best["files"] if x.get("primary")), best["files"][0])
marker = os.path.join(plugins, ".verwaltet", slug)
old = open(marker).read().strip() if os.path.exists(marker) else ""
target = os.path.join(plugins, f["filename"])
if old == f["filename"] and os.path.exists(target):
    print(f"  {label} {best['version_number']} ist aktuell"); sys.exit(0)
data = urllib.request.urlopen(urllib.request.Request(f["url"], headers=UA), timeout=120).read()
if hashlib.sha512(data).hexdigest() != f["hashes"]["sha512"]:
    print(f"  [!] {label}: Prüfsumme falsch – nicht installiert"); sys.exit(0)
if old and old != f["filename"] and os.path.exists(os.path.join(plugins, old)):
    os.remove(os.path.join(plugins, old))
open(target, "wb").write(data)
open(marker, "w").write(f["filename"])
note = "" if exact else f"  (noch nicht offiziell für {mc} markiert – neueste Version genommen)"
print(f"  {label} {best['version_number']} installiert{note}")
PY
}
install_plugin simple-voice-chat "Simple Voice Chat"
install_plugin supervanish "SuperVanish"
install_plugin viaversion "ViaVersion"
install_plugin viabackwards "ViaBackwards"
# OPs: Inventar anderer Spieler ansehen und bearbeiten (/openinv Name, /openender Name)
install_plugin openinv "OpenInv"
# RandomControlForkFork wieder entfernt: hat den Server stark verlangsamt (TPS ~9)
RCF_MARK="$DIR/plugins/.verwaltet/randomcontrolforkfork"
if [ -f "$RCF_MARK" ]; then rm -f "$DIR/plugins/$(cat "$RCF_MARK")" "$RCF_MARK"; echo "  RandomControlForkFork entfernt"; fi
rm -f "$DIR"/plugins/RandomControl*.jar "$DIR"/plugins/randomcontrol*.jar
# Kein Crystal-/Anchor-PvP: Endkristalle und Seelenanker machen Spielern keinen Schaden mehr
install_plugin crystalpvpdisabler "CrystalPvPDisabler"
# eigenes Mini-Plugin: Endkristalle, Seelenanker und Betten zerstören keine Blöcke
base64 -d > "$DIR/plugins/MeowlRules.jar" <<'MEOWLRULES'
##MEOWLRULES_B64##
MEOWLRULES
echo "  MeowlRules installiert (Kristalle/Anker zerstören keine Blöcke)"
c_ok "Plugins bereit"

# ---------------------------------------------------------------- Konfiguration
c_step "Einstellungen"
echo "eula=true" > "$DIR/eula.txt"
RCON_FILE="$DIR/.rcon-password"
[ -s "$RCON_FILE" ] || head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 32 > "$RCON_FILE"
chmod 600 "$RCON_FILE"
RCON_PASS="$(cat "$RCON_FILE")"

# server.properties: nur unsere Werte setzen, Rest (falls vorhanden) bleibt
PROPS="$DIR/server.properties"
touch "$PROPS"
setprop() {
  if grep -q "^$1=" "$PROPS"; then
    python3 - "$PROPS" "$1" "$2" <<'PY'
import sys
p,k,v=sys.argv[1:4]
lines=open(p,encoding="utf-8").read().splitlines()
out=[(k+"="+v) if l.startswith(k+"=") else l for l in lines]
open(p,"w",encoding="utf-8").write("\n".join(out)+"\n")
PY
  else
    echo "$1=$2" >> "$PROPS"
  fi
}
FIRST_SETUP=0
grep -q "^level-name=" "$PROPS" || FIRST_SETUP=1
setprop server-port "$PORT"
setprop max-players "$MAX_PLAYERS"
setprop online-mode true
setprop white-list true
setprop enforce-whitelist true
setprop enable-rcon true
setprop rcon.port "$RCON_PORT"
setprop rcon.password "$RCON_PASS"
setprop broadcast-rcon-to-ops false
setprop view-distance "$VIEW"
setprop simulation-distance "$SIM"
setprop sync-chunk-writes false
setprop network-compression-threshold 256
if [ "$FIRST_SETUP" = 1 ]; then
  setprop level-name world
  setprop gamemode survival
  setprop difficulty normal
  setprop pvp true
  setprop spawn-protection 0
  setprop motd "$MOTD"
fi
c_ok "Max. $MAX_PLAYERS Spieler, Whitelist an, Sichtweite $VIEW"

# ---------------------------------------------------------------- Leistung (Paper/Spigot/Bukkit)
# Übliche Einstellungen großer Server: Mobs weit weg werden seltener berechnet, Items/XP verschmelzen,
# schnellere Redstone-Logik, weniger Kollisionen. Die Dateien entstehen beim ersten Start; nur vorhandene Werte werden gesetzt.
tune_yaml() {   # $1 = Datei, $2 = JSON mit "a.b.c": Wert
  [ -f "$1" ] || return 0
  python3 - "$1" "$2" <<'PY' || true
import json, sys, yaml
path, changes = sys.argv[1], json.loads(sys.argv[2])
data = yaml.safe_load(open(path, encoding="utf-8")) or {}
done = 0
for key, value in changes.items():
    node, parts = data, key.split(".")
    for p in parts[:-1]:
        node = node.get(p) if isinstance(node, dict) else None
        if node is None:
            break
    if isinstance(node, dict) and parts[-1] in node:
        node[parts[-1]] = value
        done += 1
yaml.safe_dump(data, open(path, "w", encoding="utf-8"), sort_keys=False, allow_unicode=True)
print(f"  {path.split('/')[-1]}: {done} Werte optimiert")
PY
}
tune_yaml "$DIR/spigot.yml" '{
  "world-settings.default.entity-activation-range.animals": 16,
  "world-settings.default.entity-activation-range.monsters": 24,
  "world-settings.default.entity-activation-range.raiders": 48,
  "world-settings.default.entity-activation-range.misc": 8,
  "world-settings.default.entity-activation-range.water": 8,
  "world-settings.default.entity-activation-range.villagers": 16,
  "world-settings.default.entity-activation-range.tick-inactive-villagers": false,
  "world-settings.default.merge-radius.item": 3.5,
  "world-settings.default.merge-radius.exp": 4.0,
  "world-settings.default.mob-spawn-range": 6,
  "world-settings.default.arrow-despawn-rate": 300
}'
tune_yaml "$DIR/bukkit.yml" '{
  "spawn-limits.monsters": 50,
  "spawn-limits.animals": 10,
  "spawn-limits.water-animals": 4,
  "spawn-limits.water-ambient": 10,
  "spawn-limits.ambient": 5,
  "ticks-per.monster-spawns": 2,
  "ticks-per.animal-spawns": 400
}'
tune_yaml "$DIR/config/paper-world-defaults.yml" '{
  "chunks.max-auto-save-chunks-per-tick": 8,
  "chunks.prevent-moving-into-unloaded-chunks": true,
  "collisions.max-entity-collisions": 2,
  "environment.optimize-explosions": true,
  "hopper.disable-move-event": true,
  "hopper.ignore-occluding-blocks": true,
  "misc.redstone-implementation": "ALTERNATE_CURRENT",
  "tick-rates.grass-spread": 4,
  "tick-rates.container-update": 3,
  "tick-rates.mob-spawner": 2,
  "entities.armor-stands.tick": false,
  "entities.spawning.per-player-mob-spawns": true
}'
c_ok "Leistungs-Einstellungen gesetzt ($(nproc) CPU-Kerne)"

# ---------------------------------------------------------------- Whitelist + OP
if [ -n "$PLAYERS" ]; then
  c_step "Whitelist"
  python3 - "$DIR" "$PLAYERS" <<'PY'
import json, os, re, sys, urllib.request
d, names = sys.argv[1], [n.strip() for n in sys.argv[2].split(",") if n.strip()]
def load(f):
    try:
        return json.load(open(os.path.join(d, f), encoding="utf-8"))
    except Exception:
        return []
wl, ops = load("whitelist.json"), load("ops.json")
for i, n in enumerate(names):
    if not re.fullmatch(r"[A-Za-z0-9_]{3,16}", n):
        print(f"  übersprungen (kein gültiger Name): {n}"); continue
    try:
        req = urllib.request.Request("https://api.mojang.com/users/profiles/minecraft/" + n, headers={"User-Agent": "meowl-setup"})
        prof = json.load(urllib.request.urlopen(req, timeout=10))
    except Exception:
        print(f"  nicht gefunden bei Mojang: {n}"); continue
    u = prof["id"]; uuid = f"{u[0:8]}-{u[8:12]}-{u[12:16]}-{u[16:20]}-{u[20:]}"
    if not any(e.get("uuid") == uuid for e in wl):
        wl.append({"uuid": uuid, "name": prof["name"]})
    print(f"  Whitelist: {prof['name']}")
    if i == 0 and not any(e.get("uuid") == uuid for e in ops):
        ops.append({"uuid": uuid, "name": prof["name"], "level": 4, "bypassesPlayerLimit": True})
        print(f"  Admin (OP): {prof['name']}")
json.dump(wl, open(os.path.join(d, "whitelist.json"), "w", encoding="utf-8"), indent=2)
json.dump(ops, open(os.path.join(d, "ops.json"), "w", encoding="utf-8"), indent=2)
PY
fi

# ---------------------------------------------------------------- Befehl "mc" (Konsole über RCON)
cat > /usr/local/bin/mc <<'PY'
#!/usr/bin/env python3
"""mc <befehl>  –  schickt einen Befehl an den Survival-Server, z.B.:  mc whitelist add Name"""
import socket, struct, sys
props = dict(l.split("=", 1) for l in open("/opt/minecraft/server.properties", encoding="utf-8").read().splitlines() if "=" in l and not l.startswith("#"))
port, pw = int(props.get("rcon.port", "25575")), props.get("rcon.password", "")
cmd = " ".join(sys.argv[1:]) or "list"
def pkt(i, t, body):
    b = body.encode("utf-8") + b"\x00\x00"
    return struct.pack("<iii", len(b) + 8, i, t) + b
def recv(s):
    n = struct.unpack("<i", s.recv(4))[0]
    data = b""
    while len(data) < n:
        data += s.recv(n - len(data))
    i, t = struct.unpack("<ii", data[:8])
    return i, data[8:-2].decode("utf-8", "replace")
try:
    s = socket.create_connection(("127.0.0.1", port), timeout=5)
except OSError:
    print("Survival-Server läuft gerade nicht (oder startet noch)."); sys.exit(1)
s.sendall(pkt(1, 3, pw)); i, _ = recv(s)
if i == -1:
    print("RCON-Passwort falsch"); sys.exit(1)
s.sendall(pkt(2, 2, cmd)); _, out = recv(s)
print(out if out else "OK")
PY
chmod 755 /usr/local/bin/mc
c_ok "Befehl 'mc' eingerichtet (z.B. mc whitelist add Name)"

# ---------------------------------------------------------------- Dienst
c_step "Dienst '$SERVICE' (startet automatisch)"
FLAGS="-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=15 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true"
cat > /etc/systemd/system/$SERVICE.service <<EOF
[Unit]
Description=Minecraft Survival-Server (Paper $MC_VERSION)
After=network-online.target
Wants=network-online.target

[Service]
User=minecraft
WorkingDirectory=$DIR
ExecStart=$JAVA_BIN -Xms${XMX}M -Xmx${XMX}M $FLAGS $EXTRA_FLAGS -jar $DIR/paper.jar --nogui
Restart=on-failure
RestartSec=10
TimeoutStopSec=90
SuccessExitStatus=0 1 143
Nice=5

[Install]
WantedBy=multi-user.target
EOF
chown -R minecraft:minecraft "$DIR"
chmod 600 "$PROPS" "$RCON_FILE"
systemctl daemon-reload
systemctl enable "$SERVICE" >/dev/null 2>&1
systemctl restart "$SERVICE"
c_ok "Dienst gestartet"

# ---------------------------------------------------------------- Backups
cat > /usr/local/bin/mc-backup <<'EOF'
#!/usr/bin/env bash
# Tägliches Welt-Backup (die letzten 7 bleiben)
set -u
DIR=/opt/minecraft
STAMP=$(date +%Y-%m-%d_%H-%M)
mc save-off >/dev/null 2>&1; mc save-all flush >/dev/null 2>&1; sleep 5
tar -czf "$DIR/backups/welt_$STAMP.tar.gz" -C "$DIR" $(cd "$DIR" && ls -d world* 2>/dev/null) 2>/dev/null
mc save-on >/dev/null 2>&1
ls -1t "$DIR"/backups/welt_*.tar.gz 2>/dev/null | tail -n +8 | xargs -r rm -f
chown -R minecraft:minecraft "$DIR/backups"
EOF
chmod 755 /usr/local/bin/mc-backup
echo "30 4 * * * root /usr/local/bin/mc-backup" > /etc/cron.d/minecraft-backup
c_ok "Backup jede Nacht um 4:30 (die letzten 7 bleiben in $DIR/backups)"

# ---------------------------------------------------------------- Firewall
if command -v ufw >/dev/null && [[ "$(ufw status 2>/dev/null || true)" == *"Status: active"* ]]; then
  ufw allow OpenSSH >/dev/null || true
  ufw allow $PORT/tcp >/dev/null
  ufw allow $VOICE_PORT/udp >/dev/null
  c_ok "Firewall: Port $PORT (Spiel) und $VOICE_PORT/udp (Voice Chat) freigegeben (RCON bleibt zu)"
fi

# ---------------------------------------------------------------- Web-Panel
c_step "Web-Panel"
mkdir -p /opt/minecraft-panel
cat > /opt/minecraft-panel/panel.py <<'PANELPY'
##PANEL_PY##
PANELPY
PANEL_PASS_FILE=/opt/minecraft-panel/.password
[ -s "$PANEL_PASS_FILE" ] || { head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 16 > "$PANEL_PASS_FILE"; }
chown -R root:root /opt/minecraft-panel
chmod 700 /opt/minecraft-panel
chmod 600 "$PANEL_PASS_FILE"
cat > /etc/systemd/system/minecraft-panel.service <<EOF
[Unit]
Description=Web-Panel für den Survival-Server
After=network-online.target

[Service]
ExecStart=/usr/bin/python3 /opt/minecraft-panel/panel.py
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable minecraft-panel >/dev/null 2>&1
systemctl restart minecraft-panel
IP="$(curl -fsS -4 --max-time 5 https://api.ipify.org 2>/dev/null || hostname -I | awk '{print $1}')"
PANEL_HOST="panel.${IP//./-}.sslip.io"
# HTTPS über Caddy (läuft schon, wenn der Meowl Server installiert ist)
if ! command -v caddy >/dev/null; then
  apt-get install -y -qq caddy >/dev/null 2>&1 || c_warn "Caddy ließ sich nicht installieren – Panel nur auf dem VPS selbst erreichbar (Port 8090)"
fi
PANEL_OK=0
if command -v caddy >/dev/null; then
  mkdir -p /etc/caddy
  cat > /etc/caddy/minecraft-panel.caddy <<EOF
$PANEL_HOST {
  encode gzip
  reverse_proxy 127.0.0.1:8090
}
EOF
  CF=/etc/caddy/Caddyfile
  if [ ! -s "$CF" ] || grep -q "/usr/share/caddy" "$CF"; then
    printf 'import /etc/caddy/minecraft-panel.caddy\n' > "$CF"
  elif ! grep -q "minecraft-panel.caddy" "$CF"; then
    printf '\nimport /etc/caddy/minecraft-panel.caddy\n' >> "$CF"
  fi
  if caddy validate --config "$CF" --adapter caddyfile >/dev/null 2>&1; then
    systemctl enable caddy >/dev/null 2>&1 || true
    systemctl reload caddy 2>/dev/null || systemctl restart caddy || true
    PANEL_OK=1
    if command -v ufw >/dev/null && [[ "$(ufw status 2>/dev/null || true)" == *"Status: active"* ]]; then
      ufw allow 80/tcp >/dev/null; ufw allow 443/tcp >/dev/null
    fi
    c_ok "Panel: https://$PANEL_HOST"
  else
    c_warn "Caddy-Konfiguration ungültig – Panel nicht von außen erreichbar"
  fi
fi

# ---------------------------------------------------------------- Warten bis online
c_step "Server startet (beim ersten Mal wird die Welt erzeugt, das dauert 1–3 Minuten)"
for i in $(seq 1 90); do
  LOG="$(journalctl -u "$SERVICE" --since "-5min" --no-pager 2>/dev/null || true)"
  if [[ "$LOG" == *"Done ("* ]]; then
    c_ok "Server ist online"
    break
  fi
  if ! systemctl is-active --quiet "$SERVICE"; then
    journalctl -u "$SERVICE" -n 30 --no-pager || true
    fail "Server ist abgestürzt – die Meldungen oben sagen warum"
  fi
  sleep 3
done

# Meowl Server darf nicht betroffen sein
if [ -f /etc/systemd/system/meowl-server.service ]; then
  systemctl is-active --quiet meowl-server && c_ok "Meowl Server läuft weiterhin normal" || c_warn "Meowl Server läuft gerade nicht"
fi

cat <<EOF

=====================================================================
  Survival-Server läuft!   (Paper $MC_VERSION, max. $MAX_PLAYERS Spieler)
  Beitreten mit Minecraft $MC_VERSION – oder älter (z.B. 1.21.11) dank ViaBackwards.

  Adresse in Minecraft:   $IP
  (oder: ${IP//./-}.sslip.io)

  Web-Panel:   https://$PANEL_HOST
  Passwort:    $(cat /opt/minecraft-panel/.password)
  (Status, Konsole, Whitelist, Admins, Neustart, Backups – im Browser)

  Nur Spieler auf der Whitelist kommen rein. Weitere hinzufügen:
     ingame als Admin:   /whitelist add Name
     oder auf dem VPS:   mc whitelist add Name

  Nützlich auf dem VPS:
     mc list                       wer ist online
     systemctl restart minecraft   Server neu starten
     journalctl -u minecraft -f    Server-Log ansehen (Strg+C beendet)
  Voice Chat: jeder braucht die Mod "Simple Voice Chat" im Client
     (Modrinth, passend zur eigenen Minecraft-Version). Taste V = Einstellungen.
     Server-Port $VOICE_PORT/UDP muss offen sein (auch in der Firewall des Anbieters!).
  Vanish (nur Admins):  /vanish   bzw. /sv on | /sv off
  Inventar ansehen/bearbeiten (nur Admins):  /openinv Name   Endertruhe: /openender Name
  Plugins: .jar-Dateien nach $DIR/plugins, dann neu starten.
=====================================================================
EOF
