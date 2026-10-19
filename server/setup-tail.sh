MEOWL_JAR_EOF
mv -f "$DIR/meowl-server.jar.new" "$DIR/meowl-server.jar"
# Client-Updates veröffentlichen: mitgeschickte meowl-client-*.jar in den Update-Ordner legen
mkdir -p "$DIR/data/updates"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PUBLISHED=""
PUBLISHED_MC=""
# Versionen für andere Minecraft-Versionen (meowl-client-mc26.3-X.jar) in eigene Unterordner
for f in "$SCRIPT_DIR"/meowl-client-mc*-*.jar "$SCRIPT_DIR"/meowl-client-mc*-*.txt; do
  [ -f "$f" ] || continue
  b="$(basename "$f")"
  MCV="$(printf '%s' "$b" | sed -n 's/^meowl-client-mc\([0-9][0-9.]*\)-.*/\1/p')"
  [ -n "$MCV" ] || continue
  mkdir -p "$DIR/data/updates/mc$MCV"
  mv -f "$f" "$DIR/data/updates/mc$MCV/$(printf '%s' "$b" | sed "s/^meowl-client-mc$MCV-/meowl-client-/")"
  case "$b" in *.jar) PUBLISHED_MC="$PUBLISHED_MC $b" ;; esac
done
for d in "$DIR/data/updates"/mc*/; do
  [ -d "$d" ] || continue
  ls -1 "$d"meowl-client-*.jar 2>/dev/null | sort -V | head -n -3 | xargs -r rm -f || true
  ls -1 "$d"meowl-client-*.txt 2>/dev/null | sort -V | head -n -15 | xargs -r rm -f || true
done
for f in "$SCRIPT_DIR"/meowl-client-*.jar; do
  [ -f "$f" ] || continue
  mv -f "$f" "$DIR/data/updates/"
  PUBLISHED="$PUBLISHED $(basename "$f")"
done
# Changelogs (meowl-client-X.txt) mitnehmen – werden beim Update im Client angezeigt
for f in "$SCRIPT_DIR"/meowl-client-*.txt; do
  [ -f "$f" ] || continue
  mv -f "$f" "$DIR/data/updates/"
done
# Changelogs: die 15 neuesten behalten (Verlauf im Client unter „Changelog“)
ls -1 "$DIR/data/updates"/meowl-client-*.txt 2>/dev/null | sort -V | head -n -15 | xargs -r rm -f || true
# nur die 3 neuesten Client-Versionen behalten
ls -1 "$DIR/data/updates"/meowl-client-*.jar 2>/dev/null | sort -V | head -n -3 | xargs -r rm -f || true
# YouTube-Cookies (optional, gegen "YouTube blockiert den Server"): nur für den Dienst lesbar
# YouTube erneuert die Cookies laufend (yt-dlp speichert die frische Fassung) – nur eine wirklich neue Datei übernehmen
if [ -f "$SCRIPT_DIR/youtube-cookies.txt" ]; then
  NEWSUM="$(sha256sum "$SCRIPT_DIR/youtube-cookies.txt" | cut -d" " -f1)"
  OLDSUM="$(cat "$DIR/data/youtube-cookies.sha256" 2>/dev/null || true)"
  if [ "$NEWSUM" != "$OLDSUM" ] || [ ! -f "$DIR/data/youtube-cookies.txt" ]; then
    mv -f "$SCRIPT_DIR/youtube-cookies.txt" "$DIR/data/youtube-cookies.txt"
    printf "%s\n" "$NEWSUM" > "$DIR/data/youtube-cookies.sha256"
    c_ok "Neue YouTube-Cookies übernommen"
  else
    rm -f "$SCRIPT_DIR/youtube-cookies.txt"
    c_ok "YouTube-Cookies unverändert – Server behält seine aufgefrischte Fassung"
  fi
fi
chown -R meowl:meowl "$DIR"
[ -f "$DIR/data/youtube-cookies.txt" ] && chmod 600 "$DIR/data/youtube-cookies.txt"
c_ok "Dateien in $DIR"
[ -n "$PUBLISHED" ] && c_ok "Client-Update veröffentlicht:$PUBLISHED (Spieler bekommen es automatisch angeboten)"
[ -n "$PUBLISHED_MC" ] && c_ok "Client-Update für andere Minecraft-Versionen:$PUBLISHED_MC"

