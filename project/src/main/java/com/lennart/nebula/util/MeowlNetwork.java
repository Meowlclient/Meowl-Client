package com.lennart.nebula.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.module.mods.Watches;
import com.lennart.nebula.module.mods.pet.PetConfig;
import com.lennart.nebula.net.MeowlService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

public final class MeowlNetwork {
   private static final String TOPIC_URL = "https://ntfy.sh/meowl-client-presence-v1";
   private static final int PROTOCOL = 1;
   private static final long HEARTBEAT_MS = 240000L;
   private static final long MIN_PUBLISH_GAP_MS = 10000L;
   private static final long POLL_MS = 30000L;
   private static final long ONLINE_MS = 600000L;
   private static final long SERVER_SYNC_MS = 20000L;
   private static final long RATE_LIMIT_PAUSE_MS = 1800000L;
   private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8L)).build();
   private final Map<UUID, MeowlNetwork.Peer> peers = new ConcurrentHashMap<>();
   private volatile boolean polling;
   private volatile boolean publishing;
   private long lastPoll;
   private long lastPublish;
   private String lastPayload;
   private boolean firstPoll = true;
   private volatile long publishBlockedUntil;
   private volatile boolean serverSyncing;
   private long lastServerSync;

   public void tick(UUID self, String name, boolean petEnabled, PetConfig pet) {
      long now = System.currentTimeMillis();
      String payload = payload(self, name, petEnabled, pet, false);
      boolean changed = !payload.equals(this.lastPayload);
      MeowlService service = MeowlService.get();
      if (service.isConnected() && !this.serverSyncing && (now - this.lastServerSync >= 20000L || changed && now - this.lastServerSync >= 3000L)) {
         this.lastServerSync = now;
         this.serverSync(service, self, petEnabled, pet);
      }

      if (!this.publishing && now >= this.publishBlockedUntil && (changed && now - this.lastPublish >= 10000L || now - this.lastPublish >= 240000L)) {
         this.lastPublish = now;
         this.lastPayload = payload;
         this.publish(payload);
      }

      if (!this.polling && now - this.lastPoll >= 30000L) {
         this.lastPoll = now;
         this.poll(self);
      }
   }

   public void goOffline(UUID self, String name) {
      if (this.lastPayload != null) {
         this.publish(payload(self, name, false, null, true));
      }

      this.lastPayload = null;
      this.lastPublish = 0L;
      this.lastPoll = 0L;
      this.firstPoll = true;
      this.peers.clear();
   }

   public MeowlNetwork.Peer get(UUID uuid) {
      MeowlNetwork.Peer peer = this.peers.get(uuid);
      return alive(peer) ? peer : null;
   }

   public MeowlNetwork.Peer get(UUID uuid, String name) {
      MeowlNetwork.Peer peer = this.get(uuid);
      if (peer == null && name != null) {
         for (MeowlNetwork.Peer p : this.peers.values()) {
            if (alive(p) && name.equalsIgnoreCase(p.name())) {
               return p;
            }
         }
      }

      return peer;
   }

   private static boolean alive(MeowlNetwork.Peer peer) {
      return peer != null && !peer.offline() && System.currentTimeMillis() - peer.lastSeenMs() <= 600000L;
   }

   private static String payload(UUID self, String name, boolean petEnabled, PetConfig pet, boolean offline) {
      JsonObject o = new JsonObject();
      o.addProperty("v", 1);
      o.addProperty("uuid", self.toString());
      o.addProperty("name", name);
      o.addProperty("off", offline);
      o.addProperty("pet", petEnabled && pet != null);
      if (pet != null) {
         o.addProperty("skin", pet.skin());
         o.addProperty("dance", pet.dance());
         o.addProperty("speed", pet.speed());
         o.addProperty("size", pet.size());
         o.addProperty("radius", pet.radius());
         o.addProperty("lift", pet.lift());
         o.addProperty("shadow", pet.shadow());
      }

      if (!offline) {
         Watches.presenceFields(o);
         com.lennart.nebula.module.mods.Wings.presenceFields(o);
      com.lennart.nebula.cosmetic.Cosmetics.presenceFields(o);
      com.lennart.nebula.module.mods.DesertAfk.presenceFields(o);
      com.lennart.nebula.module.mods.ProfilePicture.presenceFields(o);
      }

      return o.toString();
   }

   private void publish(String payload) {
      this.publishing = true;
      HttpRequest request = HttpRequest.newBuilder(URI.create("https://ntfy.sh/meowl-client-presence-v1"))
         .timeout(Duration.ofSeconds(10L))
         .header("User-Agent", "MeowlClient")
         .POST(BodyPublishers.ofString(payload))
         .build();
      this.http.sendAsync(request, BodyHandlers.discarding()).whenComplete((response, error) -> {
         this.publishing = false;
         if (response != null && response.statusCode() == 429) {
            this.publishBlockedUntil = System.currentTimeMillis() + 1800000L;
            NebulaClient.LOGGER.info("Meowl-Netzwerk: ntfy.sh-Limit erreicht, Pause 30 min (Meowl Server läuft weiter)");
         }

         if (error != null) {
            NebulaClient.LOGGER.debug("Meowl-Netzwerk: Senden fehlgeschlagen: {}", error.toString());
         }
      });
   }

   private void poll(UUID self) {
      this.polling = true;
      String since = this.firstPoll ? "12m" : "6m";
      HttpRequest request = HttpRequest.newBuilder(URI.create("https://ntfy.sh/meowl-client-presence-v1/json?poll=1&since=" + since))
         .timeout(Duration.ofSeconds(10L))
         .header("User-Agent", "MeowlClient")
         .GET()
         .build();
      this.http.sendAsync(request, BodyHandlers.ofLines()).whenComplete((response, error) -> {
         try {
            if (error != null || response.statusCode() != 200) {
               return;
            }

            this.firstPoll = false;
            response.body().forEach(line -> this.handleLine(line, self));
         } catch (RuntimeException var8) {
            NebulaClient.LOGGER.debug("Meowl-Netzwerk: Antwort unlesbar: {}", var8.toString());
            return;
         } finally {
            this.polling = false;
         }
      });
   }

   private void serverSync(MeowlService service, UUID self, boolean petEnabled, PetConfig pet) {
      this.serverSyncing = true;
      JsonObject o = new JsonObject();
      o.addProperty("pet", petEnabled && pet != null);
      if (petEnabled && pet != null) {
         o.addProperty("skin", pet.skin());
         o.addProperty("dance", pet.dance());
         o.addProperty("speed", pet.speed());
         o.addProperty("size", pet.size());
         o.addProperty("radius", pet.radius());
         o.addProperty("lift", pet.lift());
         o.addProperty("shadow", pet.shadow());
      }

      Watches.presenceFields(o);
      com.lennart.nebula.module.mods.Wings.presenceFields(o);
      com.lennart.nebula.cosmetic.Cosmetics.presenceFields(o);
      com.lennart.nebula.module.mods.DesertAfk.presenceFields(o);
      com.lennart.nebula.module.mods.ProfilePicture.presenceFields(o);
      JsonArray near = new JsonArray();
      Minecraft mc = Minecraft.getInstance();
      if (mc.getConnection() != null) {
         for (PlayerInfo e : mc.getConnection().getOnlinePlayers()) {
            if (near.size() >= 150) {
               break;
            }

            String n = e.getProfile().name();
            if (n != null && n.matches("[A-Za-z0-9_]{1,16}")) {
               near.add(n);
            }
         }
      }

      o.add("near", near);
      service.post("/presence", o)
         .whenComplete(
            (r, ex) -> {
               this.serverSyncing = false;
               if (ex == null && r != null && r.ok() && r.json() != null && r.json().has("players")) {
                  long now = System.currentTimeMillis();

                  for (JsonElement el : r.json().getAsJsonArray("players")) {
                     try {
                        JsonObject p = el.getAsJsonObject();
                        UUID uuid = UUID.fromString(p.get("uuid").getAsString());
                        if (!uuid.equals(self)) {
                           boolean petOn = p.has("pet") && p.get("pet").getAsBoolean();
                           PetConfig cfg = petOn
                              ? new PetConfig(
                                    str(p, "skin"),
                                    str(p, "dance"),
                                    num(p, "speed", 1.0),
                                    num(p, "size", 0.8),
                                    num(p, "radius", 4.0),
                                    !p.has("shadow") || p.get("shadow").getAsBoolean(),
                                    num(p, "lift", 0.0)
                                 )
                                 .sanitized()
                              : null;
                           String name = p.has("name") ? p.get("name").getAsString() : "?";
                           this.peers
                              .put(
                                 uuid,
                                 new MeowlNetwork.Peer(
                                    uuid, name.length() > 16 ? name.substring(0, 16) : name, petOn, cfg, now, false, MeowlNetwork.Watch.of(p), MeowlNetwork.WingsInfo.of(p), cosOf(p),
                                    p.has("afk") && p.get("afk").getAsBoolean(), pfpOf(p)
                                 )
                              );
                        }
                     } catch (RuntimeException var13) {
                     }
                  }
               }
            }
         );
   }

   private void handleLine(String line, UUID self) {
      if (line != null && !line.isBlank()) {
         try {
            JsonObject event = JsonParser.parseString(line).getAsJsonObject();
            if (event.has("event") && "message".equals(event.get("event").getAsString()) && event.has("message")) {
               long timeMs = event.has("time") ? event.get("time").getAsLong() * 1000L : System.currentTimeMillis();
               JsonObject msg = JsonParser.parseString(event.get("message").getAsString()).getAsJsonObject();
               if (msg.has("v") && msg.get("v").getAsInt() == 1 && msg.has("uuid")) {
                  UUID uuid = UUID.fromString(msg.get("uuid").getAsString());
                  if (uuid.equals(self)) {
                     return;
                  }

                  MeowlNetwork.Peer old = this.peers.get(uuid);
                  if (old != null && old.lastSeenMs() > timeMs) {
                     return;
                  }

                  String name = msg.has("name") ? msg.get("name").getAsString() : "?";
                  if (name.length() > 16) {
                     name = name.substring(0, 16);
                  }

                  boolean offline = msg.has("off") && msg.get("off").getAsBoolean();
                  boolean petEnabled = msg.has("pet") && msg.get("pet").getAsBoolean();
                  PetConfig pet = null;
                  if (petEnabled) {
                     pet = new PetConfig(
                           str(msg, "skin"),
                           str(msg, "dance"),
                           num(msg, "speed", 1.0),
                           num(msg, "size", 0.8),
                           num(msg, "radius", 4.0),
                           !msg.has("shadow") || msg.get("shadow").getAsBoolean(),
                           num(msg, "lift", 0.0)
                        )
                        .sanitized();
                  }

                  this.peers.put(uuid, new MeowlNetwork.Peer(uuid, name, petEnabled, pet, timeMs, offline, offline ? null : MeowlNetwork.Watch.of(msg), offline ? null : MeowlNetwork.WingsInfo.of(msg), offline ? java.util.Map.of() : cosOf(msg), !offline && msg.has("afk") && msg.get("afk").getAsBoolean(), offline ? null : pfpOf(msg)));
                  return;
               }

               return;
            }

            return;
         } catch (RuntimeException var13) {
         }
      }
   }

   private static String str(JsonObject o, String key) {
      return o.has(key) ? o.get(key).getAsString() : null;
   }

   private static double num(JsonObject o, String key, double def) {
      return o.has(key) ? o.get(key).getAsDouble() : def;
   }

   /** Profilbild (Base64-PNG, 16 x 16) oder null. */
   static String pfpOf(JsonObject o) {
      if (o.has("pfp") && o.get("pfp").isJsonPrimitive()) {
         String s = o.get("pfp").getAsString();
         return !s.isEmpty() && s.length() <= 4096 && s.matches("[A-Za-z0-9+/=]+") ? s : null;
      } else {
         return null;
      }
   }

   public record Peer(UUID uuid, String name, boolean petEnabled, PetConfig pet, long lastSeenMs, boolean offline, MeowlNetwork.Watch watch, MeowlNetwork.WingsInfo wings, java.util.Map<String, String> cos, boolean afk, String pfp) {
      /** Wert einer geteilten Kosmetik (z.B. "hat"), null = keine. */
      public String cosmetic(String key) {
         return this.cos == null ? null : this.cos.get(key);
      }
   }

   /** Geteilte Kosmetik: kleines Objekt "cos" mit Schlüssel/Wert-Paaren. */
   static java.util.Map<String, String> cosOf(JsonObject o) {
      if (o.has("cos") && o.get("cos").isJsonObject()) {
         java.util.Map<String, String> out = new java.util.HashMap<>();

         for (java.util.Map.Entry<String, JsonElement> e : o.getAsJsonObject("cos").entrySet()) {
            if (out.size() < 24 && e.getValue().isJsonPrimitive() && e.getKey().length() <= 20) {
               String v = e.getValue().getAsString();
               if (!v.isEmpty() && v.length() <= 64) {
                  out.put(e.getKey(), v);
               }
            }
         }

         return out;
      } else {
         return java.util.Map.of();
      }
   }

   /** Flügel eines anderen Meowl-Spielers. */
   public record WingsInfo(String style, float size) {
      static MeowlNetwork.WingsInfo of(JsonObject o) {
         if (o.has("wings") && o.get("wings").isJsonPrimitive()) {
            String style = o.get("wings").getAsString();
            if (!style.isEmpty() && style.length() <= 32) {
               float size = o.has("wingsSize") ? (float)Math.max(0.4, Math.min(1.4, o.get("wingsSize").getAsDouble())) : 0.8F;
               return new MeowlNetwork.WingsInfo(style, size);
            }
         }

         return null;
      }
   }

   /** color = eigene Farbe 0xFFRRGGBB, 0 = Original. */
   public record Watch(String model, boolean offArm, float size, float position, int color) {
      static MeowlNetwork.Watch of(JsonObject o) {
         if (o.has("watch") && o.get("watch").isJsonPrimitive()) {
            String model = o.get("watch").getAsString();
            if (!model.isEmpty() && model.length() <= 32) {
               boolean off = o.has("watchOff") && o.get("watchOff").getAsBoolean();
               float size = o.has("watchSize") ? (float)Math.max(0.6, Math.min(1.6, o.get("watchSize").getAsDouble())) : 1.0F;
               float pos = o.has("watchPos") ? (float)Math.max(-3.0, Math.min(1.5, o.get("watchPos").getAsDouble())) : 0.0F;
               int col = 0;
               if (o.has("watchColor") && o.get("watchColor").getAsString().matches("[0-9A-Fa-f]{6}")) {
                  col = 0xFF000000 | Integer.parseInt(o.get("watchColor").getAsString(), 16);
               }

               return new MeowlNetwork.Watch(model, off, size, pos, col);
            } else {
               return null;
            }
         } else {
            return null;
         }
      }
   }
}
