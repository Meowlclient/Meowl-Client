package com.lennart.meowlserver;

import com.lennart.meowlserver.auth.AuthService;
import com.lennart.meowlserver.auth.Session;
import com.lennart.meowlserver.cam.CamRelay;
import com.lennart.meowlserver.clap.ClapService;
import com.lennart.meowlserver.duel.DuelService;
import com.lennart.meowlserver.events.EventHub;
import com.lennart.meowlserver.friends.FriendService;
import com.lennart.meowlserver.chat.ChatService;
import com.lennart.meowlserver.games.GameService;
import com.lennart.meowlserver.paint.PaintStore;
import com.lennart.meowlserver.images.ImageStore;
import com.lennart.meowlserver.images.StreamStore;
import com.lennart.meowlserver.images.WatchSession;
import com.lennart.meowlserver.update.UpdateStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class MeowlServer {
   private static final Logger LOG = Logger.getLogger("MeowlServer");
   public static final String VERSION = "1.21.3";
   private static final int MAX_JSON = 16384;
   private final AuthService auth;
   private final EventHub events = new EventHub();
   private final ImageStore images;
   private final UpdateStore updates;
   private final StreamStore streams = new StreamStore();
   private final CamRelay cams = new CamRelay((sender, server, on) -> this.broadcastToServer(server, "cam_state", Map.of("uuid", sender.toString(), "on", on)));
   private final DuelService duels;
   private final ClapService claps;
   private final FriendService friends;
   private final GameService games;
   private final PaintStore paint;
   private final PaintStore chatImages;
   private final ChatService chat;
   private final RateLimiter limiter = new RateLimiter();
   private boolean trustProxy;
   public static final String DEFAULT_DISCORD_APP_ID = "1557308109532176384";
   private String discordAppId = "";

   private static String readDiscordAppId(Path dataDir) {
      String env = System.getenv("MEOWL_DISCORD_APP_ID");
      String id = env != null ? env.trim() : "";
      if (id.isEmpty()) {
         try {
            Path f = dataDir.resolve("discord-app-id.txt");
            if (Files.exists(f)) {
               id = Files.readString(f).trim();
            }
         } catch (IOException var4) {
         }
      }

      if (id.isEmpty()) {
         id = DEFAULT_DISCORD_APP_ID;
      }

      return id.matches("\\d{15,22}") ? id : "";
   }

   private MeowlServer(Path dataDir, boolean offlineAuth) {
      this.discordAppId = readDiscordAppId(dataDir);
      WatchSession.cookies = dataDir.resolve("youtube-cookies.txt");
      WatchSession.startCookieKeeper();
      this.auth = new AuthService(offlineAuth);
      this.images = new ImageStore(dataDir);
      this.updates = new UpdateStore(dataDir);
      this.duels = new DuelService(this.auth, this.events);
      this.claps = new ClapService(this.auth, this.events);
      this.friends = new FriendService(dataDir, this.auth, this.events);
      this.games = new GameService(this.auth, this.events);
      this.paint = new PaintStore(dataDir);
      this.chatImages = new PaintStore(dataDir, "chatimg", 2560, true, 2000);
      this.chat = new ChatService(dataDir, this.friends, this.events);
   }

   public static void main(String[] args) throws IOException {
      int port = 8787;
      String bind = "0.0.0.0";
      Path data = Path.of("meowl-data");
      boolean offline = false;
      boolean trustProxy = false;

      for (int i = 0; i < args.length; i++) {
         String var7 = args[i];
         switch (var7) {
            case "--port":
               port = Integer.parseInt(args[++i]);
               break;
            case "--data":
               data = Path.of(args[++i]);
               break;
            case "--bind":
               bind = args[++i];
               break;
            case "--offline-auth":
               offline = true;
               break;
            case "--trust-proxy":
               trustProxy = true;
               break;
            default:
               System.err.println("Unbekannte Option: " + args[i]);
               System.err.println("Optionen: --port <zahl>  --bind <ip>  --data <ordner>  --offline-auth  --trust-proxy");
               System.exit(2);
         }
      }

      MeowlServer server = new MeowlServer(data, offline);
      server.trustProxy = trustProxy;
      server.start(bind, port);
   }

   private void start(String bind, int port) throws IOException {
      HttpServer server = HttpServer.create(new InetSocketAddress(bind, port), 128);
      server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
      server.createContext("/", this::handle);
      server.start();
      ScheduledExecutorService maintenance = Executors.newSingleThreadScheduledExecutor();
      maintenance.scheduleAtFixedRate(this::maintenance, 1L, 1L, TimeUnit.SECONDS);
      LOG.info("Meowl Server " + VERSION + " läuft auf " + bind + ":" + port);
   }

   private void maintenance() {
      try {
         this.duels.tick();
         this.claps.tick();
         this.games.tick();
         this.cams.tick(uuid -> {
            Session s = this.auth.byUuid(uuid);
            return s != null && s.isOnline();
         });
         if (System.currentTimeMillis() / 1000L % 30L == 0L) {
            this.auth.cleanup();
            this.limiter.cleanup();
         }
      } catch (RuntimeException var2) {
         LOG.warning("Wartung fehlgeschlagen: " + var2);
      }
   }

   private void handle(HttpExchange ex) {
      try {
         this.route(ex);
      } catch (ApiException var8) {
         sendJson(ex, var8.status(), Map.of("error", var8.getMessage()));
      } catch (IllegalArgumentException var9) {
         sendJson(ex, 400, Map.of("error", var9.getMessage() == null ? "Ungültige Anfrage" : var9.getMessage()));
      } catch (Exception var10) {
         LOG.warning("Fehler bei " + ex.getRequestURI() + ": " + var10);
         sendJson(ex, 500, Map.of("error", "Interner Fehler"));
      } finally {
         ex.close();
      }
   }

   private void route(HttpExchange ex) throws IOException, InterruptedException {
      String method = ex.getRequestMethod();
      String path = ex.getRequestURI().getPath();
      String ip = ex.getRemoteAddress().getAddress().getHostAddress();
      if (this.trustProxy && ex.getRemoteAddress().getAddress().isLoopbackAddress()) {
         String fwd = ex.getRequestHeaders().getFirst("X-Forwarded-For");
         if (fwd != null && !fwd.isBlank()) {
            String last = fwd.substring(fwd.lastIndexOf(44) + 1).trim();
            if (!last.isEmpty() && last.length() <= 45) {
               ip = last;
            }
         }
      }

      if (path.equals("/assets/meowl.png") && method.equals("GET")) {
         this.limiter.check(ip, "asset", 30.0, 1.0);
         byte[] png = MeowlLogo.png();
         ex.getResponseHeaders().add("Content-Type", "image/png");
         ex.getResponseHeaders().add("Cache-Control", "public, max-age=86400");
         ex.sendResponseHeaders(200, png.length);

         try (OutputStream out = ex.getResponseBody()) {
            out.write(png);
         }
      } else if (path.equals("/status")) {
         Map<String, Object> st = new LinkedHashMap<>();
         st.put("name", "Meowl Server");
         st.put("version", VERSION);
         st.put("offlineAuth", this.auth.isOfflineAuth());
         if (!this.discordAppId.isEmpty()) {
            st.put("discordAppId", this.discordAppId);
         }

         sendJson(ex, 200, st);
      } else if (path.equals("/update/latest") && method.equals("GET")) {
         this.limiter.check(ip, "update", 20.0, 0.3333333333333333);
         UpdateStore store = this.updates.forMc(query(ex.getRequestURI()).get("mc"));
         UpdateStore.Release release = store == null ? null : store.latest();
         if (release == null) {
            throw new ApiException(404, "Keine Updates veröffentlicht");
         }

         sendJson(ex, 200, release.toJson());
      } else if (path.equals("/update/notes") && method.equals("GET")) {
         this.limiter.check(ip, "update.notes", 10.0, 0.2);
         UpdateStore store = this.updates.forMc(query(ex.getRequestURI()).get("mc"));
         sendJson(ex, 200, Map.of("notes", store == null ? java.util.List.of() : store.notes()));
      } else if (path.startsWith("/update/download/") && method.equals("GET")) {
         this.limiter.check(ip, "update.dl", 6.0, 0.01);
         UpdateStore store = this.updates.forMc(query(ex.getRequestURI()).get("mc"));
         Path file = store == null ? null : store.file(path.substring("/update/download/".length()));
         if (file == null) {
            throw new ApiException(404, "Version nicht gefunden");
         }

         ex.getResponseHeaders().add("Content-Type", "application/java-archive");
         ex.sendResponseHeaders(200, Files.size(file));

         try (OutputStream out = ex.getResponseBody()) {
            Files.copy(file, out);
         }
      } else if ((path.startsWith("/paint/") || path.startsWith("/chatimg/")) && method.equals("GET")) {
         this.limiter.check(ip, "paint.get", 60.0, 5.0);
         Path file = path.startsWith("/chatimg/") ? this.chatImages.file(lastSegment(path)) : this.paint.file(lastSegment(path));
         if (file == null) {
            throw new ApiException(404, "Bild nicht gefunden");
         }

         ex.getResponseHeaders().add("Content-Type", PaintStore.contentType(file));
         ex.getResponseHeaders().add("Cache-Control", "public, max-age=31536000, immutable");
         ex.sendResponseHeaders(200, Files.size(file));

         try (OutputStream out = ex.getResponseBody()) {
            Files.copy(file, out);
         }
      } else if (path.equals("/auth/challenge") && method.equals("POST")) {
         this.limiter.check(ip, "auth", 10.0, 0.16666666666666666);
         Map<String, Object> body = jsonBody(ex);
         sendJson(ex, 200, Map.of("serverId", this.auth.challenge(Json.str(body, "name", 16))));
      } else if (path.equals("/auth/verify") && method.equals("POST")) {
         this.limiter.check(ip, "auth", 10.0, 0.16666666666666666);
         Map<String, Object> body = jsonBody(ex);
         Object uuid = body.get("uuid");
         Session s = this.auth.verify(Json.str(body, "name", 16), Json.str(body, "serverId", 64), uuid instanceof String u ? u : null);
         sendJson(ex, 200, Map.of("token", s.token(), "uuid", s.uuid().toString(), "name", s.name(), "offlineAuth", this.auth.isOfflineAuth()));
      } else {
         Session me = this.auth.require(ex.getRequestHeaders().getFirst("Authorization"));
         String user = me.uuid().toString();
         String mcServer = ex.getRequestHeaders().getFirst("X-Meowl-Server");
         if (mcServer != null) {
            String before = me.mcServer();
            String next = normalizeServer(mcServer);
            if (before != null && !before.isEmpty() && !before.equals(next)) {
               // Server verlassen: seine Bilder dort verschwinden
               this.dropImages(me.uuid(), before);
            }

            me.setMcServer(next);
         }

         String clientVersion = ex.getRequestHeaders().getFirst("X-Meowl-Client");
         if (clientVersion != null) {
            me.setClientVersion(clientVersion);
         }

         this.friends.remember(me);
         String var9 = method + " " + firstSegments(path);
         switch (var9) {
            case "POST /auth/logout":
               this.cams.stop(me.uuid());
               MeowlServer.ActiveEmote gone = this.emotes.remove(me.uuid());
               if (gone != null) {
                  this.broadcastToServer(gone.server(), "emote", Map.of("uuid", user, "name", me.name(), "id", gone.id(), "on", false, "loop", gone.loop()));
               }

               this.duels.leave(me);
               this.claps.cancel(me);
               this.dropImages(me.uuid(), null);
               this.games.leaveAll(me);
               this.auth.logout(me);
               sendJson(ex, 200, Map.of("ok", true));
               break;
            case "GET /events": {
               this.limiter.check(user, "events", 10.0, 5.0);
               Map<String, String> q = query(ex.getRequestURI());
               long since = Long.parseLong(q.getOrDefault("since", "-1"));
               if (since < 0L) {
                  sendJson(ex, 200, Map.of("events", List.of(), "latest", this.events.latest(me.uuid())));
                  return;
               }

               long wait = Math.min(25000L, Math.max(0L, Long.parseLong(q.getOrDefault("wait", "25")) * 1000L));
               List<Object> list = new ArrayList<>();
               long latest = since;

               for (EventHub.Event e : this.events.poll(me.uuid(), since, wait)) {
                  list.add(Map.of("seq", e.seq(), "type", e.type(), "data", e.data()));
                  latest = Math.max(latest, e.seq());
               }

               sendJson(ex, 200, Map.of("events", list, "latest", latest));
               break;
            }
            case "GET /images": {
               this.limiter.check(user, "images.list", 5.0, 0.5);
               List<Object> list = new ArrayList<>();
               this.images.forServer(me.mcServer()).forEach(i -> list.add(i.toJson()));
               sendJson(ex, 200, Map.of("images", list));
               break;
            }
            case "POST /images": {
               this.limiter.check(user, "images.add", 10.0, 0.16666666666666666);
               for (ImageStore.Image old : this.images.trimFor(me.uuid(), me.mcServer())) {
                  this.broadcastToServer(old.server(), "image_removed", Map.of("id", old.id()));
               }

               ImageStore.Image image = this.images.add(jsonBody(ex), me.mcServer(), me.uuid());
               this.broadcastToServer(image.server(), "image_added", image.toJson());
               sendJson(ex, 200, image.toJson());
               break;
            }
            case "DELETE /images": {
               this.limiter.check(user, "images.remove", 20.0, 0.3333333333333333);
               ImageStore.Image image = this.images.remove(lastSegment(path), me.uuid());
               this.broadcastToServer(image.server(), "image_removed", Map.of("id", image.id()));
               sendJson(ex, 200, Map.of("ok", true));
               break;
            }
            case "GET /streams": {
               String[] seg = path.split("/");
               if (seg.length >= 4) {
                  // /streams/<id>/frame bzw. /streams/<id>/audio: aktuelles Bild / neuer Ton der gemeinsamen Wiedergabe
                  WatchSession w = this.streams.session(seg[2]);
                  long afterSeq = Long.parseLong(query(ex.getRequestURI()).getOrDefault("after", "0"));
                  byte[] body = null;
                  long seq = 0L;
                  String type = "image/jpeg";
                  if (w != null && seg[3].equals("frame")) {
                     this.limiter.check(user, "streams.frame", 90.0, 40.0);
                     if (w.frame() != null && w.frameSeq() > afterSeq) {
                        body = w.frame();
                        seq = w.frameSeq();
                     }
                  } else if (w != null && seg[3].equals("audio")) {
                     this.limiter.check(user, "streams.audio", 30.0, 12.0);
                     Object[] a = w.audioAfter(afterSeq);
                     if (a != null) {
                        seq = (Long)a[0];
                        body = (byte[])a[1];
                        type = "application/octet-stream";
                     }
                  }

                  if (body == null) {
                     ex.sendResponseHeaders(204, -1L);
                     return;
                  }

                  ex.getResponseHeaders().add("Content-Type", type);
                  ex.getResponseHeaders().add("X-Seq", String.valueOf(seq));
                  ex.getResponseHeaders().add("X-Rate", String.valueOf(WatchSession.AUDIO_RATE));
                  ex.sendResponseHeaders(200, body.length);
                  try (OutputStream out = ex.getResponseBody()) {
                     out.write(body);
                  }

                  return;
               }

               this.limiter.check(user, "streams.list", 5.0, 0.5);
               List<Object> list = new ArrayList<>();
               this.streams.forServer(me.mcServer()).forEach(s -> list.add(s.toJson()));
               sendJson(ex, 200, Map.of("streams", list));
               break;
            }
            case "POST /streams": {
               String[] pseg = path.split("/");
               if (pseg.length >= 4 && pseg[3].equals("pause")) {
                  // Rechtsklick auf den Bildschirm: Pause/Weiter für alle Zuschauer
                  this.limiter.check(user, "streams.pause", 10.0, 2.0);
                  StreamStore.Screen sc = this.streams.screen(pseg[2]);
                  WatchSession ws = this.streams.session(pseg[2]);
                  if (sc == null || ws == null || !sc.server().equals(me.mcServer())) {
                     throw new ApiException(404, "Bildschirm nicht gefunden");
                  }

                  ws.setPaused(!ws.paused());
                  this.broadcastToServer(sc.server(), "stream_added", sc.toJson());
                  sendJson(ex, 200, sc.toJson());
                  break;
               }

               this.limiter.check(user, "streams.add", 6.0, 0.2);
               for (StreamStore.Screen old : this.streams.trimFor(me.uuid(), me.mcServer())) {
                  this.broadcastToServer(old.server(), "stream_removed", Map.of("id", old.id()));
               }

               StreamStore.Screen screen = this.streams.add(jsonBody(ex), me.mcServer(), me.uuid(), me.name());
               this.broadcastToServer(screen.server(), "stream_added", screen.toJson());
               sendJson(ex, 200, screen.toJson());
               break;
            }
            case "DELETE /streams": {
               this.limiter.check(user, "streams.remove", 20.0, 0.5);
               StreamStore.Screen screen = this.streams.remove(lastSegment(path), me.uuid());
               this.broadcastToServer(screen.server(), "stream_removed", Map.of("id", screen.id()));
               sendJson(ex, 200, Map.of("ok", true));
               break;
            }
            case "POST /cam/frame":
               this.limiter.check(user, "cam.frame", 12.0, 12.0);
               long seq = this.cams.upload(me.uuid(), me.mcServer(), readBody(ex, 49152));
               sendJson(ex, 200, Map.of("seq", seq));
               break;
            case "POST /cam/state":
               this.limiter.check(user, "cam.state", 10.0, 2.0);
               this.cams.setState(me.uuid(), me.mcServer(), Json.bool(jsonBody(ex), "on"));
               sendJson(ex, 200, Map.of("ok", true));
               break;
            case "POST /emote": {
               this.limiter.check(user, "emote", 8.0, 1.0);
               Map<String, Object> body = jsonBody(ex);
               String id = Json.str(body, "id", 24);
               if (!id.matches("[a-z_]{1,24}")) {
                  throw new ApiException(400, "Unbekannter Emote");
               }

               boolean on = Json.bool(body, "on");
               boolean loop = body.get("loop") instanceof Boolean b && b;
               String server = me.mcServer();
               if (on) {
                  if (server == null || server.isEmpty()) {
                     throw new ApiException(409, "Nicht auf einem Minecraft-Server");
                  }

                  this.emotes.put(me.uuid(), new MeowlServer.ActiveEmote(id, server, loop, System.currentTimeMillis(), me.name()));
               } else {
                  MeowlServer.ActiveEmote old = this.emotes.remove(me.uuid());
                  if (old != null) {
                     server = old.server();
                  }
               }

               if (server != null && !server.isEmpty()) {
                  this.broadcastToServer(server, "emote", Map.of("uuid", user, "name", me.name(), "id", id, "on", on, "loop", loop));
               }

               sendJson(ex, 200, Map.of("ok", true));
               break;
            }
            case "GET /emote/active": {
               this.limiter.check(user, "emote.active", 6.0, 0.5);
               List<Object> list = new ArrayList<>();
               long now = System.currentTimeMillis();
               this.emotes.entrySet().removeIf(en -> now - en.getValue().since() > (en.getValue().loop() ? 600000L : 10000L));

               for (Map.Entry<UUID, MeowlServer.ActiveEmote> en : this.emotes.entrySet()) {
                  MeowlServer.ActiveEmote e = en.getValue();
                  if (e.server().equals(me.mcServer()) && !en.getKey().equals(me.uuid())) {
                     list.add(Map.of("uuid", en.getKey().toString(), "name", e.name(), "id", e.id(), "loop", e.loop(), "age", now - e.since()));
                  }
               }

               sendJson(ex, 200, Map.of("emotes", list));
               break;
            }
            case "POST /cam/stop":
               this.cams.stop(me.uuid());
               sendJson(ex, 200, Map.of("ok", true));
               break;
            case "GET /cam/active":
               this.limiter.check(user, "cam.active", 10.0, 1.0);
               List<String> camList = new ArrayList<>(this.cams.active(me.mcServer()));

               // auch Spieler aus der eigenen Tab-Liste (falls die Server-Adresse unterschiedlich erkannt wird, z.B. LAN)
               for (UUID other : this.cams.allActive()) {
                  if (!camList.contains(other.toString()) && this.nearby(me, other)) {
                     camList.add(other.toString());
                  }
               }

               sendJson(ex, 200, Map.of("active", camList));
               break;
            case "GET /cam/frame":
               this.limiter.check(user, "cam.get", 120.0, 80.0);
               UUID sender = UUID.fromString(lastSegment(path));
               long after = Long.parseLong(query(ex.getRequestURI()).getOrDefault("after", "0"));
               CamRelay.Frame f = this.cams.latest(sender, me.mcServer(), after);
               if (f == null && this.nearby(me, sender)) {
                  f = this.cams.latestAny(sender, after);
               }

               if (f == null) {
                  ex.sendResponseHeaders(204, -1L);
                  return;
               }

               ex.getResponseHeaders().add("Content-Type", "image/jpeg");
               ex.getResponseHeaders().add("X-Seq", String.valueOf(f.seq()));
               ex.sendResponseHeaders(200, f.jpeg().length);

               try (OutputStream out = ex.getResponseBody()) {
                  out.write(f.jpeg());
                  break;
               }
            case "POST /presence": {
               this.limiter.check(user, "presence", 6.0, 0.1);
               Map<String, Object> presenceBody = jsonBody(ex);
               me.setPresence(presence(presenceBody));
               Set<String> near = new HashSet<>();
               Object e = presenceBody.get("near");
               if (e instanceof List) {
                  for (Object o : (List)e) {
                     if (near.size() >= 200) {
                        break;
                     }

                     if (o instanceof String n && n.length() <= 16) {
                        near.add(n.toLowerCase(Locale.ROOT));
                     }
                  }
               }

               me.setNear(near);
               List<Object> list = new ArrayList<>();

               for (Session s : this.auth.all()) {
                  Map<String, Object> p = s.presence();
                  boolean sameServer = !me.mcServer().isEmpty() && s.mcServer().equals(me.mcServer());
                  boolean inTab = !me.mcServer().isEmpty() && near.contains(s.name().toLowerCase(Locale.ROOT));
                  if (s != me && p != null && s.isOnline() && (sameServer || inTab)) {
                     Map<String, Object> entry = new LinkedHashMap<>(p);
                     entry.put("uuid", s.uuid().toString());
                     entry.put("name", s.name());
                     list.add(entry);
                  }
               }

               sendJson(ex, 200, Map.of("players", list));
               break;
            }
            case "POST /duel/invite":
               this.limiter.check(user, "duel.invite", 6.0, 0.1);
               sendJson(ex, 200, this.duels.invite(me, jsonBody(ex)));
               break;
            case "POST /duel/respond":
               sendJson(ex, 200, this.duels.respond(me, jsonBody(ex)));
               break;
            case "POST /duel/pose":
               this.limiter.check(user, "duel.pose", 30.0, 25.0);
               this.duels.pose(me, jsonBody(ex));
               sendJson(ex, 200, Map.of("ok", true));
               break;
            case "POST /duel/shot":
               this.limiter.check(user, "duel.shot", 5.0, 4.0);
               sendJson(ex, 200, this.duels.shot(me, jsonBody(ex)));
               break;
            case "POST /duel/ground":
               this.limiter.check(user, "duel.ground", 3.0, 0.1);
               this.duels.ground(me, jsonBody(ex));
               sendJson(ex, 200, Map.of("ok", true));
               break;
            case "POST /duel/reload":
               this.duels.reload(me);
               sendJson(ex, 200, Map.of("ok", true));
               break;
            case "POST /duel/leave":
               this.duels.leave(me);
               sendJson(ex, 200, Map.of("ok", true));
               break;
            case "GET /clap/time":
               this.limiter.check(user, "clap.time", 20.0, 4.0);
               sendJson(ex, 200, Map.of("serverTime", System.currentTimeMillis()));
               break;
            case "POST /clap/invite":
               this.limiter.check(user, "clap.invite", 6.0, 0.1);
               sendJson(ex, 200, this.claps.invite(me, jsonBody(ex)));
               break;
            case "POST /clap/respond":
               this.limiter.check(user, "clap.respond", 10.0, 1.0);
               sendJson(ex, 200, this.claps.respond(me, jsonBody(ex)));
               break;
            case "POST /clap/ready":
               this.limiter.check(user, "clap.ready", 20.0, 5.0);
               sendJson(ex, 200, this.claps.ready(me, jsonBody(ex)));
               break;
            case "POST /clap/hit":
               this.limiter.check(user, "clap.hit", 5.0, 1.0);
               sendJson(ex, 200, this.claps.hit(me, jsonBody(ex)));
               break;
            case "GET /friends":
               this.limiter.check(user, "friends.list", 10.0, 1.0);
               sendJson(ex, 200, this.friends.list(me));
               break;
            case "POST /friends/add":
               this.limiter.check(user, "friends.add", 10.0, 0.2);
               sendJson(ex, 200, this.friends.add(me, jsonBody(ex)));
               break;
            case "POST /friends/remove":
               this.limiter.check(user, "friends.remove", 10.0, 0.5);
               sendJson(ex, 200, this.friends.remove(me, jsonBody(ex)));
               break;
            case "POST /locate/update":
               this.limiter.check(user, "locate", 6.0, 1.0);
               sendJson(ex, 200, this.friends.locate(me, jsonBody(ex)));
               break;
            case "POST /locate/stop":
               this.limiter.check(user, "locate.stop", 6.0, 1.0);
               sendJson(ex, 200, this.friends.stop(me));
               break;
            case "POST /game/invite":
               this.limiter.check(user, "game.invite", 6.0, 0.2);
               sendJson(ex, 200, this.games.invite(me, jsonBody(ex)));
               break;
            case "POST /game/respond":
               this.limiter.check(user, "game.respond", 10.0, 1.0);
               sendJson(ex, 200, this.games.respond(me, jsonBody(ex)));
               break;
            case "POST /game/move":
               this.limiter.check(user, "game.move", 20.0, 4.0);
               sendJson(ex, 200, this.games.move(me, jsonBody(ex)));
               break;
            case "POST /game/end":
               this.limiter.check(user, "game.end", 10.0, 1.0);
               sendJson(ex, 200, this.games.end(me, jsonBody(ex)));
               break;
            case "POST /paint":
               this.limiter.check(user, "paint.add", 10.0, 0.05);
               sendJson(ex, 200, Map.of("id", this.paint.store(readBody(ex, PaintStore.MAX_BYTES))));
               break;
            case "POST /chat/send":
               this.limiter.check(user, "chat.send", 10.0, 1.0);
               sendJson(ex, 200, this.chat.send(me, jsonBody(ex)));
               break;
            case "GET /chat/history":
               this.limiter.check(user, "chat.history", 20.0, 2.0);
               sendJson(ex, 200, this.chat.history(me, query(ex.getRequestURI()).getOrDefault("with", "")));
               break;
            case "GET /chat/unread":
               this.limiter.check(user, "chat.unread", 10.0, 0.5);
               sendJson(ex, 200, this.chat.unread(me));
               break;
            case "POST /chat/image":
               this.limiter.check(user, "chat.image", 6.0, 0.05);
               sendJson(ex, 200, Map.of("file", this.chatImages.storeFile(readBody(ex, PaintStore.CHAT_MAX_BYTES))));
               break;
            case "POST /clap/cancel":
               this.claps.cancel(me);
               sendJson(ex, 200, Map.of("ok", true));
               break;
            default:
               throw new ApiException(404, "Unbekannter Endpunkt");
         }
      }
   }

   /** Steht der andere Spieler in meiner Tab-Liste (zuletzt per /presence gemeldet)? */
   private boolean nearby(Session me, UUID other) {
      Set<String> near = me.near();
      if (near.isEmpty()) {
         return false;
      } else {
         for (Session s : this.auth.all()) {
            if (s.uuid().equals(other) && s.isOnline()) {
               return near.contains(s.name().toLowerCase(Locale.ROOT));
            }
         }

         return false;
      }
   }

   private void dropImages(UUID creator, String server) {
      for (ImageStore.Image old : this.images.removeAllOf(creator, server)) {
         this.broadcastToServer(old.server(), "image_removed", Map.of("id", old.id()));
      }

      // Stream-Bildschirme des Spielers verschwinden ebenfalls
      for (StreamStore.Screen old : this.streams.removeAllOf(creator, server)) {
         this.broadcastToServer(old.server(), "stream_removed", Map.of("id", old.id()));
      }
   }

   private static Map<String, Object> presence(Map<String, Object> body) {
      Map<String, Object> p = new LinkedHashMap<>();
      boolean pet = Json.bool(body, "pet");
      p.put("pet", pet);
      if (pet) {
         Object skin = body.get("skin");
         Object dance = body.get("dance");
         p.put("skin", skin instanceof String sk && sk.length() <= 32 ? sk : "classic");
         p.put("dance", dance instanceof String d && d.length() <= 32 ? d : "none");
         p.put("speed", Json.num(body, "speed", 0.0, 10.0, 1.0));
         p.put("size", Json.num(body, "size", 0.0, 10.0, 0.8));
         p.put("radius", Json.num(body, "radius", 0.0, 32.0, 4.0));
         p.put("lift", Json.num(body, "lift", 0.0, 2.0, 0.0));
         p.put("shadow", !Boolean.FALSE.equals(body.get("shadow")));
      }

      if (body.get("wings") instanceof String wg && !wg.isEmpty() && wg.length() <= 48) {
         p.put("wings", wg);
         p.put("wingsSize", Json.num(body, "wingsSize", 0.4, 1.4, 0.8));
      }

      // allgemeine Kosmetik (Hüte, Auren …): kleine Schlüssel/Wert-Liste, wird nur durchgereicht
      if (body.get("cos") instanceof Map<?, ?> cos) {
         Map<String, Object> out = new LinkedHashMap<>();

         for (Map.Entry<?, ?> e : cos.entrySet()) {
            if (out.size() >= 24) {
               break;
            }

            if (e.getKey() instanceof String k && k.matches("[a-z_]{1,20}") && e.getValue() instanceof String v && !v.isEmpty() && v.length() <= 64) {
               out.put(k, v);
            }
         }

         if (!out.isEmpty()) {
            p.put("cos", out);
         }
      }

      // AFK-Szene läuft (nur angezeigt, wenn der Spieler das teilt)
      if (Boolean.TRUE.equals(body.get("afk"))) {
         p.put("afk", true);
      }

      // Profilbild statt Meowl-Zeichen: kleines PNG als Base64
      if (body.get("pfp") instanceof String pf && !pf.isEmpty() && pf.length() <= 4096 && pf.matches("[A-Za-z0-9+/=]+")) {
         p.put("pfp", pf);
      }

      if (body.get("watch") instanceof String w && !w.isEmpty() && w.length() <= 32) {
         p.put("watch", w);
         p.put("watchOff", Boolean.TRUE.equals(body.get("watchOff")));
         p.put("watchSize", Json.num(body, "watchSize", 0.6, 1.6, 1.0));
         p.put("watchPos", Json.num(body, "watchPos", -3.0, 1.5, 0.0));
         if (body.get("watchColor") instanceof String wc && wc.matches("[0-9A-Fa-f]{6}")) {
            p.put("watchColor", wc);
         }
      }

      return p;
   }

   private final Map<UUID, MeowlServer.ActiveEmote> emotes = new java.util.concurrent.ConcurrentHashMap<>();

   private record ActiveEmote(String id, String server, boolean loop, long since, String name) {
   }

   private void broadcastToServer(String mcServer, String type, Object data) {
      for (Session s : this.auth.all()) {
         if (s.isOnline() && s.mcServer().equals(mcServer)) {
            this.events.send(s.uuid(), type, data);
         }
      }
   }

   private static String firstSegments(String path) {
      String[] parts = path.split("/");
      if (parts.length < 3 || !parts[1].equals("images") && !parts[1].equals("streams") && !parts[1].equals("events")) {
         return parts.length >= 3 ? "/" + parts[1] + "/" + parts[2] : path;
      } else {
         return "/" + parts[1];
      }
   }

   private static String lastSegment(String path) {
      return path.substring(path.lastIndexOf(47) + 1);
   }

   private static String normalizeServer(String s) {
      return canonicalServer(s);
   }

   static String canonicalServer(String address) {
      String n = address.trim().toLowerCase(Locale.ROOT);
      if (n.endsWith(".")) {
         n = n.substring(0, n.length() - 1);
      }

      if (n.endsWith(":25565")) {
         n = n.substring(0, n.length() - 6);
      }

      String host = n;
      String port = "";
      int colon = n.lastIndexOf(58);
      if (colon > 0 && n.indexOf(58) == colon) {
         host = n.substring(0, colon);
         port = n.substring(colon);
      }

      if (!host.matches("[0-9.]+") && !host.contains(":")) {
         String[] parts = host.split("\\.");
         if (parts.length > 2) {
            host = parts[parts.length - 2] + "." + parts[parts.length - 1];
         }
      }

      String result = host + port;
      return result.length() > 128 ? result.substring(0, 128) : result;
   }

   private static Map<String, String> query(URI uri) {
      Map<String, String> map = new LinkedHashMap<>();
      String q = uri.getRawQuery();
      if (q != null) {
         for (String part : q.split("&")) {
            int eq = part.indexOf(61);
            if (eq > 0) {
               map.put(part.substring(0, eq), URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
            }
         }
      }

      return map;
   }

   private static byte[] readBody(HttpExchange ex, int limit) throws IOException {
      byte[] var4;
      try (InputStream in = ex.getRequestBody()) {
         byte[] data = in.readNBytes(limit + 1);
         if (data.length > limit) {
            throw new ApiException(413, "Anfrage zu groß");
         }

         var4 = data;
      }

      return var4;
   }

   private static Map<String, Object> jsonBody(HttpExchange ex) throws IOException {
      return Json.parseObject(new String(readBody(ex, 16384), StandardCharsets.UTF_8));
   }

   private static void sendJson(HttpExchange ex, int status, Object body) {
      try {
         byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
         ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
         ex.sendResponseHeaders(status, bytes.length);

         try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
         }
      } catch (IOException var9) {
      }
   }
}
