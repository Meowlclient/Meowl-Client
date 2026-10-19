package com.lennart.meowlserver.auth;

import com.lennart.meowlserver.ApiException;
import com.lennart.meowlserver.Json;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class AuthService {
   private static final Logger LOG = Logger.getLogger("Auth");
   private static final long CHALLENGE_TTL_MS = 60000L;
   private static final long SESSION_TTL_MS = 86400000L;
   private final boolean offlineAuth;
   private final SecureRandom random = new SecureRandom();
   private final Map<String, AuthService.Challenge> challenges = new ConcurrentHashMap<>();
   private final Map<String, Session> sessions = new ConcurrentHashMap<>();
   private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).build();

   public AuthService(boolean offlineAuth) {
      this.offlineAuth = offlineAuth;
      if (offlineAuth) {
         LOG.warning("OFFLINE-AUTH AKTIV: Spieler werden NICHT bei Mojang geprüft. Nur für Tests verwenden!");
      }
   }

   public boolean isOfflineAuth() {
      return this.offlineAuth;
   }

   private static void checkName(String name) {
      if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) {
         throw new ApiException(400, "Ungültiger Spielername");
      }
   }

   public String challenge(String name) {
      checkName(name);
      byte[] bytes = new byte[16];
      this.random.nextBytes(bytes);
      String serverId = HexFormat.of().formatHex(bytes);
      this.challenges.put(name.toLowerCase(), new AuthService.Challenge(serverId, name, System.currentTimeMillis() + 60000L));
      return serverId;
   }

   public Session verify(String name, String serverId, String offlineUuid) {
      checkName(name);
      AuthService.Challenge c = this.challenges.remove(name.toLowerCase());
      if (c != null && c.expires >= System.currentTimeMillis() && c.serverId.equals(serverId)) {
         UUID uuid;
         String realName;
         if (this.offlineAuth) {
            uuid = offlineUuid != null ? UUID.fromString(offlineUuid) : UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            realName = name;
         } else {
            Map<String, Object> profile = this.hasJoined(name, serverId);
            String id = (String)profile.get("id");
            uuid = UUID.fromString(id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
            realName = (String)profile.get("name");
         }

         byte[] bytes = new byte[32];
         this.random.nextBytes(bytes);
         String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
         this.sessions.values().removeIf(s -> s.uuid().equals(uuid));
         Session session = new Session(token, uuid, realName, System.currentTimeMillis() + 86400000L);
         this.sessions.put(token, session);
         LOG.info("Angemeldet: " + realName + " (" + uuid + ")");
         return session;
      } else {
         throw new ApiException(401, "Challenge ungültig oder abgelaufen");
      }
   }

   private Map<String, Object> hasJoined(String name, String serverId) {
      String url = "https://sessionserver.mojang.com/session/minecraft/hasJoined?username="
         + URLEncoder.encode(name, StandardCharsets.UTF_8)
         + "&serverId="
         + URLEncoder.encode(serverId, StandardCharsets.UTF_8);

      try {
         HttpResponse<String> r = this.http
            .send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10L)).GET().build(), BodyHandlers.ofString());
         if (r.statusCode() == 200 && !r.body().isBlank()) {
            return Json.parseObject(r.body());
         } else {
            throw new ApiException(401, "Mojang hat die Anmeldung nicht bestätigt");
         }
      } catch (IOException var5) {
         throw new ApiException(503, "Mojang nicht erreichbar");
      } catch (InterruptedException var61) {
         Thread.currentThread().interrupt();
         throw new ApiException(503, "Unterbrochen");
      }
   }

   public Session require(String authorizationHeader) {
      if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
         Session s = this.sessions.get(authorizationHeader.substring(7).trim());
         if (s != null && s.expiresAt() >= System.currentTimeMillis()) {
            s.touch();
            return s;
         } else {
            throw new ApiException(401, "Sitzung abgelaufen");
         }
      } else {
         throw new ApiException(401, "Nicht angemeldet");
      }
   }

   public Session byUuid(UUID uuid) {
      for (Session s : this.sessions.values()) {
         if (s.uuid().equals(uuid)) {
            return s;
         }
      }

      return null;
   }

   public Iterable<Session> all() {
      return this.sessions.values();
   }

   public void logout(Session session) {
      this.sessions.remove(session.token());
   }

   public void cleanup() {
      long now = System.currentTimeMillis();
      this.challenges.values().removeIf(c -> c.expires < now);
      this.sessions.values().removeIf(s -> s.expiresAt() < now);
   }

   private record Challenge(String serverId, String name, long expires) {
   }
}
