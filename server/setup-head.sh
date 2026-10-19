#!/usr/bin/env bash
# =====================================================================
#  Meowl Server – Installation / Update auf einem Linux-VPS
#  (Ubuntu 22.04+/24.04, Debian 12+)
#
#  Benutzung (als root oder mit sudo):
#     bash meowl-server-setup.sh                 -> Adresse automatisch (https://<IP>.sslip.io)
#     bash meowl-server-setup.sh meowl.deine.de  -> eigene Domain (A-Eintrag muss auf den VPS zeigen)
#
#  Was passiert:
#   - Java 21 wird installiert (falls nötig)
#   - Der Meowl Server läuft als Dienst "meowl-server" (startet automatisch, auch nach Neustart/Absturz)
#   - Caddy davor sorgt für HTTPS mit echtem Zertifikat (Let's Encrypt), automatisch erneuert
#   - Nochmal ausführen = Update auf die Version in diesem Skript (Daten bleiben erhalten)
#   - Liegt eine meowl-client-X.jar daneben, wird sie als Client-Update veröffentlicht
# =====================================================================
set -euo pipefail

MEOWL_VERSION="1.6.1"
DIR=/opt/meowl-server
PORT=8787
DOMAIN_ARG="${1:-}"

c_ok()   { printf '\033[1;32m[OK]\033[0m %s\n' "$*"; }
c_step() { printf '\n\033[1;35m==> %s\033[0m\n' "$*"; }
c_warn() { printf '\033[1;33m[!]\033[0m %s\n' "$*"; }
fail()   { printf '\033[1;31m[FEHLER]\033[0m %s\n' "$*"; exit 1; }

[ "$(id -u)" -eq 0 ] || fail "Bitte als root starten:  sudo bash $0 $DOMAIN_ARG"
[ -r /etc/os-release ] && . /etc/os-release || fail "Unbekanntes Betriebssystem"
case "${ID:-}${ID_LIKE:-}" in
  *ubuntu*|*debian*) ;;
  *) fail "Dieses Skript ist für Ubuntu/Debian. Gefunden: ${PRETTY_NAME:-?}" ;;
esac
echo "Meowl Server $MEOWL_VERSION  –  System: ${PRETTY_NAME:-?}"

if [ -n "$DOMAIN_ARG" ] && ! [[ "$DOMAIN_ARG" =~ ^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$ && "$DOMAIN_ARG" == *.* ]]; then
  fail "Ungültige Domain: $DOMAIN_ARG"
fi

export DEBIAN_FRONTEND=noninteractive

