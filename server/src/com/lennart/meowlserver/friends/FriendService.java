package com.lennart.meowlserver.friends;

import com.lennart.meowlserver.ApiException;
import com.lennart.meowlserver.Json;
import com.lennart.meowlserver.auth.AuthService;
import com.lennart.meowlserver.auth.Session;
import com.lennart.meowlserver.events.EventHub;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.logging.Logger;

public final class FriendService {
   private static final Logger LOG = Logger.getLogger("Friends");
   private static final int MAX_FRIENDS = 100;
   private final Path file;
   private final AuthService auth;
   private final EventHub events;
   private final Map<UUID, Set<UUID>> added = new HashMap<>();
   private final Map<UUID, String> names = new HashMap<>();
   private final Map<UUID, Long> lastLocate = new HashMap<>();

   public FriendService(Path dataDir, AuthService auth, EventHub events) {
      this.file = dataDir.resolve("friends.json");
      this.auth = auth;
      this.events = events;
      this.load();
   }

   private synchronized void load() {
      try {
         if (Files.exists(this.file)) {
            Map<String, Object> root = Json.parseObject(Files.readString(this.file, StandardCharsets.UTF_8));
            if (root.get("names") instanceof Map<?, ?> n) {
               for (Entry<?, ?> e : n.entrySet()) {
                  this.names.put(UUID.fromString(e.getKey().toString()), String.valueOf(e.getValue()));
               }
            }

            if (root.get("added") instanceof Map<?, ?> a) {
               for (Entry<?, ?> e : a.entrySet()) {
                  Set<UUID> set = new LinkedHashSet<>();
                  Object var7 = e.getValue();
                  if (var7 instanceof List) {
                     for (Object o : (List)var7) {
                        set.add(UUID.fromString(o.toString()));
                     }
                  }

                  this.added.put(UUID.fromString(e.getKey().toString()), set);
               }
            }
         }
      } catch (IOException | RuntimeException var9) {
         LOG.warning("friends.json konnte nicht gelesen werden: " + var9);
      }
   }

