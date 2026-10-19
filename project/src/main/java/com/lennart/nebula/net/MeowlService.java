package com.lennart.nebula.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.update.Version;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ServerData;

public final class MeowlService {
   public static final String CLIENT_VERSION = FabricLoader.getInstance()
      .getModContainer("nebula")
      .map(m -> m.getMetadata().getVersion().getFriendlyString())
      .orElse("0.0.0")
      .replaceAll("[^0-9A-Za-z.+-]", "");
   private static final MeowlService INSTANCE = new MeowlService();
   private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).build();
   private final Map<String, List<Consumer<JsonObject>>> listeners = new ConcurrentHashMap<>();
   private final List<Runnable> connectListeners = new CopyOnWriteArrayList<>();
   private volatile String baseUrl;
   private volatile String token;
   private volatile String myUuid;
   private volatile MeowlService.Status status = MeowlService.Status.DISCONNECTED;
   private volatile String statusText = "Nicht verbunden";
   private volatile Thread pollThread;
   private volatile boolean running;
   public static final String MIN_SERVER_VERSION = "1.9.0";
   private volatile String serverVersion;
   private volatile String discordAppId;

   private MeowlService() {
   }

   public static MeowlService get() {
      return INSTANCE;
   }

   public MeowlService.Status status() {
      return this.status;
   }

   public String statusText() {
      return this.statusText;
   }

   public boolean isConnected() {
      return this.status == MeowlService.Status.CONNECTED && this.token != null;
   }

   public String discordAppId() {
      return this.discordAppId;
   }

   public String serverVersion() {
      return this.serverVersion;
   }

   public boolean serverOutdated() {
      Version have = this.serverVersion == null ? null : Version.parse(this.serverVersion);
      Version need = Version.parse(MIN_SERVER_VERSION);
      return have != null && need != null && need.isNewerThan(have);
   }

   public String myUuid() {
      return this.myUuid;
   }

   public void on(String type, Consumer<JsonObject> listener) {
      this.listeners.computeIfAbsent(type, t -> new CopyOnWriteArrayList<>()).add(listener);
   }

   public void onConnected(Runnable listener) {
      this.connectListeners.add(listener);
   }

   public String baseUrl() {
      return this.baseUrl;
   }

   public static boolean isValidUrl(String url) {
      if (url != null && !url.isBlank() && url.length() <= 200) {
         try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("https") || scheme.equals("http")) && uri.getHost() != null && uri.getUserInfo() == null;
         } catch (Exception var31) {
            return false;
         }
      } else {
         return false;
      }
   }

   public synchronized void connect(String url) {
      this.disconnect();
      if (!isValidUrl(url)) {
         this.setStatus(MeowlService.Status.ERROR, "Ungültige Server-Adresse");
      } else {
         this.baseUrl = url.trim().replaceAll("/+$", "");
         this.running = true;
         this.setStatus(MeowlService.Status.CONNECTING, "Verbinde …");
         Thread t = new Thread(this::runLoop, "Meowl-Service");
         t.setDaemon(true);
         this.pollThread = t;
         t.start();
      }
   }

   public synchronized void disconnect() {
      this.running = false;
      Thread t = this.pollThread;
      this.pollThread = null;
      if (t != null) {
         t.interrupt();
      }

      String oldToken = this.token;
      String oldBase = this.baseUrl;
      this.token = null;
      if (oldToken != null && oldBase != null) {
         HttpRequest req = HttpRequest.newBuilder(URI.create(oldBase + "/auth/logout"))
            .timeout(Duration.ofSeconds(5L))
            .header("Authorization", "Bearer " + oldToken)
            .POST(BodyPublishers.noBody())
            .build();
         this.http.sendAsync(req, BodyHandlers.discarding()).exceptionally(e -> null);
      }

      this.setStatus(MeowlService.Status.DISCONNECTED, "Nicht verbunden");
   }

   private void setStatus(MeowlService.Status s, String text) {
      this.status = s;
      this.statusText = text;
   }

   private void runLoop() {
      long backoff = 2000L;
      long since = -1L;
      Thread me = Thread.currentThread();

      while (this.running && this.pollThread == me) {
         try {
            if (this.token == null) {
               String[] result = this.login();
               if (this.pollThread != me) {
                  return;
               }

               this.token = result[0];
               this.myUuid = result[1];
               this.setStatus(MeowlService.Status.CONNECTED, result[2]);
               since = -1L;
               backoff = 2000L;
               Minecraft.getInstance().execute(() -> this.connectListeners.forEach(Runnable::run));
            }

            MeowlService.Response r = this.requestBlocking("GET", "/events?since=" + since + "&wait=25", null, null, Duration.ofSeconds(35L));
            if (r.status == 401) {
               this.token = null;
               this.setStatus(MeowlService.Status.CONNECTING, "Sitzung abgelaufen – melde neu an …");
            } else {
               if (!r.ok() || r.json == null) {
                  throw new IllegalStateException(r.error());
               }

               since = r.json.get("latest").getAsLong();

               for (JsonElement el : r.json.getAsJsonArray("events")) {
                  JsonObject ev = el.getAsJsonObject();
                  this.dispatch(ev.get("type").getAsString(), ev.get("data").isJsonObject() ? ev.getAsJsonObject("data") : new JsonObject());
               }
            }
         } catch (InterruptedException var11) {
            return;
         } catch (Exception var12) {
            if (!this.running || this.pollThread != me) {
               return;
            }

            this.token = null;
            this.setStatus(MeowlService.Status.ERROR, "Fehler: " + var12.getMessage() + " – neuer Versuch …");
            NebulaClient.LOGGER.warn("Meowl Service: {}", var12.toString());

            try {
               Thread.sleep(backoff);
            } catch (InterruptedException var10) {
               return;
            }

            backoff = Math.min(60000L, backoff * 2L);
         }
      }
   }

   private String[] login() throws Exception {
      Minecraft mc = Minecraft.getInstance();
      User session = mc.getUser();
      String name = session.getName();
      MeowlService.Response status = this.requestBlocking("GET", "/status", null, null, Duration.ofSeconds(10L));
      if (status.ok() && status.json != null) {
         boolean offlineAuth = status.json.has("offlineAuth") && status.json.get("offlineAuth").getAsBoolean();
         this.serverVersion = status.json.has("version") ? status.json.get("version").getAsString() : null;
         this.discordAppId = status.json.has("discordAppId") ? status.json.get("discordAppId").getAsString() : null;
         JsonObject body = new JsonObject();
         body.addProperty("name", name);
         MeowlService.Response challenge = this.requestBlocking("POST", "/auth/challenge", body, null, Duration.ofSeconds(10L));
         if (!challenge.ok()) {
            throw new IllegalStateException(challenge.error());
         } else {
            String serverId = challenge.json.get("serverId").getAsString();
            if (!offlineAuth) {
               mc.services().sessionService().joinServer(session.getProfileId(), session.getAccessToken(), serverId);
            }

            JsonObject verify = new JsonObject();
            verify.addProperty("name", name);
            verify.addProperty("serverId", serverId);
            if (session.getProfileId() != null) {
               verify.addProperty("uuid", session.getProfileId().toString());
            }

            MeowlService.Response result = this.requestBlocking("POST", "/auth/verify", verify, null, Duration.ofSeconds(15L));
            if (!result.ok()) {
               throw new IllegalStateException(result.error());
            } else {
               return new String[]{
                  result.json.get("token").getAsString(),
                  result.json.get("uuid").getAsString(),
                  "Verbunden"
                     + (this.serverVersion != null ? " · Server " + this.serverVersion : "")
                     + (this.serverOutdated() ? " – veraltet, bitte Server-Update einspielen (.bat erneut starten)" : "")
                     + (offlineAuth ? " (Server im Test-Modus ohne Mojang-Prüfung!)" : "")
               };
            }
         }
      } else {
         throw new IllegalStateException("Server antwortet nicht (" + status.error() + ")");
      }
   }

   private void dispatch(String type, JsonObject data) {
      List<Consumer<JsonObject>> list = this.listeners.get(type);
      if (list != null) {
         Minecraft.getInstance().execute(() -> {
            for (Consumer<JsonObject> l : list) {
               try {
                  l.accept(data);
               } catch (RuntimeException var6) {
                  NebulaClient.LOGGER.warn("Meowl Service: Listener für {} fehlgeschlagen", type, var6);
               }
            }
         });
      }
   }

   public static String currentServerId() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.isLocalServer() && mc.getSingleplayerServer() != null) {
         return "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName().toLowerCase(Locale.ROOT);
      } else {
         ServerData info = mc.getCurrentServer();
         return info == null ? "" : canonicalServer(info.ip);
      }
   }

   public static String canonicalServer(String address) {
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

   private Builder builder(String path, Duration timeout) {
      Builder b = HttpRequest.newBuilder(URI.create(this.baseUrl + path))
         .timeout(timeout)
         .header("User-Agent", "MeowlClient")
         .header("X-Meowl-Client", CLIENT_VERSION);
      String server = currentServerId();
      if (!server.isEmpty()) {
         b.header("X-Meowl-Server", server);
      }

      if (this.token != null) {
         b.header("Authorization", "Bearer " + this.token);
      }

      return b;
   }

   private MeowlService.Response requestBlocking(String method, String path, JsonObject json, byte[] raw, Duration timeout) throws Exception {
      HttpResponse<byte[]> r = this.http.send(this.build(method, path, json, raw, timeout), BodyHandlers.ofByteArray());
      return toResponse(r);
   }

   private HttpRequest build(String method, String path, JsonObject json, byte[] raw, Duration timeout) {
      Builder b = this.builder(path, timeout);
      if (json != null) {
         b.header("Content-Type", "application/json").method(method, BodyPublishers.ofString(json.toString()));
      } else if (raw != null) {
         b.header("Content-Type", "image/jpeg").method(method, BodyPublishers.ofByteArray(raw));
      } else {
         b.method(method, BodyPublishers.noBody());
      }

      return b.build();
   }

   private static MeowlService.Response toResponse(HttpResponse<byte[]> r) {
      JsonObject json = null;
      String type = r.headers().firstValue("Content-Type").orElse("");
      if (type.startsWith("application/json") && r.body().length > 0) {
         try {
            JsonElement el = JsonParser.parseString(new String(r.body(), StandardCharsets.UTF_8));
            json = el.isJsonObject() ? el.getAsJsonObject() : null;
         } catch (RuntimeException var4) {
         }
      }

      return new MeowlService.Response(r.statusCode(), json, r.body(), r.headers().map());
   }

   public CompletableFuture<MeowlService.Response> request(String method, String path, JsonObject json, byte[] raw) {
      return !this.isConnected()
         ? CompletableFuture.failedFuture(new IllegalStateException("Nicht mit dem Meowl Server verbunden"))
         : this.http.sendAsync(this.build(method, path, json, raw, Duration.ofSeconds(10L)), BodyHandlers.ofByteArray()).thenApply(MeowlService::toResponse);
   }

   public CompletableFuture<MeowlService.Response> post(String path, JsonObject json) {
      return this.request("POST", path, json, null);
   }

   public CompletableFuture<MeowlService.Response> get(String path) {
      return this.request("GET", path, null, null);
   }

   public record Response(int status, JsonObject json, byte[] bytes, Map<String, List<String>> headers) {
      public boolean ok() {
         return this.status >= 200 && this.status < 300;
      }

      public String error() {
         return this.json != null && this.json.has("error") ? this.json.get("error").getAsString() : "HTTP " + this.status;
      }
   }

   public static enum Status {
      DISCONNECTED,
      CONNECTING,
      CONNECTED,
      ERROR;
   }
}