# ---------------------------------------------------------------- Pakete
c_step "Pakete aktualisieren"
# Fremde Paketquellen können wegfallen (z.B. Caddy bei Cloudsmith: 402) – dann abschalten statt abzubrechen
if ! apt-get update -qq 2>/tmp/meowl-apt.err; then
  # alle Server, die beim Laden einen Fehler gemeldet haben, und die Quell-Dateien, die sie benutzen
  for host in $(grep -oE 'https?://[^/ ]+' /tmp/meowl-apt.err | sed -E 's#https?://##' | sort -u || true); do
    for f in /etc/apt/sources.list.d/*.list /etc/apt/sources.list.d/*.sources; do
      [ -e "$f" ] || continue
      if grep -v '^[[:space:]]*#' "$f" | grep -q "$host"; then
        mv "$f" "$f.disabled"
        c_warn "Paketquelle $(basename "$f") ($host) antwortet nicht mehr – abgeschaltet"
      fi
    done
  done
  apt-get update -qq || fail "Paketlisten konnten nicht geladen werden"
fi
apt-get install -y -qq curl ca-certificates gnupg iproute2 >/dev/null
c_ok "Grundpakete da"

c_step "Java 21"
# Java 21+ prüfen – ohne grep -q in einer Pipe (mit pipefail meldet das sonst fälschlich "fehlt")
java_ok() {
  [ -x "$1" ] || return 1
  local v; v="$("$1" -version 2>&1 | sed -n 1p || true)"
  [[ "$v" =~ version\ \"([0-9]+) ]] || return 1
  [ "${BASH_REMATCH[1]}" -ge 21 ]
}
# apt-Paket nur nehmen, wenn es wirklich installierbar ist (apt-cache show kennt auch Pakete ohne Kandidat)
apt_has() { local c; c="$(apt-cache policy "$1" 2>/dev/null | sed -n 's/^ *Candidate: *//p' || true)"; [ -n "$c" ] && [ "$c" != "(none)" ]; }
find_java() {
  JAVA_BIN=""
  local cand
  for cand in "$DIR/java/bin/java" "$(command -v java 2>/dev/null || true)" /usr/lib/jvm/*/bin/java; do
    if java_ok "$cand"; then JAVA_BIN="$(readlink -f "$cand")"; return 0; fi
  done
  return 1
}
if find_java; then
  c_ok "Java ist schon installiert ($JAVA_BIN)"
else
  for pkg in openjdk-21-jre-headless openjdk-25-jre-headless; do
    if apt_has "$pkg"; then
      apt-get install -y -qq "$pkg" >/dev/null || true
      if find_java; then break; fi
    fi
  done
  if [ -z "$JAVA_BIN" ]; then
    # Kein passendes Paket in dieser Linux-Version: Temurin 21 direkt von Adoptium (ohne apt)
    case "$(uname -m)" in
      x86_64|amd64) ARCH=x64 ;;
      aarch64|arm64) ARCH=aarch64 ;;
      *) ARCH="" ;;
    esac
    if [ -n "$ARCH" ]; then
      TMPJ="$(mktemp)"
      if curl -fsSL -o "$TMPJ" "https://api.adoptium.net/v3/binary/latest/21/ga/linux/$ARCH/jre/hotspot/normal/eclipse"; then
        mkdir -p "$DIR"
        rm -rf "$DIR/java.new" && mkdir -p "$DIR/java.new"
        if tar -xzf "$TMPJ" -C "$DIR/java.new" --strip-components=1; then
          rm -rf "$DIR/java" && mv "$DIR/java.new" "$DIR/java"
        fi
      fi
      rm -f "$TMPJ"
    fi
    find_java || true
  fi
  [ -n "$JAVA_BIN" ] || fail "Java 21 konnte nicht installiert werden (api.adoptium.net nicht erreichbar?)"
  c_ok "Java installiert ($JAVA_BIN)"
fi

# ---------------------------------------------------------------- Video-Werkzeuge (gemeinsames Schauen)
c_step "ffmpeg und yt-dlp für gemeinsame Videos"
if ! command -v ffmpeg >/dev/null 2>&1; then
  apt-get install -y -qq ffmpeg >/dev/null 2>&1 || c_warn "ffmpeg konnte nicht installiert werden – gemeinsame Videos gehen dann nicht"
fi
YTDLP_ASSET="yt-dlp_linux"
case "$(uname -m)" in
  aarch64|arm64) YTDLP_ASSET="yt-dlp_linux_aarch64" ;;
esac
# immer die neueste Version holen (YouTube ändert sich oft)
if curl -fsSL --max-time 120 -o /usr/local/bin/yt-dlp.new "https://github.com/yt-dlp/yt-dlp/releases/latest/download/$YTDLP_ASSET"; then
  chmod 755 /usr/local/bin/yt-dlp.new && mv -f /usr/local/bin/yt-dlp.new /usr/local/bin/yt-dlp
  c_ok "yt-dlp aktuell"
else
  rm -f /usr/local/bin/yt-dlp.new
  c_warn "yt-dlp konnte nicht geladen werden – gemeinsame Videos gehen dann nicht"
fi
# YouTube verlangt inzwischen eine JavaScript-Laufzeit für yt-dlp (sonst "Video nicht abrufbar")
DENO_ASSET="deno-x86_64-unknown-linux-gnu.zip"
case "$(uname -m)" in
  aarch64|arm64) DENO_ASSET="deno-aarch64-unknown-linux-gnu.zip" ;;
esac
command -v unzip >/dev/null 2>&1 || apt-get install -y -qq unzip >/dev/null 2>&1 || true
TMPD="$(mktemp -d)"
if curl -fsSL --max-time 180 -o "$TMPD/deno.zip" "https://github.com/denoland/deno/releases/latest/download/$DENO_ASSET" \
   && unzip -oq "$TMPD/deno.zip" -d "$TMPD" && [ -f "$TMPD/deno" ]; then
  install -m 755 "$TMPD/deno" /usr/local/bin/deno
  c_ok "Deno (für YouTube) aktuell"
else
  c_warn "Deno konnte nicht geladen werden – YouTube-Videos gehen evtl. nicht"
fi
rm -rf "$TMPD"

# ---------------------------------------------------------------- Meowl Server
c_step "Meowl Server $MEOWL_VERSION einrichten"
id meowl >/dev/null 2>&1 || useradd --system --home-dir "$DIR" --shell /usr/sbin/nologin meowl
mkdir -p "$DIR/data"
base64 -d > "$DIR/meowl-server.jar.new" <<'MEOWL_JAR_EOF'
