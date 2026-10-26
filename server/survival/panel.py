#!/usr/bin/env python3
"""Web-Panel für den Survival-Server: Status, Konsole, Spieler, Whitelist, Backups.

Läuft nur auf 127.0.0.1 und wird von Caddy per HTTPS nach außen gegeben. Login mit dem Passwort aus
/opt/minecraft-panel/.password (Besitzer). Der Besitzer kann zeitlich begrenzte Gast-Zugänge mit eigenem
Passwort und eingeschränkten Rechten anlegen (Zuschauer, Moderator, Admin). Ohne Zusatzpakete (nur Python-Standardbibliothek).
"""
import hashlib
import hmac
import json
import os
import re
import secrets
import socket
import struct
import subprocess
import threading
import time
from http.cookies import SimpleCookie
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DIR = "/opt/minecraft"
SERVICE = "minecraft"
PORT = 8090
PASS_FILE = "/opt/minecraft-panel/.password"
TEMP_FILE = "/opt/minecraft-panel/temp-logins.json"
# Rang: 0 Zuschauer, 1 Moderator, 2 Admin, 3 Besitzer (Hauptpasswort)
RANKS = {"zuschauer": 0, "moderator": 1, "admin": 2, "besitzer": 3}
ROLE_LABEL = {"zuschauer": "Zuschauer", "moderator": "Moderator", "admin": "Admin", "besitzer": "Besitzer"}
# Befehle, die Moderatoren in der Konsole nicht dürfen
MOD_BLOCKED = {"op", "deop", "stop", "restart", "reload", "save-off", "lp", "luckperms", "pl", "plugman"}
SESSIONS = {}
FAILS = {}
LOCK = threading.Lock()
NAME = re.compile(r"^[A-Za-z0-9_]{3,16}$")
BACKUP = re.compile(r"^[A-Za-z0-9_.\-]+\.tar\.gz$")
COLOR = re.compile(r"§.")


def password():
    try:
        return open(PASS_FILE, encoding="utf-8").read().strip()
    except OSError:
        return ""


def load_temp():
    """Gültige Gast-Zugänge (abgelaufene fallen weg)."""
    try:
        data = json.load(open(TEMP_FILE, encoding="utf-8"))
    except Exception:  # noqa: BLE001
        return []
    now = time.time()
    return [t for t in data if isinstance(t, dict) and t.get("until", 0) > now]


def save_temp(items):
    tmp = TEMP_FILE + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(items, f, indent=2)
    os.chmod(tmp, 0o600)
    os.replace(tmp, TEMP_FILE)


def hash_pw(pw, salt):
    return hashlib.pbkdf2_hmac("sha256", pw.encode("utf-8"), bytes.fromhex(salt), 120000).hex()


def props():
    out = {}
    try:
        for line in open(os.path.join(DIR, "server.properties"), encoding="utf-8").read().splitlines():
            if "=" in line and not line.startswith("#"):
                k, v = line.split("=", 1)
                out[k] = v
    except OSError:
        pass
    return out


def rcon(cmd):
    """Befehl an den Server schicken; None, wenn er nicht läuft."""
    p = props()
    try:
        s = socket.create_connection(("127.0.0.1", int(p.get("rcon.port", "25575"))), timeout=4)
    except OSError:
        return None

    def pkt(i, t, body):
        b = body.encode("utf-8") + b"\x00\x00"
        return struct.pack("<iii", len(b) + 8, i, t) + b

    def recv():
        n = struct.unpack("<i", s.recv(4))[0]
        data = b""
        while len(data) < n:
            chunk = s.recv(n - len(data))
            if not chunk:
                break
            data += chunk
        i, _ = struct.unpack("<ii", data[:8])
        return i, data[8:-2].decode("utf-8", "replace")

    try:
        s.sendall(pkt(1, 3, p.get("rcon.password", "")))
        i, _ = recv()
        if i == -1:
            return None
        s.sendall(pkt(2, 2, cmd))
        _, out = recv()
        return COLOR.sub("", out)
    except OSError:
        return None
    finally:
        s.close()


def run(*args, timeout=15):
    try:
        return subprocess.run(args, capture_output=True, text=True, timeout=timeout).stdout
    except Exception as e:  # noqa: BLE001
        return str(e)


def load_json(name):
    try:
        return json.load(open(os.path.join(DIR, name), encoding="utf-8"))
    except Exception:  # noqa: BLE001
        return []