   private synchronized void save() {
      Map<String, Object> n = new LinkedHashMap<>();
      this.names.forEach((k, v) -> n.put(k.toString(), v));
      Map<String, Object> a = new LinkedHashMap<>();
      this.added.forEach((k, v) -> {
         List<Object> list = new ArrayList<>();
         v.forEach(u -> list.add(u.toString()));
         a.put(k.toString(), list);
      });

      try {
         Path tmp = this.file.resolveSibling("friends.json.tmp");
         Files.writeString(tmp, Json.write(Map.of("names", n, "added", a)), StandardCharsets.UTF_8);
         Files.move(tmp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException var4) {
         LOG.warning("friends.json konnte nicht gespeichert werden: " + var4);
      }
   }

   public synchronized void remember(Session s) {
      String old = this.names.put(s.uuid(), s.name());
      if (!s.name().equals(old)) {
         this.save();
      }
   }

   public synchronized boolean mutual(UUID a, UUID b) {
      return this.added.getOrDefault(a, Set.of()).contains(b) && this.added.getOrDefault(b, Set.of()).contains(a);
   }

   public synchronized String nameOf(UUID uuid) {
      return this.names.getOrDefault(uuid, "?");
   }

   public synchronized UUID findByName(String name) {
      String n = name.trim().toLowerCase(Locale.ROOT);

      for (Session s : this.auth.all()) {
         if (s.name().toLowerCase(Locale.ROOT).equals(n)) {
            return s.uuid();
         }
      }

      for (Entry<UUID, String> e : this.names.entrySet()) {
         if (e.getValue().toLowerCase(Locale.ROOT).equals(n)) {
            return e.getKey();
         }
      }

      return null;
   }

   private boolean online(UUID uuid) {
      Session s = this.auth.byUuid(uuid);
      return s != null && s.isOnline();
   }

   public synchronized Map<String, Object> list(Session me) {
      List<Object> friends = new ArrayList<>();
      List<Object> incoming = new ArrayList<>();

      for (UUID other : this.added.getOrDefault(me.uuid(), Set.of())) {
         Session s = this.auth.byUuid(other);
         Map<String, Object> f = new LinkedHashMap<>();
         f.put("uuid", other.toString());
         f.put("name", this.names.getOrDefault(other, "?"));
         f.put("mutual", this.mutual(me.uuid(), other));
         f.put("online", s != null && s.isOnline());
         f.put("sameServer", s != null && s.isOnline() && !me.mcServer().isEmpty() && me.mcServer().equals(s.mcServer()));
         friends.add(f);
      }

      for (Entry<UUID, Set<UUID>> e : this.added.entrySet()) {
         if (e.getValue().contains(me.uuid()) && !this.added.getOrDefault(me.uuid(), Set.of()).contains(e.getKey())) {
            incoming.add(Map.of("uuid", e.getKey().toString(), "name", this.names.getOrDefault(e.getKey(), "?")));
         }
      }

      return Map.of("friends", friends, "incoming", incoming);
   }

   public synchronized Map<String, Object> add(Session me, Map<String, Object> body) {
      String name = Json.str(body, "name", 16);
      UUID target = this.findByName(name);
      if (target == null) {
         throw new ApiException(404, name + " hat sich noch nie mit Meowl angemeldet");
      } else if (target.equals(me.uuid())) {
         throw new ApiException(400, "Du kannst dich nicht selbst hinzufügen");
      } else {
         Set<UUID> mine = this.added.computeIfAbsent(me.uuid(), k -> new LinkedHashSet<>());
         if (mine.size() >= 100) {
            throw new ApiException(400, "Maximal 100 Freunde");
         } else {
            boolean isNew = mine.add(target);
            this.save();
            boolean mutual = this.mutual(me.uuid(), target);
            if (isNew) {
               this.events.send(target, mutual ? "friend_added" : "friend_request", Map.of("uuid", me.uuid().toString(), "name", me.name()));
            }

            return Map.of("uuid", target.toString(), "name", this.names.getOrDefault(target, name), "mutual", mutual, "online", this.online(target));
         }
      }
   }

   public synchronized Map<String, Object> remove(Session me, Map<String, Object> body) {
      String key = Json.str(body, "name", 40);

      UUID target;
      try {
         target = UUID.fromString(key);
      } catch (IllegalArgumentException var6) {
         target = this.findByName(key);
      }

      if (target == null) {
         throw new ApiException(404, "Nicht gefunden");
      } else {
         boolean was = this.mutual(me.uuid(), target);
         this.added.getOrDefault(me.uuid(), new LinkedHashSet<>()).remove(target);
         this.save();
         if (was) {
            this.events.send(target, "friend_locate_stop", Map.of("uuid", me.uuid().toString()));
         }

         return Map.of("ok", true);
      }
   }

   public Map<String, Object> locate(Session me, Map<String, Object> body) {
      double x = Json.num(body, "x", -3.0E7, 3.0E7);
      double y = Json.num(body, "y", -2048.0, 4096.0);
      double z = Json.num(body, "z", -3.0E7, 3.0E7);
      String dim = Json.str(body, "dim", 64);
      boolean panic = Json.bool(body, "panic");
      int duration = (int)Json.num(body, "duration", 5.0, 600.0, 60.0);
      if (me.mcServer().isEmpty()) {
         throw new ApiException(400, "Nicht auf einem Server");
      } else {
         Map<String, Object> ev = new LinkedHashMap<>();
         ev.put("uuid", me.uuid().toString());
         ev.put("name", me.name());
         ev.put("x", x);
         ev.put("y", y);
         ev.put("z", z);
         ev.put("dim", dim);
         ev.put("panic", panic);
         ev.put("duration", duration);
         int sent = 0;
         // optional: nur an bestimmte Freunde (Namen); fehlt die Liste, an alle gegenseitigen Freunde
         Set<String> only = null;
         if (body.get("to") instanceof List<?> to) {
            only = new java.util.HashSet<>();

            for (Object o : to) {
               if (o instanceof String n && n.length() <= 16 && only.size() < 32) {
                  only.add(n.toLowerCase(java.util.Locale.ROOT));
               }
            }
         }

         List<UUID> targets = new ArrayList<>();
         synchronized (this) {
            for (UUID other : this.added.getOrDefault(me.uuid(), Set.of())) {
               if (this.mutual(me.uuid(), other)) {
                  targets.add(other);
               }
            }

            this.lastLocate.put(me.uuid(), System.currentTimeMillis());
         }

         for (UUID otherx : targets) {
            Session s = this.auth.byUuid(otherx);
            if (s != null
               && s.isOnline()
               && (me.mcServer().equals(s.mcServer()) || sameTab(me, s))
               && (only == null || only.contains(s.name().toLowerCase(java.util.Locale.ROOT)))) {
               this.events.send(otherx, "friend_locate", ev);
               sent++;
            }
         }

         return Map.of("sent", sent);
      }
   }

   /** Sehen sich die beiden gerade gegenseitig in der Tab-Liste? (gleicher Server, auch wenn die Adressen verschieden geschrieben sind) */
   private static boolean sameTab(Session a, Session b) {
      return a.near().contains(b.name().toLowerCase(java.util.Locale.ROOT)) || b.near().contains(a.name().toLowerCase(java.util.Locale.ROOT));
   }

   public Map<String, Object> stop(Session me) {
      List<UUID> targets = new ArrayList<>();
      synchronized (this) {
         for (UUID other : this.added.getOrDefault(me.uuid(), Set.of())) {
            if (this.mutual(me.uuid(), other)) {
               targets.add(other);
            }
         }
      }

      for (UUID otherx : targets) {
         this.events.send(otherx, "friend_locate_stop", Map.of("uuid", me.uuid().toString()));
      }

      return Map.of("ok", true);
   }
}