# Ports 80/443 frei für Caddy? (sonst direkt über Port $PORT ohne HTTPS)
USE_CADDY=1
if ss -ltnpH 2>/dev/null | grep -E ':(80|443)\s' | grep -vq caddy; then
  c_warn "Port 80/443 wird schon von einem anderen Programm benutzt (z.B. nginx/apache)."
  c_warn "Ich richte den Meowl Server deshalb direkt auf Port $PORT ein (ohne HTTPS)."
  USE_CADDY=0
fi
BIND=127.0.0.1
[ "$USE_CADDY" = 1 ] || BIND=0.0.0.0

cat > /etc/systemd/system/meowl-server.service <<UNIT
[Unit]
Description=Meowl Server (Meowl Client: Pets, Bilder, Facecam, Duelle)
After=network-online.target
Wants=network-online.target

[Service]
User=meowl
Group=meowl
WorkingDirectory=$DIR
ExecStart=$JAVA_BIN -Xmx256m -jar $DIR/meowl-server.jar --port $PORT --bind $BIND --data $DIR/data --trust-proxy
Restart=always
RestartSec=5
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=$DIR/data

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable meowl-server >/dev/null 2>&1
systemctl restart meowl-server
for i in $(seq 1 30); do
  curl -fsS "http://127.0.0.1:$PORT/status" >/dev/null 2>&1 && break
  sleep 1
done
curl -fsS "http://127.0.0.1:$PORT/status" >/dev/null 2>&1 || { journalctl -u meowl-server -n 30 --no-pager; fail "Meowl Server startet nicht (Log oben)"; }
c_ok "Dienst meowl-server läuft"

# ---------------------------------------------------------------- Firewall
if command -v ufw >/dev/null && ufw status | grep -q "Status: active"; then
  c_step "Firewall (ufw)"
  ufw allow OpenSSH >/dev/null || true
  if [ "$USE_CADDY" = 1 ]; then ufw allow 80/tcp >/dev/null; ufw allow 443/tcp >/dev/null; else ufw allow $PORT/tcp >/dev/null; fi
  c_ok "Ports freigegeben"
fi

# ---------------------------------------------------------------- Adresse + HTTPS
PUBLIC_IP="$(curl -4 -fsS --max-time 10 https://api.ipify.org || curl -4 -fsS --max-time 10 https://ifconfig.me || true)"
if [ "$USE_CADDY" = 0 ]; then
  [ -n "$PUBLIC_IP" ] || PUBLIC_IP="<IP-deines-Servers>"
  URL="http://$PUBLIC_IP:$PORT"
else
  if [ -n "$DOMAIN_ARG" ]; then
    DOMAIN="$DOMAIN_ARG"
  elif [ -s "$DIR/domain.txt" ]; then
    DOMAIN="$(cat "$DIR/domain.txt")"
  else
    [ -n "$PUBLIC_IP" ] || fail "Öffentliche IP nicht gefunden. Starte mit eigener Domain: bash $0 meowl.deine-domain.de"
    DOMAIN="${PUBLIC_IP//./-}.sslip.io"   # kostenlose Adresse, die automatisch auf deine IP zeigt
  fi
  echo "$DOMAIN" > "$DIR/domain.txt"

  c_step "HTTPS mit Caddy für $DOMAIN"
  if ! command -v caddy >/dev/null; then
    # zuerst aus den normalen Debian-Paketen, nur notfalls von Cloudsmith
    if ! apt-get install -y -qq caddy >/dev/null 2>&1; then
      apt-get install -y -qq debian-keyring debian-archive-keyring apt-transport-https >/dev/null 2>&1 || true
      curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg || true
      curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' > /etc/apt/sources.list.d/caddy-stable.list || true
      apt-get update -qq || true
      apt-get install -y -qq caddy >/dev/null || fail "Caddy konnte nicht installiert werden"
    fi
  fi
  cat > /etc/caddy/meowl.caddy <<CADDY