def status():
    active = run("systemctl", "is-active", SERVICE).strip() == "active"
    p = props()
    players, maxp = [], int(p.get("max-players", "0") or 0)
    tps = None
    if active:
        lst = rcon("list") or ""
        m = re.search(r"(\d+) of a max of (\d+) players online:?\s*(.*)", lst)
        if m:
            maxp = int(m.group(2))
            players = [x.strip() for x in m.group(3).split(",") if x.strip()]
        t = rcon("tps") or ""
        m = re.search(r":\s*\*?([\d.]+)", t)
        if m:
            tps = float(m.group(1))
    mem = run("systemctl", "show", SERVICE, "-p", "MemoryCurrent", "--value").strip()
    since = run("systemctl", "show", SERVICE, "-p", "ActiveEnterTimestampMonotonic", "--value").strip()
    uptime = None
    try:
        if active and since.isdigit():
            uptime = int(time.monotonic() - int(since) / 1_000_000)
    except ValueError:
        pass
    version = ""
    try:
        version = open(os.path.join(DIR, ".mc-version"), encoding="utf-8").read().strip()
    except OSError:
        pass
    return {
        "online": active,
        "players": players,
        "max": maxp,
        "tps": tps,
        "ramMb": int(mem) // 1048576 if mem.isdigit() else None,
        "uptime": uptime,
        "version": version,
        "motd": p.get("motd", ""),
        "whitelist": p.get("white-list", "true") == "true",
    }


def console():
    out = run("journalctl", "-u", SERVICE, "-n", "250", "--no-pager", "-o", "cat")
    return COLOR.sub("", out)


RESET = {"running": False, "step": "", "done": 0, "error": ""}


def set_seed_random():
    """level-seed leeren: beim nächsten Start würfelt Minecraft eine neue Welt."""
    path = os.path.join(DIR, "server.properties")
    lines = open(path, encoding="utf-8").read().splitlines()
    out = [("level-seed=" if ln.startswith("level-seed=") else ln) for ln in lines]
    if not any(ln.startswith("level-seed=") for ln in lines):
        out.append("level-seed=")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")


def do_reset(new_seed):
    """Welt neu: Spieler rauswerfen, Server stoppen, alte Welt sichern, Welt-Ordner löschen (mit Inventaren,
    Stats, Erfolgen, Toden/Scoreboard), optional neuer Seed, Server wieder starten. Whitelist und OPs bleiben."""
    def step(t):
        RESET["step"] = t

    try:
        step("Spieler werden informiert …")
        if rcon("say §cDie Welt wird jetzt zurückgesetzt – gleich geht es mit einer neuen Welt weiter!") is not None:
            time.sleep(3)
            rcon("kick @a Die Welt wird zurückgesetzt – in ein bis zwei Minuten wieder da")
            rcon("save-all flush")
            time.sleep(2)
        step("Server wird gestoppt …")
        run("systemctl", "stop", SERVICE, timeout=180)
        worlds = sorted(d for d in os.listdir(DIR) if d.startswith("world") and os.path.isdir(os.path.join(DIR, d)))
        if worlds:
            step("Alte Welt wird gesichert …")
            os.makedirs(os.path.join(DIR, "backups"), exist_ok=True)
            name = "vor-reset_" + time.strftime("%Y-%m-%d_%H-%M") + ".tar.gz"
            r = subprocess.run(["tar", "-czf", os.path.join(DIR, "backups", name), "-C", DIR] + worlds, capture_output=True, text=True, timeout=3600)
            if r.returncode != 0:
                raise RuntimeError("Sicherung fehlgeschlagen – Welt wurde NICHT gelöscht: " + (r.stderr or "")[-200:])
            step("Alte Welt wird gelöscht …")
            for w in worlds:
                subprocess.run(["rm", "-rf", "--", os.path.join(DIR, w)], timeout=600)
        if new_seed:
            set_seed_random()
        run("chown", "-R", "minecraft:minecraft", DIR, timeout=300)
        step("Server startet mit neuer Welt …")
        run("systemctl", "start", SERVICE, timeout=120)
        RESET["done"] = time.time()
        step("Fertig – neue Welt wird erzeugt (erster Start dauert etwas länger)")
    except Exception as e:  # noqa: BLE001
        RESET["error"] = str(e)
        run("systemctl", "start", SERVICE, timeout=120)
        step("Abgebrochen")
    finally:
        RESET["running"] = False


def backups():
    d = os.path.join(DIR, "backups")
    items = []
    try:
        for f in sorted(os.listdir(d), reverse=True):
            if BACKUP.match(f):
                st = os.stat(os.path.join(d, f))
                items.append({"name": f, "mb": round(st.st_size / 1048576, 1), "time": int(st.st_mtime)})
    except OSError:
        pass
    return items


