package com.lennart.nebula.emote;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lennart.nebula.net.MeowlService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Laufende Emotes: der eigene und die anderer Meowl-Spieler. Der eigene wird über den Meowl Server an alle
 * Meowl-Spieler auf demselben Minecraft-Server geschickt; deren Emotes kommen als Ereignis "emote" zurück.
 */
public final class EmoteState {
   private static final long START = System.nanoTime();
   private static final Map<UUID, EmoteState.Active> REMOTE = new ConcurrentHashMap<>();
   private static EmoteState.Active local;
   private static EmoteState.Active preview;
   /** Vorschau gilt nur, solange sie gezeigt wird (Aufruf jedes Bild). */
   private static long previewAt;
   private static boolean showOthers = true;
   private static boolean share = true;
   private static boolean hooked;
   private static long lastFetch;

   public static final class Active {
      public final EmoteType type;
      public final float start;
      /** Zeitpunkt des Abbruchs (für das Ausblenden), sonst -1. */
      public float end = -1.0F;

      public Active(EmoteType type, float start) {
         this.type = type;
         this.start = start;
      }

      boolean finished(float now) {
         return this.end >= 0.0F ? now - this.end > 0.3F : !this.type.loops() && now - this.start > this.type.duration;
      }
   }

   private EmoteState() {
   }

   public static float now() {
      return (System.nanoTime() - START) / 1.0E9F;
   }

   public static void configure(boolean showOthers, boolean share) {
      EmoteState.showOthers = showOthers;
      EmoteState.share = share;
      if (!showOthers) {
         REMOTE.clear();
      }
   }

   // ------------------------------------------------------------------ eigener Emote

   public static EmoteState.Active local() {
      EmoteState.Active a = local;
      if (a != null && a.finished(now())) {
         local = null;
         return null;
      } else {
         return a;
      }
   }

   /** Spielt einen Emote ab (ein Loop-Emote desselben Typs wird stattdessen beendet). */
   public static void play(EmoteType type) {
      EmoteState.Active cur = local();
      if (cur != null && cur.type == type && type.loops() && cur.end < 0.0F) {
         stop();
      } else {
         local = new EmoteState.Active(type, now());
         send(type, true);
      }
   }

   public static void stop() {
      EmoteState.Active cur = local();
      if (cur != null && cur.end < 0.0F) {
         cur.end = now();
         send(cur.type, false);
      }
   }

   public static boolean playing() {
      EmoteState.Active cur = local();
      return cur != null && cur.end < 0.0F;
   }

   private static void send(EmoteType type, boolean on) {
      MeowlService s = MeowlService.get();
      if (share && s.isConnected()) {
         JsonObject o = new JsonObject();
         o.addProperty("id", type.id);
         o.addProperty("on", on);
         o.addProperty("loop", type.loops());
         s.post("/emote", o);
      }
   }

   // ------------------------------------------------------------------ andere Spieler

   public static void hook() {
      if (!hooked) {
         hooked = true;
         MeowlService s = MeowlService.get();
         s.on("emote", EmoteState::onEvent);
         s.onConnected(() -> {
            REMOTE.clear();
            lastFetch = 0L;
         });
      }
   }

   private static void onEvent(JsonObject d) {
      try {
         UUID uuid = UUID.fromString(d.get("uuid").getAsString());
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && uuid.equals(mc.player.getUUID()) || !showOthers) {
            return;
         }

         EmoteType type = EmoteType.byId(d.get("id").getAsString());
         if (type == null) {
            return;
         }

         if (d.has("on") && d.get("on").getAsBoolean()) {
            REMOTE.put(uuid, new EmoteState.Active(type, now()));
         } else {
            EmoteState.Active a = REMOTE.get(uuid);
            if (a != null && a.type == type && a.end < 0.0F) {
               a.end = now();
            }
         }
      } catch (RuntimeException var5) {
      }
   }

   /** Regelmäßig: abgelaufene entfernen, beim Betreten eines Servers laufende Loop-Emotes der anderen holen. */
   public static void tick() {
      float now = now();
      REMOTE.values().removeIf(a -> a.finished(now) || a.type.loops() && now - a.start > 900.0F);
      MeowlService s = MeowlService.get();
      long ms = System.currentTimeMillis();
      if (showOthers && s.isConnected() && Minecraft.getInstance().level != null && ms - lastFetch > 20000L) {
         lastFetch = ms;
         s.get("/emote/active").whenComplete((r, ex) -> {
            if (ex == null && r != null && r.ok() && r.json() != null && r.json().has("emotes")) {
               Minecraft.getInstance().execute(() -> {
                  float t = now();

                  for (JsonElement el : r.json().getAsJsonArray("emotes")) {
                     try {
                        JsonObject o = el.getAsJsonObject();
                        UUID uuid = UUID.fromString(o.get("uuid").getAsString());
                        EmoteType type = EmoteType.byId(o.get("id").getAsString());
                        if (type != null && type.loops() && !REMOTE.containsKey(uuid)) {
                           REMOTE.put(uuid, new EmoteState.Active(type, t - 1.0F));
                        }
                     } catch (RuntimeException var8) {
                     }
                  }
               });
            }
         });
      }
   }

   /** Vorschau im Emote-Rad (nur auf dem eigenen Charakter, wird nicht gesendet). */
   public static void preview(EmoteType type) {
      previewAt = System.currentTimeMillis();
      if (type == null) {
         preview = null;
      } else if (preview == null || preview.type != type) {
         preview = new EmoteState.Active(type, now());
      } else if (!type.loops() && now() - preview.start > type.duration + 0.4F) {
         preview = new EmoteState.Active(type, now());
      }
   }

   public static void clearRemote() {
      REMOTE.clear();
   }

   /** Emote für eine Spieler-Entity (null = keiner). Der eigene Hinsetz-Emote läuft über {@link com.lennart.nebula.afk.SitPose}. */
   public static EmoteState.Active forEntity(int entityId) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.player.getId() == entityId) {
         if (preview != null && System.currentTimeMillis() - previewAt < 500L) {
            return preview;
         }

         EmoteState.Active a = local();
         return a != null && a.type != EmoteType.SIT ? a : null;
      } else if (REMOTE.isEmpty() || mc.level == null) {
         return null;
      } else {
         Entity e = mc.level.getEntity(entityId);
         if (e instanceof Player p) {
            EmoteState.Active a = REMOTE.get(p.getUUID());
            if (a != null && a.finished(now())) {
               REMOTE.remove(p.getUUID());
               return null;
            } else {
               return a;
            }
         } else {
            return null;
         }
      }
   }

   /** Vorschau für genau einen Render-Zustand (Shop-Karten: jede Karte zeigt ein anderes Emote). */
   private static final Map<Object, EmoteState.Active> STATE_PREVIEW = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

   public static void previewState(Object renderState, EmoteState.Active emote) {
      STATE_PREVIEW.put(renderState, emote);
   }

   /** Emote für einen Render-Zustand: erst eine Shop-Vorschau, sonst wie {@link #forEntity}. */
   public static EmoteState.Active forState(Object renderState, int entityId) {
      if (!STATE_PREVIEW.isEmpty()) {
         EmoteState.Active a = STATE_PREVIEW.get(renderState);
         if (a != null) {
            return a;
         }
      }

      return forEntity(entityId);
   }

   public static int remoteCount() {
      return REMOTE.size();
   }
}