# Meowl Server (automatisch erzeugt von meowl-server-setup.sh)
$DOMAIN {
	encode gzip
	reverse_proxy 127.0.0.1:$PORT {
		flush_interval -1
	}
}
CADDY
  CF=/etc/caddy/Caddyfile
  if [ ! -s "$CF" ] || grep -q "/usr/share/caddy" "$CF"; then
    # Standard-Datei von Caddy (Testseite) -> ersetzen
    printf 'import /etc/caddy/meowl.caddy\n' > "$CF"
  elif ! grep -q "meowl.caddy" "$CF"; then
    printf '\nimport /etc/caddy/meowl.caddy\n' >> "$CF"
  fi
  caddy validate --config "$CF" --adapter caddyfile >/dev/null 2>&1 || fail "Caddy-Konfiguration ungültig (siehe $CF)"
  systemctl enable caddy >/dev/null 2>&1
  systemctl restart caddy
  URL="https://$DOMAIN"
  echo "Warte auf das HTTPS-Zertifikat (bis zu 90 s) ..."
  OK=0
  for i in $(seq 1 45); do
    if curl -fsS --max-time 5 "$URL/status" >/dev/null 2>&1; then OK=1; break; fi
    sleep 2
  done
  if [ "$OK" = 1 ]; then
    c_ok "HTTPS funktioniert"
  else
    c_warn "HTTPS antwortet noch nicht. Meistens blockiert die Firewall beim Anbieter Port 80/443"
    c_warn "(z.B. Hetzner/Oracle 'Cloud Firewall' / 'Security List'): dort TCP 80 und 443 eingehend erlauben."
    [ -n "$DOMAIN_ARG" ] && c_warn "Bei eigener Domain: zeigt der A-Eintrag von $DOMAIN auf $PUBLIC_IP?"
    c_warn "Danach einfach nochmal ausführen. Log: journalctl -u caddy -n 50"
  fi
fi
echo "$URL" > "$DIR/adresse.txt"

# ---------------------------------------------------------------- Client-Update prüfen
OFFERED="$(curl -fsS "http://127.0.0.1:$PORT/update/latest" 2>/dev/null | sed -n 's/.*"version":"\([^"]*\)".*/\1/p')"
if [ -n "$PUBLISHED" ]; then
  NEWEST="$(printf '%s\n' $PUBLISHED | sed 's/^meowl-client-//; s/\.jar$//' | sort -V | tail -n 1)"
  if [ "$OFFERED" = "$NEWEST" ]; then
    c_ok "Client-Update aktiv: Der Server bietet jetzt Version $OFFERED an."
  else
    c_warn "Hochgeladen: $NEWEST – der Server bietet aber $OFFERED an. Liegt eine noch neuere Version im Ordner?"
  fi
else
  c_warn "Es lag keine meowl-client-X.jar neben der .bat – Client-Version bleibt ${OFFERED:-unbekannt}."
fi
for d in "$DIR/data/updates"/mc*/; do
  [ -d "$d" ] || continue
  MCV="$(basename "$d" | sed 's/^mc//')"
  OFF_MC="$(curl -fsS "http://127.0.0.1:$PORT/update/latest?mc=$MCV" 2>/dev/null | sed -n 's/.*"version":"\([^"]*\)".*/\1/p' || true)"
  [ -n "$OFF_MC" ] && c_ok "Minecraft $MCV: Server bietet Client-Version $OFF_MC an." || true
done

cat <<DONE

=====================================================================
  Meowl Server $MEOWL_VERSION ist eingerichtet.

  ADRESSE FÜR DEN MOD:   $URL
  CLIENT-VERSION:        ${OFFERED:-keine veröffentlicht}  (Spieler bekommen sie automatisch)

  Im Spiel: Mods -> Meowl Server -> Adresse einfügen -> Verbinden.
  Alle Freunde tragen dieselbe Adresse ein. Sie ändert sich nie.

  Nützlich:
    Status:     systemctl status meowl-server
    Log:        journalctl -u meowl-server -f
    Neustart:   systemctl restart meowl-server
    Update:     neues meowl-server-setup.sh hochladen und nochmal ausführen
=====================================================================
DONE