def edit_list(file, name, add, op=False):
    """Whitelist/OP-Liste direkt bearbeiten (wenn der Server aus ist)."""
    import urllib.request

    data = load_json(file)
    if add:
        req = urllib.request.Request("https://api.mojang.com/users/profiles/minecraft/" + name, headers={"User-Agent": "meowl-panel"})
        prof = json.load(urllib.request.urlopen(req, timeout=10))
        u = prof["id"]
        uuid = f"{u[0:8]}-{u[8:12]}-{u[12:16]}-{u[16:20]}-{u[20:]}"
        if not any(e.get("uuid") == uuid for e in data):
            entry = {"uuid": uuid, "name": prof["name"]}
            if op:
                entry.update({"level": 4, "bypassesPlayerLimit": True})
            data.append(entry)
    else:
        data = [e for e in data if e.get("name", "").lower() != name.lower()]
    json.dump(data, open(os.path.join(DIR, file), "w", encoding="utf-8"), indent=2)
    run("chown", "minecraft:minecraft", os.path.join(DIR, file))


PAGE = r"""<!doctype html><html lang="de"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Survival-Panel</title><style>
:root{--bg:#0d0f17;--card:#161a26;--line:#262c3e;--txt:#e8eaf2;--dim:#8a90a8;--acc:#8b5cf6;--ok:#3ecf8e;--bad:#ff5f6d}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--txt);font:15px/1.45 system-ui,Segoe UI,sans-serif}
header{display:flex;align-items:center;gap:12px;padding:14px 20px;border-bottom:1px solid var(--line);background:#11141e;position:sticky;top:0}
header h1{font-size:18px;margin:0}.dot{width:10px;height:10px;border-radius:50%;background:var(--bad)}.dot.on{background:var(--ok)}
nav{display:flex;gap:6px;margin-left:auto;flex-wrap:wrap}nav button{background:none;border:1px solid var(--line);color:var(--dim);padding:7px 12px;border-radius:8px;cursor:pointer}
nav button.act{background:var(--acc);color:#fff;border-color:var(--acc)}main{max-width:1000px;margin:0 auto;padding:20px}
.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:12px}.card{background:var(--card);border:1px solid var(--line);border-radius:12px;padding:14px}
.k{color:var(--dim);font-size:13px}.v{font-size:24px;font-weight:600;margin-top:4px}.row{display:flex;gap:8px;flex-wrap:wrap;margin:14px 0}
button.b{background:#232a3d;color:var(--txt);border:1px solid var(--line);padding:9px 14px;border-radius:9px;cursor:pointer}button.b:hover{border-color:var(--acc)}
button.p{background:var(--acc);border-color:var(--acc)}button.r{background:#3a1d24;border-color:#5a2a33}
input{background:#0f121b;border:1px solid var(--line);color:var(--txt);padding:9px 12px;border-radius:9px;min-width:0;flex:1}
pre{background:#090b11;border:1px solid var(--line);border-radius:10px;padding:12px;height:420px;overflow:auto;white-space:pre-wrap;font:12.5px/1.4 ui-monospace,Consolas,monospace;margin:0}
ul{list-style:none;padding:0;margin:0}li{display:flex;align-items:center;gap:10px;padding:8px 10px;border-bottom:1px solid var(--line)}li img{width:24px;height:24px;border-radius:4px;image-rendering:pixelated}
li .sp{margin-left:auto;display:flex;gap:6px}.hide{display:none}#msg{position:fixed;bottom:18px;left:50%;transform:translateX(-50%);background:#222a3e;padding:10px 16px;border-radius:10px;opacity:0;transition:.3s}
#login{max-width:340px;margin:12vh auto;text-align:center}#login input{width:100%;margin:14px 0}h2{font-size:16px;margin:22px 0 8px}
#me{font-size:12.5px;color:var(--dim);border:1px solid var(--line);border-radius:20px;padding:3px 10px}#me.g{color:#ffd479;border-color:#6a5420}
select{background:#0f121b;border:1px solid var(--line);color:var(--txt);padding:9px 12px;border-radius:9px}
.newpw{background:#14241c;border:1px solid #2c5a42;border-radius:10px;padding:12px;margin:10px 0}.newpw code{font-size:20px;letter-spacing:1px;user-select:all;color:#7dffb8}
</style></head><body>
<div id="login" class="hide card"><h1>Survival-Panel</h1><p class="k">Hauptpasswort (steht am Ende der Installation) oder ein Gast-Passwort</p>
<input id="pw" type="password" placeholder="Passwort" autofocus><button class="b p" style="width:100%" onclick="login()">Anmelden</button><p id="lerr" class="k"></p></div>
<div id="app" class="hide"><header><span id="dot" class="dot"></span><h1>Survival-Server</h1>
<span id="me"></span><nav><button data-t="home" class="act">Übersicht</button><button data-t="con">Konsole</button><button data-t="pl" data-r="1">Spieler</button><button data-t="bk" data-r="2">Backups</button><button data-t="tmp" data-r="3">Gast-Zugänge</button><button onclick="logout()">Abmelden</button></nav></header>
<main>
<section id="home"><div class="grid">
<div class="card"><div class="k">Status</div><div class="v" id="s_on">–</div></div>
<div class="card"><div class="k">Spieler</div><div class="v" id="s_pl">–</div></div>
<div class="card"><div class="k">TPS (20 = perfekt)</div><div class="v" id="s_tps">–</div></div>
<div class="card"><div class="k">Arbeitsspeicher</div><div class="v" id="s_ram">–</div></div>
<div class="card"><div class="k">Läuft seit</div><div class="v" id="s_up">–</div></div>
<div class="card"><div class="k">Version</div><div class="v" id="s_ver">–</div></div></div>
<div class="row" data-r="2"><button class="b p" onclick="svc('start')">Starten</button><button class="b" onclick="svc('restart')">Neu starten</button><button class="b r" onclick="svc('stop')">Stoppen</button></div>
<h2>Gerade online</h2><ul id="online"></ul></section>
<section id="con" class="hide"><pre id="log"></pre><div class="row" data-r="1"><input id="cmd" placeholder="Befehl ohne / (z.B. say Hallo, time set day, gamemode creative Name)"><button class="b p" onclick="send()">Senden</button></div></section>
<section id="pl" class="hide"><h2>Whitelist</h2><div class="row"><input id="wlname" placeholder="Spielername"><button class="b p" onclick="wl('add')">Hinzufügen</button></div><ul id="wl"></ul>
<div data-r="2"><h2>Admins (OP)</h2><div class="row"><input id="opname" placeholder="Spielername"><button class="b p" onclick="op('add')">Zum Admin machen</button></div><ul id="ops"></ul></div></section>
<section id="tmp" class="hide"><p class="k">Gast-Zugänge haben ein eigenes Passwort, laufen von selbst ab und können jederzeit gelöscht werden. Das Hauptpasswort bleibt geheim.</p>
<div class="card"><div class="row"><input id="tname" placeholder="Name (z.B. Max)" maxlength="24">
<select id="trole"><option value="zuschauer">Zuschauer – nur ansehen</option><option value="moderator" selected>Moderator – Konsole, Kicken, Whitelist</option><option value="admin">Admin – alles außer Gast-Zugänge</option></select>
<select id="thours"><option value="1">1 Stunde</option><option value="3" selected>3 Stunden</option><option value="12">12 Stunden</option><option value="24">1 Tag</option><option value="72">3 Tage</option><option value="168">7 Tage</option><option value="720">30 Tage</option></select></div>
<div class="row"><input id="tpw" placeholder="Passwort (leer = zufällig erzeugen)" maxlength="64"><button class="b p" onclick="tmpAdd()">Zugang erstellen</button></div><div id="tnew"></div></div>
<h2>Aktive Gast-Zugänge</h2><ul id="tlist"></ul></section>
<section id="bk" class="hide"><div class="row"><button class="b p" onclick="backup()">Backup jetzt erstellen</button></div><p class="k">Automatisch jede Nacht um 4:30, die letzten 7 bleiben.</p><ul id="bks"></ul>
<div data-r="3" class="hide" style="margin-top:18px;padding:12px;border:1px solid #a33;border-radius:10px">
<b style="color:#ff7a7a">Welt zurücksetzen</b>
<p class="k">Löscht die Welt mit allen Inventaren, Stats, Erfolgen und Toden. Whitelist und OPs bleiben. Die alte Welt wird vorher als Backup „vor-reset_…“ gesichert (wird nicht automatisch gelöscht).</p>
<label class="k"><input type="checkbox" id="rseed" checked> Neue Welt mit neuem Seed</label>
<div class="row"><input id="rconfirm" placeholder="ZURÜCKSETZEN eintippen"><button class="b r" onclick="resetWorld()">Welt zurücksetzen</button></div>
<p class="k" id="rstate"></p></div></section>
</main></div><div id="msg"></div>
<script>
const $=id=>document.getElementById(id);let tab='home',timer,rank=0;
function msg(t){const m=$('msg');m.textContent=t;m.style.opacity=1;setTimeout(()=>m.style.opacity=0,2500)}
async function api(p,b){const r=await fetch(p,{method:b?'POST':'GET',headers:{'Content-Type':'application/json','X-Panel':'1'},body:b?JSON.stringify(b):undefined});
if(r.status==401){show(false);throw 0}if(r.status==403){msg('Keine Rechte für diesen Zugang');throw 0}return r.json()}
function show(on){$('login').classList.toggle('hide',on);$('app').classList.toggle('hide',!on);if(on){load();clearInterval(timer);timer=setInterval(load,3000)}}
async function login(){const r=await fetch('/api/login',{method:'POST',headers:{'Content-Type':'application/json','X-Panel':'1'},body:JSON.stringify({password:$('pw').value})});
const j=await r.json();if(j.ok)show(true);else $('lerr').textContent=j.error||'Falsch'}
$('pw').addEventListener('keydown',e=>{if(e.key=='Enter')login()});$('cmd').addEventListener('keydown',e=>{if(e.key=='Enter')send()});
async function logout(){await api('/api/logout',{});show(false)}
document.querySelectorAll('nav button[data-t]').forEach(b=>b.onclick=()=>{tab=b.dataset.t;document.querySelectorAll('nav button').forEach(x=>x.classList.remove('act'));b.classList.add('act');
['home','con','pl','bk','tmp'].forEach(s=>$(s).classList.toggle('hide',s!=tab));load()});
function left(u){const s=Math.max(0,u-Date.now()/1000);const d=Math.floor(s/86400),h=Math.floor(s%86400/3600),m=Math.floor(s%3600/60);return d?d+' T '+h+' h':h?h+' h '+m+' min':m+' min'}
function applyMe(me){rank=me.rank;document.querySelectorAll('[data-r]').forEach(e=>e.classList.toggle('hide',rank<+e.dataset.r));
const b=$('me');if(me.role=='besitzer'){b.textContent='Besitzer';b.className=''}else{b.textContent='Gast: '+me.name+' · '+me.label+' · noch '+left(me.until);b.className='g'}
if(document.querySelector('nav button.act').classList.contains('hide'))document.querySelector('nav button[data-t=home]').click()}
async function tmpAdd(){const r=await api('/api/temp',{name:$('tname').value,role:$('trole').value,hours:+$('thours').value,password:$('tpw').value});
if(!r.ok){msg(r.message);return}$('tpw').value='';$('tname').value='';
$('tnew').innerHTML='<div class=newpw>Zugang für <b>'+esc(r.name)+'</b> ('+r.label+'), gültig '+(r.hours>=24?r.hours/24+' Tag(e)':r.hours+' h')+'<br>Passwort: <code>'+esc(r.password)+'</code><div class=k>Nur jetzt sichtbar – zusammen mit dem Panel-Link weitergeben.</div></div>';load()}
async function tmpDel(id){if(!confirm('Zugang löschen? Wer damit eingeloggt ist, fliegt sofort raus.'))return;const r=await api('/api/temp',{action:'delete',id:id});msg(r.message);load()}
function head(n){return '<img src="https://mc-heads.net/avatar/'+encodeURIComponent(n)+'/24">'}
function esc(s){return s.replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]))}
function dur(s){if(s==null)return'–';const h=Math.floor(s/3600),m=Math.floor(s%3600/60);return h?h+' h '+m+' min':m+' min'}
async function load(){try{const s=await api('/api/status');if(s.me)applyMe(s.me);$('dot').classList.toggle('on',s.online);$('s_on').textContent=s.online?'Online':'Aus';
$('s_pl').textContent=s.players.length+' / '+s.max;$('s_tps').textContent=s.tps==null?'–':s.tps.toFixed(1);$('s_ram').textContent=s.ramMb==null?'–':s.ramMb+' MB';
$('s_up').textContent=dur(s.uptime);$('s_ver').textContent=s.version||'–';
$('online').innerHTML=s.players.map(n=>'<li>'+head(n)+esc(n)+(rank>=1?'<span class="sp"><button class="b" onclick="cmdp(\'kick '+n+'\')">Kicken</button></span>':'')+'</li>').join('')||'<li class="k">Niemand online</li>';
if(tab=='con'){const c=await api('/api/console');const l=$('log');const end=l.scrollTop+l.clientHeight>=l.scrollHeight-20;l.textContent=c.text;if(end)l.scrollTop=l.scrollHeight}
if(tab=='pl'){const w=await api('/api/lists');$('wl').innerHTML=w.whitelist.map(n=>'<li>'+head(n)+esc(n)+'<span class="sp"><button class="b r" onclick="wl(\'remove\',\''+n+'\')">Entfernen</button></span></li>').join('')||'<li class="k">leer</li>';
$('ops').innerHTML=w.ops.map(n=>'<li>'+head(n)+esc(n)+'<span class="sp"><button class="b r" onclick="op(\'remove\',\''+n+'\')">Admin weg</button></span></li>').join('')||'<li class="k">keine</li>'}
if(tab=='tmp'){const t=await api('/api/temp');$('tlist').innerHTML=t.items.map(x=>'<li><b>'+esc(x.name)+'</b><span class="k">'+x.label+' · noch '+left(x.until)+(x.sessions?' · '+x.sessions+' angemeldet':'')+'</span><span class="sp"><button class="b r" onclick="tmpDel(\''+x.id+'\')">Löschen</button></span></li>').join('')||'<li class="k">Keine Gast-Zugänge</li>'}
if(tab=='bk'){const b=await api('/api/backups');$('bks').innerHTML=b.items.map(x=>'<li>'+esc(x.name)+'<span class="k">'+x.mb+' MB</span><span class="sp"><a class="b" href="/backups/'+encodeURIComponent(x.name)+'">Herunterladen</a></span></li>').join('')||'<li class="k">Noch keine Backups</li>'}}catch(e){}}
async function svc(a){if(a!='start'&&!confirm('Server wirklich '+(a=='stop'?'stoppen':'neu starten')+'?'))return;const r=await api('/api/service',{action:a});msg(r.message||'OK');load()}
async function send(){const c=$('cmd').value.trim();if(!c)return;$('cmd').value='';const r=await api('/api/command',{cmd:c});msg(r.output||'OK');load()}
async function cmdp(c){const r=await api('/api/command',{cmd:c});msg(r.output||'OK');load()}
async function wl(a,n){n=n||$('wlname').value.trim();if(!n)return;const r=await api('/api/whitelist',{action:a,name:n});msg(r.message);$('wlname').value='';load()}
async function op(a,n){n=n||$('opname').value.trim();if(!n)return;const r=await api('/api/op',{action:a,name:n});msg(r.message);$('opname').value='';load()}
async function resetWorld(){if(!confirm('Wirklich die ganze Welt mit allen Stats und Toden zurücksetzen? Alle Spieler werden gekickt.'))return;const r=await api('/api/reset',{confirm:$('rconfirm').value,newSeed:$('rseed').checked});msg(r.message);$('rconfirm').value='';resetPoll()}
async function resetPoll(){try{const r=await api('/api/reset');$('rstate').textContent=(r.error?'Fehler: '+r.error+' – ':'')+(r.step||'');if(r.running)setTimeout(resetPoll,1500);else if(tab=='bk')load()}catch(e){}}
async function backup(){const r=await api('/api/backup',{});msg(r.message);setTimeout(load,4000)}
fetch('/api/status',{headers:{'X-Panel':'1'}}).then(r=>show(r.status!=401));
</script></body></html>"""


