package com.lennart.nebula.emote;

import net.minecraft.client.Minecraft;

/**
 * Emotes in der Ich-Ansicht: solange ein eigener Emote läuft, wird dein Körper (ohne Kopf) samt Fahrzeug in der Welt
 * gezeichnet – so siehst du beim Tanzen deine Arme und im Hubschrauber das Cockpit. Die normalen Ego-Hände sind dann aus.
 */
public final class EmoteBody {
   private EmoteBody() {
   }

   /** Eigenen Körper in der Ich-Ansicht zeigen? */
   public static boolean showOwnBody() {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player == null || !mc.options.getCameraType().isFirstPerson() || mc.screen != null) {
         return false;
      }

      EmoteState.Active a = EmoteState.local();
      return a != null && a.type.offered() && com.lennart.nebula.module.mods.OtherClients.showMeowlEmotes();
   }

   /** Kopf (und Kopf-Kosmetik) dieser Spieler-Entity ausblenden, weil die Kamera darin sitzt? */
   public static boolean hideHead(int entityId) {
      Minecraft mc = Minecraft.getInstance();
      return mc.player != null && mc.player.getId() == entityId && showOwnBody();
   }
}
