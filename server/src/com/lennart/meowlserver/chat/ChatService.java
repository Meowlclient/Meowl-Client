package com.lennart.meowlserver.chat;

import com.lennart.meowlserver.ApiException;
import com.lennart.meowlserver.Json;
import com.lennart.meowlserver.auth.Session;
import com.lennart.meowlserver.events.EventHub;
import com.lennart.meowlserver.friends.FriendService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Private Nachrichten zwischen gegenseitigen Meowl-Freunden: Text und Bilder (Links auf Block Images,
 * hochgeladene Screenshots). Verlauf pro Paar (letzte 200) liegt in data/chat/*.jsonl, ungelesene Nachrichten
 * werden gezählt, damit man sie auch nach dem Offline-Sein sieht.
 */
public final class ChatService {
   private static final Logger LOG = Logger.getLogger("Chat");
   private static final int KEEP = 200;
   private final Path dir;
   private final FriendService friends;
   private final EventHub events;
   private final Map<String, Deque<Map<String, Object>>> history = new HashMap<>();
   private final Map<UUID, Map<String, Integer>> unread = new HashMap<>();

   public ChatService(Path dataDir, FriendService friends, EventHub events) {
      this.dir = dataDir.resolve("chat");
      this.friends = friends;
      this.events = events;

      try {
         Files.createDirectories(this.dir);
      } catch (IOException var5) {
      }
   }

   private static String pair(UUID a, UUID b) {
      return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
   }

   private UUID friend(Session me, String name) {
      UUID other = this.friends.findByName(name);
      if (other == null) {
         throw new ApiException(404, name + " ist nicht bei Meowl angemeldet");
      } else if (!this.friends.mutual(me.uuid(), other)) {
         throw new ApiException(403, "Schreiben geht nur mit gegenseitigen Freunden");
      } else {
         return other;
      }
   }

   private Deque<Map<String, Object>> load(String key) {
      return this.history.computeIfAbsent(key, k -> {
         Deque<Map<String, Object>> d = new ArrayDeque<>();
         Path f = this.dir.resolve(k + ".jsonl");

         try {
            if (Files.exists(f)) {
               List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);

               for (String line : lines.subList(Math.max(0, lines.size() - KEEP), lines.size())) {
                  if (!line.isBlank()) {
                     d.addLast(Json.parseObject(line));
                  }
               }

               if (lines.size() > KEEP * 2) {
                  // Datei gelegentlich kürzen
                  List<String> keep = new ArrayList<>();
                  d.forEach(m -> keep.add(Json.write(m)));
                  Files.write(f, keep, StandardCharsets.UTF_8);
               }
            }
         } catch (IOException | RuntimeException var6) {
            LOG.warning("Chat-Verlauf " + k + " nicht lesbar: " + var6);
         }

         return d;
      });
   }

   public synchronized Map<String, Object> send(Session me, Map<String, Object> body) {
      String toName = Json.str(body, "to", 16);
      UUID to = this.friend(me, toName);
      String text = body.get("text") instanceof String t ? t.strip() : "";
      String image = body.get("image") instanceof String i ? i.strip() : "";
      if (text.length() > 300) {
         throw new ApiException(400, "Nachricht zu lang (max. 300 Zeichen)");
      } else if (image.length() > 512 || !image.isEmpty() && !image.startsWith("https://")) {
         throw new ApiException(400, "Ungültiger Bild-Link");
      } else if (text.isEmpty() && image.isEmpty()) {
         throw new ApiException(400, "Leere Nachricht");
      }

      Map<String, Object> msg = new LinkedHashMap<>();
      msg.put("id", UUID.randomUUID().toString());
      msg.put("from", me.name());
      msg.put("to", this.friends.nameOf(to));
      msg.put("time", System.currentTimeMillis());
      if (!text.isEmpty()) {
         msg.put("text", text);
      }

      if (!image.isEmpty()) {
         msg.put("image", image);
      }

      String key = pair(me.uuid(), to);
      Deque<Map<String, Object>> d = this.load(key);
      d.addLast(msg);

      while (d.size() > KEEP) {
         d.removeFirst();
      }

      try {
         Files.writeString(this.dir.resolve(key + ".jsonl"), Json.write(msg) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      } catch (IOException var10) {
         LOG.warning("Chat konnte nicht gespeichert werden: " + var10);
      }

      this.unread.computeIfAbsent(to, k -> new HashMap<>()).merge(me.name().toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
      this.events.send(to, "chat_msg", msg);
      return msg;
   }

   public synchronized Map<String, Object> history(Session me, String withName) {
      UUID other = this.friend(me, withName);
      List<Object> list = new ArrayList<>(this.load(pair(me.uuid(), other)));
      Map<String, Integer> mine = this.unread.get(me.uuid());
      if (mine != null) {
         mine.remove(this.friends.nameOf(other).toLowerCase(java.util.Locale.ROOT));
      }

      return Map.of("messages", list);
   }

   public synchronized Map<String, Object> unread(Session me) {
      return Map.of("unread", new LinkedHashMap<>(this.unread.getOrDefault(me.uuid(), Map.of())));
   }
}