class Handler(BaseHTTPRequestHandler):
    server_version = "SurvivalPanel"

    def log_message(self, fmt, *args):
        pass

    def client(self):
        return self.headers.get("X-Forwarded-For", self.client_address[0]).split(",")[0].strip()

    def session(self):
        """Gültige Sitzung als dict {tok, role, rank, name, until, temp} oder None."""
        c = SimpleCookie(self.headers.get("Cookie", ""))
        tok = c["mp_session"].value if "mp_session" in c else ""
        now = time.time()
        with LOCK:
            s = SESSIONS.get(tok)
            if not s or s["exp"] <= now:
                SESSIONS.pop(tok, None)
                return None
        if s.get("temp"):
            # Gast-Zugang noch da (nicht widerrufen, nicht abgelaufen)?
            if not any(t.get("id") == s["temp"] for t in load_temp()):
                with LOCK:
                    SESSIONS.pop(tok, None)
                return None
        return dict(s, tok=tok, rank=RANKS.get(s["role"], 0))

    def deny(self):
        return self.send(403, {"error": "Keine Rechte", "message": "Dafür reichen die Rechte dieses Zugangs nicht", "output": "Keine Rechte"})

    def send(self, code, body, ctype="application/json", extra=None):
        data = body if isinstance(body, bytes) else (json.dumps(body) if ctype == "application/json" else body).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype + ("; charset=utf-8" if not ctype.startswith("application/gzip") else ""))
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Referrer-Policy", "no-referrer")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(data)

    def body(self):
        n = min(int(self.headers.get("Content-Length", "0") or 0), 20000)
        try:
            return json.loads(self.rfile.read(n) or b"{}")
        except ValueError:
            return {}

    def do_GET(self):
        if self.path == "/" or self.path.startswith("/?"):
            return self.send(200, PAGE, "text/html")
        s = self.session()
        if not s:
            return self.send(401, {"error": "login"})
        if self.path == "/api/status":
            st = status()
            st["me"] = {"role": s["role"], "label": ROLE_LABEL.get(s["role"], s["role"]), "rank": s["rank"], "name": s.get("name", ""), "until": s.get("until")}
            return self.send(200, st)
        if self.path == "/api/console":
            return self.send(200, {"text": console()})
        if self.path == "/api/lists":
            if s["rank"] < 1:
                return self.deny()
            return self.send(200, {"whitelist": [e.get("name", "?") for e in load_json("whitelist.json")], "ops": [e.get("name", "?") for e in load_json("ops.json")]})
        if self.path == "/api/temp":
            if s["rank"] < 3:
                return self.deny()
            with LOCK:
                active = {}
                for v in SESSIONS.values():
                    if v.get("temp") and v["exp"] > time.time():
                        active[v["temp"]] = active.get(v["temp"], 0) + 1
            items = [{"id": t["id"], "name": t["name"], "role": t["role"], "label": ROLE_LABEL.get(t["role"], t["role"]), "until": t["until"], "sessions": active.get(t["id"], 0)} for t in load_temp()]
            return self.send(200, {"items": sorted(items, key=lambda x: x["until"]), "now": time.time()})
        if s["rank"] < 2 and (self.path == "/api/backups" or self.path.startswith("/backups/")):
            return self.deny()
        if self.path == "/api/backups":
            return self.send(200, {"items": backups()})
        if self.path == "/api/reset":
            if s["rank"] < 3:
                return self.deny()
            return self.send(200, dict(RESET))
        if self.path.startswith("/backups/"):
            name = self.path[len("/backups/"):]
            if not BACKUP.match(name):
                return self.send(404, {"error": "nicht gefunden"})
            path = os.path.join(DIR, "backups", name)
            if not os.path.isfile(path):
                return self.send(404, {"error": "nicht gefunden"})
            self.send_response(200)
            self.send_header("Content-Type", "application/gzip")
            self.send_header("Content-Length", str(os.path.getsize(path)))
            self.send_header("Content-Disposition", f'attachment; filename="{name}"')
            self.end_headers()
            with open(path, "rb") as f:
                while True:
                    chunk = f.read(1 << 20)
                    if not chunk:
                        break
                    self.wfile.write(chunk)
            return None
        return self.send(404, {"error": "nicht gefunden"})

    def do_POST(self):
        # einfacher Schutz gegen Anfragen fremder Seiten (zusätzlich zu SameSite=Strict)
        if self.headers.get("X-Panel") != "1":
            return self.send(403, {"error": "verboten"})
        if self.path == "/api/login":
            ip = self.client()
            now = time.time()
            with LOCK:
                fails = [t for t in FAILS.get(ip, []) if now - t < 600]
                FAILS[ip] = fails
            if len(fails) >= 8:
                return self.send(429, {"ok": False, "error": "Zu viele Versuche – 10 Minuten warten"})
            pw = str(self.body().get("password", ""))[:200]
            real = password()
            sess = None
            if real and hmac.compare_digest(pw.encode(), real.encode()):
                sess = {"exp": now + 12 * 3600, "role": "besitzer", "name": ""}
            elif pw:
                for t in load_temp():
                    if hmac.compare_digest(hash_pw(pw, t["salt"]), t["hash"]):
                        sess = {"exp": min(now + 12 * 3600, t["until"]), "role": t["role"], "name": t["name"], "temp": t["id"], "until": t["until"]}
                        break
            if sess:
                tok = secrets.token_urlsafe(32)
                with LOCK:
                    SESSIONS[tok] = sess
                age = max(60, int(sess["exp"] - now))
                return self.send(200, {"ok": True}, extra={"Set-Cookie": f"mp_session={tok}; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age={age}"})
            time.sleep(1.0)
            with LOCK:
                FAILS.setdefault(ip, []).append(now)
            return self.send(200, {"ok": False, "error": "Passwort falsch"})
        s = self.session()
        if not s:
            return self.send(401, {"error": "login"})
        tok = s["tok"]
        rank = s["rank"]
        b = self.body()
        if self.path == "/api/logout":
            with LOCK:
                SESSIONS.pop(tok, None)
            return self.send(200, {"ok": True}, extra={"Set-Cookie": "mp_session=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Strict"})
        if self.path == "/api/temp":
            if rank < 3:
                return self.deny()
            return self.send(200, self.temp_action(b))
        if self.path == "/api/command":
            if rank < 1:
                return self.deny()
            cmd = str(b.get("cmd", "")).strip().lstrip("/")[:300]
            if not cmd:
                return self.send(200, {"output": ""})
            first = cmd.split()[0].lower().split(":")[-1]
            if rank < 2 and (first in MOD_BLOCKED or cmd.lower().startswith("whitelist off")):
                return self.send(200, {"output": "Diesen Befehl dürfen Moderatoren nicht benutzen"})
            out = rcon(cmd)
            return self.send(200, {"output": "Server läuft nicht" if out is None else (out or "OK")})
        if self.path == "/api/service":
            if rank < 2:
                return self.deny()
            action = b.get("action")
            if action not in ("start", "stop", "restart"):
                return self.send(400, {"message": "unbekannt"})
            run("systemctl", action, SERVICE, timeout=120)
            return self.send(200, {"message": {"start": "Server startet …", "stop": "Server gestoppt", "restart": "Server startet neu …"}[action]})
        if self.path in ("/api/whitelist", "/api/op"):
            name = str(b.get("name", "")).strip()
            add = b.get("action") == "add"
            if not NAME.match(name):
                return self.send(200, {"message": "Ungültiger Name"})
            is_op = self.path == "/api/op"
            if rank < (2 if is_op else 1):
                return self.deny()
            cmd = ("op " if add else "deop ") + name if is_op else ("whitelist add " if add else "whitelist remove ") + name
            out = rcon(cmd)
            if out is None:
                try:
                    edit_list("ops.json" if is_op else "whitelist.json", name, add, op=is_op)
                    out = "Gespeichert (gilt beim nächsten Start)"
                except Exception:  # noqa: BLE001
                    out = "Spieler nicht gefunden"
            return self.send(200, {"message": out or "OK"})
        if self.path == "/api/reset":
            if rank < 3:
                return self.deny()
            if str(b.get("confirm", "")).strip().upper() != "ZURÜCKSETZEN":
                return self.send(200, {"ok": False, "message": "Zum Bestätigen ZURÜCKSETZEN eintippen"})
            with LOCK:
                if RESET["running"]:
                    return self.send(200, {"ok": False, "message": "Reset läuft schon"})
                RESET.update({"running": True, "step": "Startet …", "done": 0, "error": ""})
            threading.Thread(target=do_reset, args=(bool(b.get("newSeed")),), daemon=True).start()
            return self.send(200, {"ok": True, "message": "Reset läuft – alte Welt wird vorher gesichert"})
        if self.path == "/api/backup":
            if rank < 2:
                return self.deny()
            subprocess.Popen(["/usr/local/bin/mc-backup"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            return self.send(200, {"message": "Backup läuft – dauert je nach Weltgröße etwas"})
        return self.send(404, {"error": "nicht gefunden"})

    def temp_action(self, b):
        """Gast-Zugänge anlegen oder widerrufen (nur Besitzer)."""
        items = load_temp()
        if b.get("action") == "delete":
            tid = str(b.get("id", ""))
            items = [t for t in items if t.get("id") != tid]
            save_temp(items)
            with LOCK:
                for k in [k for k, v in SESSIONS.items() if v.get("temp") == tid]:
                    SESSIONS.pop(k, None)
            return {"ok": True, "message": "Zugang gelöscht – wer damit drin war, ist abgemeldet"}
        name = re.sub(r"[^\w .\-äöüÄÖÜß]", "", str(b.get("name", "")))[:24].strip() or "Gast"
        role = b.get("role") if b.get("role") in ("zuschauer", "moderator", "admin") else "zuschauer"
        try:
            hours = max(0.25, min(24 * 30, float(b.get("hours", 3))))
        except (TypeError, ValueError):
            hours = 3.0
        pw = str(b.get("password", "")).strip()
        if pw and len(pw) < 6:
            return {"ok": False, "message": "Passwort mindestens 6 Zeichen (oder leer lassen für ein zufälliges)"}
        if pw and hmac.compare_digest(pw.encode(), password().encode()):
            return {"ok": False, "message": "Nicht das Hauptpasswort benutzen"}
        if not pw:
            alphabet = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            pw = "".join(secrets.choice(alphabet) for _ in range(10))
        if len(items) >= 30:
            return {"ok": False, "message": "Zu viele Zugänge – erst alte löschen"}
        salt = secrets.token_hex(16)
        items.append({"id": secrets.token_hex(6), "name": name, "role": role, "until": time.time() + hours * 3600, "created": time.time(), "salt": salt, "hash": hash_pw(pw, salt)})
        save_temp(items)
        return {"ok": True, "password": pw, "name": name, "label": ROLE_LABEL[role], "hours": hours, "message": "Zugang erstellt"}


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
