package com.lennart.meowlserver.images;

import com.lennart.meowlserver.ApiException;
import com.lennart.meowlserver.Json;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gemeinsame Stream-Bildschirme (Twitch/YouTube) an Wänden: wer einen aufhängt, sieht ihn mit allen Meowl-Spielern auf
 * demselben Minecraft-Server. Nur im Speicher – verschwinden, wenn der Ersteller den Server verlässt.
 */
public final class StreamStore {
   public static final int MAX_PER_CREATOR = 1;
   private static final Set<String> HOSTS = Set.of("twitch.tv", "www.twitch.tv", "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "tiktok.com", "www.tiktok.com", "vm.tiktok.com", "m.tiktok.com", "clips.twitch.tv");
   /** Höchstens so viele gleichzeitige Wiedergaben auf dem Server (Rechenzeit/Bandbreite). */
   private static final int MAX_SESSIONS = 4;
   private final Map<String, StreamStore.Screen> screens = new ConcurrentHashMap<>();
   private final Map<String, WatchSession> sessions = new ConcurrentHashMap<>();

   public StreamStore.Screen screen(String id) {
      return this.screens.get(id);
   }

   public WatchSession session(String id) {
      return this.sessions.get(id);
   }

   private void end(StreamStore.Screen s) {
      WatchSession w = this.sessions.remove(s.id());
      if (w != null) {
         w.stop();
      }
   }

   /** Nur Twitch- und YouTube-Links (https, Standard-Port, ohne Benutzerdaten). */
   public static String validateUrl(String url) {
      if (url == null || url.isBlank() || url.length() > 300) {
         throw new ApiException(400, "Link fehlt oder ist zu lang");
      }

      URI uri;
      try {
         uri = new URI(url.trim());
      } catch (Exception var3) {
         throw new ApiException(400, "Ungültiger Link");
      }

      String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
      if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getPort() != -1 && uri.getPort() != 443 || !HOSTS.contains(host)) {
         throw new ApiException(400, "Nur YouTube-, Twitch- und TikTok-Links");
      }

      return uri.toString();
   }

   private static StreamStore STORE;

   public StreamStore() {
      STORE = this;
   }

   public StreamStore.Screen add(Map<String, Object> body, String server, UUID creator, String name) {
      if (server == null || server.isEmpty()) {
         throw new ApiException(400, "Kein Minecraft-Server gemeldet");
      }

      String url = validateUrl(Json.str(body, "url", 300));
      String dim = Json.str(body, "dim", 64);
      if (!dim.matches("[a-z0-9_.:/-]+")) {
         throw new ApiException(400, "Ungültige Dimension");
      }

      String facing = Json.str(body, "facing", 5).toLowerCase();
      if (!List.of("north", "south", "east", "west").contains(facing)) {
         throw new ApiException(400, "Nur senkrechte Flächen");
      }

      int x = (int)Json.num(body, "x", -3.0E7, 3.0E7);
      int y = (int)Json.num(body, "y", -2048.0, 2048.0);
      int z = (int)Json.num(body, "z", -3.0E7, 3.0E7);
      double w = Json.num(body, "w", 1.0, 16.0);
      if (this.screens.size() >= 5000) {
         throw new ApiException(507, "Zu viele Bildschirme");
      }

      StreamStore.Screen s = new StreamStore.Screen(UUID.randomUUID().toString(), url, server, dim, x, y, z, facing, w, creator, name, System.currentTimeMillis());
      if (this.sessions.size() >= MAX_SESSIONS) {
         throw new ApiException(503, "Gerade laufen schon " + MAX_SESSIONS + " Videos auf dem Server");
      }

      this.screens.put(s.id(), s);
      this.sessions.put(s.id(), new WatchSession(url));
      return s;
   }

   /** Älteste eigene Bildschirme auf diesem Server entfernen, bis Platz für einen neuen ist. */
   public List<StreamStore.Screen> trimFor(UUID creator, String server) {
      List<StreamStore.Screen> mine = new ArrayList<>();
      for (StreamStore.Screen s : this.screens.values()) {
         if (s.creator().equals(creator) && s.server().equals(server)) {
            mine.add(s);
         }
      }

      mine.sort(Comparator.comparingLong(StreamStore.Screen::started));
      List<StreamStore.Screen> removed = new ArrayList<>();
      while (mine.size() - removed.size() >= MAX_PER_CREATOR) {
         StreamStore.Screen old = mine.get(removed.size());
         this.screens.remove(old.id());
         this.end(old);
         removed.add(old);
      }

      return removed;
   }

   public StreamStore.Screen remove(String id, UUID requester) {
      StreamStore.Screen s = this.screens.get(id);
      if (s == null) {
         throw new ApiException(404, "Bildschirm nicht gefunden");
      } else if (!s.creator().equals(requester)) {
         throw new ApiException(403, "Nur wer ihn aufgehängt hat, kann ihn abnehmen");
      }

      this.screens.remove(id);
      this.end(s);
      return s;
   }

   public List<StreamStore.Screen> removeAllOf(UUID creator, String server) {
      List<StreamStore.Screen> removed = new ArrayList<>();
      this.screens.values().removeIf(s -> {
         boolean hit = s.creator().equals(creator) && (server == null || s.server().equals(server));
         if (hit) {
            removed.add(s);
            this.end(s);
         }

         return hit;
      });
      return removed;
   }

   public List<StreamStore.Screen> forServer(String server) {
      List<StreamStore.Screen> out = new ArrayList<>();
      for (StreamStore.Screen s : this.screens.values()) {
         if (s.server().equals(server)) {
            out.add(s);
         }
      }

      return out;
   }

   public record Screen(String id, String url, String server, String dim, int x, int y, int z, String facing, double w, UUID creator, String name, long started) {
      public Map<String, Object> toJson() {
         Map<String, Object> m = new LinkedHashMap<>();
         m.put("id", this.id);
         m.put("url", this.url);
         m.put("dim", this.dim);
         m.put("x", this.x);
         m.put("y", this.y);
         m.put("z", this.z);
         m.put("facing", this.facing);
         m.put("w", this.w);
         m.put("creator", this.creator.toString());
         m.put("name", this.name);
         m.put("started", this.started);
         WatchSession w = STORE == null ? null : STORE.sessions.get(this.id);
         m.put("status", w == null ? "" : w.status());
         m.put("paused", w != null && w.paused());
         // Serverzeit mitschicken, damit Clients die Abspielposition (YouTube) trotz falsch gehender Uhr berechnen können
         m.put("now", System.currentTimeMillis());
         return m;
      }
   }
}
