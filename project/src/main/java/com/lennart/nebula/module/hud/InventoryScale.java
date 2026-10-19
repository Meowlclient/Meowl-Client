package com.lennart.nebula.module.hud;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * Eigene GUI-Größe für Inventare (Truhen, Inventar, Öfen …) und für die Meowl-Menüs: solange so ein Bildschirm offen
 * ist, nutzt Minecraft die eingestellte GUI-Größe, danach wieder die normale. So passt alles – Klicks, Tooltips, Slots –
 * ohne Umrechnung.
 */
public final class InventoryScale {
   private static boolean applied;

   private InventoryScale() {
   }

   /** Gewünschte GUI-Größe für einen Bildschirm (0 = wie sonst auch). */
   private static int target(Screen screen) {
      if (screen instanceof AbstractContainerScreen) {
         InventoryHud hud = InventoryHud.instance();
         return hud == null ? 0 : hud.inventoryGuiScale();
      } else if (isMeowlMenu(screen)) {
         return com.lennart.nebula.module.mods.ClientSettings.menuScale();
      } else {
         return 0;
      }
   }

   /** Eigene Menüs des Meowl Clients (der HUD-Editor bleibt bei der Spiel-Größe, sonst passen die Positionen nicht). */
   public static boolean isMeowlMenu(Screen screen) {
      return screen != null
         && screen.getClass().getName().startsWith("com.lennart.nebula.")
         && !(screen instanceof com.lennart.nebula.gui.HudEditorScreen);
   }

   private static boolean wants(Screen screen) {
      return target(screen) > 0;
   }

   private static int normalScale(Minecraft mc) {
      return mc.getWindow().calculateScale(mc.options.guiScale().get(), mc.options.forceUnicodeFont().get());
   }

   private static int scaleFor(Minecraft mc, Screen screen) {
      int max = mc.getWindow().calculateScale(0, mc.options.forceUnicodeFont().get());
      return Math.max(1, Math.min(target(screen), max));
   }

   /** Vor dem Wechsel zu {@code next}: GUI-Größe passend setzen, bevor der Bildschirm aufgebaut wird. */
   public static void beforeScreen(Screen next) {
      Minecraft mc = Minecraft.getInstance();
      Window w = mc.getWindow();
      if (wants(next)) {
         int s = scaleFor(mc, next);
         if (w.getGuiScale() != s) {
            w.setGuiScale(s);
         }

         applied = true;
      } else if (applied) {
         applied = false;
         w.setGuiScale(normalScale(mc));
      }
   }

   /** Nach einer Fenster-Größenänderung (Minecraft hat die normale Größe gesetzt): wieder die eigene Größe nehmen. */
   public static void afterResize(Screen current) {
      if (applied && wants(current)) {
         Minecraft mc = Minecraft.getInstance();
         Window w = mc.getWindow();
         int s = scaleFor(mc, current);
         if (w.getGuiScale() != s) {
            w.setGuiScale(s);
            current.resize(w.getGuiScaledWidth(), w.getGuiScaledHeight());
         }
      }
   }

   /** Einstellung geändert, während ein Inventar oder Meowl-Menü offen ist. */
   public static void refresh(Screen current) {
      if (current instanceof AbstractContainerScreen || isMeowlMenu(current)) {
         Minecraft mc = Minecraft.getInstance();
         Window w = mc.getWindow();
         int s = wants(current) ? scaleFor(mc, current) : normalScale(mc);
         applied = wants(current);
         if (w.getGuiScale() != s) {
            w.setGuiScale(s);
            current.resize(w.getGuiScaledWidth(), w.getGuiScaledHeight());
         }
      }
   }
}
