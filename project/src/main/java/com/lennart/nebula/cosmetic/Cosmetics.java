package com.lennart.nebula.cosmetic;

import com.google.gson.JsonObject;
import com.lennart.nebula.module.mods.MeowlPet;
import com.lennart.nebula.util.MeowlNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

/** Alle Kosmetik-Module: Teilen über das Meowl-Netzwerk, Werte je Spieler und Partikel. */
public final class Cosmetics {
   private static final List<Cosmetic> ALL = new ArrayList<>();
   private static final Map<String, CosmeticValue> CACHE = new HashMap<>();
   private static final Random R = new Random();
   private static final long START = System.nanoTime();

   private Cosmetics() {
   }

   private static Cosmetic previewing;
   private static long previewUntil;

   /** Die Einstellungs-Vorschau zeigt diese Kosmetik gerade (auch wenn das Modul aus ist). */
   public static void markPreview(Cosmetic c) {
      previewing = c;
      previewUntil = System.currentTimeMillis() + 300L;
   }

   private static boolean previewed(Cosmetic c) {
      return previewing == c && System.currentTimeMillis() < previewUntil;
   }

   static void register(Cosmetic c) {
      ALL.add(c);
   }

   public static List<Cosmetic> all() {
      return ALL;
   }

   /** Laufende Zeit in Sekunden (gleichmäßig, für Animationen). */
   public static float time() {
      return (System.nanoTime() - START) / 1.0E9F;
   }

   public static boolean anyActive() {
      for (Cosmetic c : ALL) {
         if (c.shares()) {
            return true;
         }
      }

      return false;
   }

   /** Will man die Kosmetik anderer sehen (dafür muss die Anwesenheit laufen)? */
   public static boolean anyShowOthers() {
      if (com.lennart.nebula.module.mods.PlasmaMode.wantsOthers()) {
         return true;
      }

      for (Cosmetic c : ALL) {
         if (c.showsOthers()) {
            return true;
         }
      }

      return false;
   }

   /** Für die Anwesenheitsmeldung: "cos": {schlüssel: wert}. */
   public static void presenceFields(JsonObject o) {
      JsonObject cos = new JsonObject();

      for (Cosmetic c : ALL) {
         if (c.shares()) {
            String v = c.encode();
            if (v != null) {
               cos.addProperty(c.key, v);
            }
         }
      }

      com.lennart.nebula.module.mods.PlasmaMode.presence(cos);

      if (cos.size() > 0) {
         o.add("cos", cos);
      }
   }

   /** Wert einer Kosmetik für einen Spieler (eigener oder geteilter), null = keine. */
   public static CosmeticValue valueFor(Player player, Cosmetic c) {
      if (player == Minecraft.getInstance().player) {
         return c.isEnabled() || previewed(c) ? decodeCached(c.encodeValue()) : null;
      } else if (!c.showsOthers()) {
         return null;
      } else {
         MeowlNetwork.Peer peer = MeowlPet.peer(player);
         return peer == null ? null : decodeCached(peer.cosmetic(c.key));
      }
   }

   private static CosmeticValue decodeCached(String text) {
      if (text == null) {
         return null;
      } else {
         synchronized (CACHE) {
            if (CACHE.size() > 512) {
               CACHE.clear();
            }

            return CACHE.computeIfAbsent(text, CosmeticValue::decode);
         }
      }
   }

   /** Jeden Tick: Partikel der Kosmetik für Spieler in der Nähe. */
   public static void tick(Minecraft mc) {
      ClientLevel world = mc.level;
      if (world != null && mc.player != null && !mc.isPaused()) {
         for (AbstractClientPlayer p : world.players()) {
            if (!p.isInvisible() && !(p.distanceToSqr(mc.player) > 2304.0)) {
               boolean firstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson();

               for (Cosmetic c : ALL) {
                  CosmeticValue v = valueFor(p, c);
                  if (v != null) {
                     try {
                        if (!firstPerson || c.particlesInFirstPerson()) {
                           c.particles(world, p, v, R);
                        }
                     } catch (RuntimeException var10) {
                     }
                  }
               }
            }
         }
      }
   }
}
