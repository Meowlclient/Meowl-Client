package com.lennart.nebula.gui;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.module.hud.InventoryScale;
import com.lennart.nebula.module.mods.ClientSettings;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Kleiner Größen-Regler unten links in jedem Meowl-Menü: stellt die GUI-Größe aller Meowl-Menüs ein (wie die GUI-Größe
 * von Minecraft, nur für den Client). „A“ = wie Minecraft.
 */
public final class MenuScaleWidget {
   private static final int H = 12;

   private MenuScaleWidget() {
   }

   public static void attach(Screen screen) {
      // im Hauptmenü steckt die Größe im Zahnrad oben rechts
      if (!InventoryScale.isMeowlMenu(screen) || screen instanceof MeowlScreen || screen instanceof MeowlTitleScreen || screen instanceof MeowlMultiplayerScreen) {
         return;
      }

      ScreenEvents.afterExtract(screen).register((s, ctx, mx, my, delta) -> render(s, ctx, mx, my));
      ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> !(click.button() == InputConstants.MOUSE_BUTTON_LEFT && click(s, click.x(), click.y())));
   }

   private static int maxScale() {
      Minecraft mc = Minecraft.getInstance();
      return Math.max(1, Math.min(6, mc.getWindow().calculateScale(0, mc.options.forceUnicodeFont().get())));
   }

   /** Lage: {x, y, Breite, Schritte}. */
   private static int[] box(Screen s) {
      int steps = maxScale() + 1;
      int w = 18 + steps * 9 + 4;
      return new int[]{4, s.height - H - 3, w, steps};
   }

   private static void render(Screen s, GuiGraphicsExtractor ctx, int mx, int my) {
      Font font = Minecraft.getInstance().font;
      int[] b = box(s);
      int x = b[0];
      int y = b[1];
      int w = b[2];
      boolean hover = Render2D.inside(mx, my, x, y, w, H);
      int cur = Math.min(ClientSettings.menuScale(), b[3] - 1);
      Render2D.roundRect(ctx, x, y, w, H, 6, hover ? 0xE02A2048 : 0xA0181230);
      ctx.text(font, "⤢", x + 5, y + 2, hover ? 0xFFE8DCFF : 0xFF9C94C4, false);
      int tx = x + 16;
      // Schiene mit Rasten: A, 1, 2, …
      ctx.fill(tx + 2, y + H / 2, tx + (b[3] - 1) * 9 + 3, y + H / 2 + 1, 0x80B8A8FF);

      for (int i = 0; i < b[3]; i++) {
         int cx = tx + i * 9 + 2;
         boolean sel = i == cur;
         boolean hov = Render2D.inside(mx, my, cx - 4, y, 9, H);
         int r = sel ? 3 : 2;
         ctx.fill(cx - r + 1, y + H / 2 - r + 1, cx + r, y + H / 2 + r, sel ? 0xFFB57BFF : (hov ? 0xFFE8DCFF : 0xFF6E6890));
         if (hov) {
            ctx.setTooltipForNextFrame(font, Component.literal("Menü-Größe: " + (i == 0 ? "wie Minecraft" : String.valueOf(i))), mx, my);
         }
      }

      if (hover && mx < tx) {
         ctx.setTooltipForNextFrame(font, Component.literal("Menü-Größe der Meowl-Menüs: " + (cur == 0 ? "wie Minecraft" : String.valueOf(cur))), mx, my);
      }
   }

   private static boolean click(Screen s, double mx, double my) {
      int[] b = box(s);
      if (!Render2D.inside(mx, my, b[0], b[1], b[2], H)) {
         return false;
      }

      int tx = b[0] + 16;

      for (int i = 0; i < b[3]; i++) {
         int cx = tx + i * 9 + 2;
         if (mx >= cx - 4 && mx < cx + 5) {
            if (i != ClientSettings.menuScale()) {
               ClientSettings.setMenuScale(i);
               NebulaClient c = NebulaClient.getInstance();
               if (c != null && c.getConfigManager() != null) {
                  c.getConfigManager().save();
               }

               // erst nach dem Klick umbauen (der Bildschirm wird dabei neu aufgebaut)
               Minecraft.getInstance().execute(() -> InventoryScale.refresh(s));
            }

            return true;
         }
      }

      return true;
   }
}
