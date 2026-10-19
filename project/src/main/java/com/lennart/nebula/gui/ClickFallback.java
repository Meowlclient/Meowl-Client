package com.lennart.nebula.gui;

import com.lennart.nebula.NebulaClient;
import net.minecraft.client.Minecraft;

/**
 * Notfall-Klicks für die Meowl-Menüs: manche Mods/Versionen (z.B. 26.x mit Essential) lassen Mausklicks im Hauptmenü
 * nicht bis zum Bildschirm durch. Dann wird die linke Maustaste selbst abgefragt (unter Windows direkt beim System) und
 * ein Klick an der Mausposition ausgelöst – aber nur, wenn kein normaler Klick angekommen ist.
 */
public final class ClickFallback {
   /** Start „gedrückt“: der Klick, der diesen Bildschirm geöffnet hat, darf hier nicht nochmal auslösen. */
   private boolean wasDown = true;
   private long lastEvent;
   private final long created = System.currentTimeMillis();
   private static boolean reported;

   /** Von mouseClicked aufrufen: ein normaler Klick ist angekommen. */
   public void onEvent() {
      this.lastEvent = System.currentTimeMillis();
   }

   /** Jedes Bild aufrufen: holt einen verlorenen Klick als echtes Klick-Ereignis nach (nach dem Bild, nicht mittendrin). */
   public void pump(net.minecraft.client.gui.screens.Screen screen, Minecraft mc) {
      double[] c = this.poll(mc);
      if (c != null) {
         mc.execute(() -> {
            if (mc.screen == screen) {
               net.minecraft.client.input.MouseButtonEvent ev = new net.minecraft.client.input.MouseButtonEvent(c[0], c[1], new net.minecraft.client.input.MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT, 0));
               screen.mouseClicked(ev, false);
               if (mc.screen == screen) {
                  screen.mouseReleased(ev);
               }
            }
         });
      }
   }

   /** Jedes Bild aufrufen; liefert {x, y} in GUI-Koordinaten, wenn ein Klick nachgeholt werden soll, sonst null. */
   public double[] poll(Minecraft mc) {
      boolean down;
      double[] pos = null;
      try {
         if (!mc.isWindowActive()) {

            this.wasDown = false;
            return null;
         }

         if (isWindows()) {
            // Bit 15 = gerade gedrückt, Bit 0 = seit der letzten Abfrage gedrückt worden (sehr kurze Klicks)
            down = (com.sun.jna.platform.win32.User32.INSTANCE.GetAsyncKeyState(0x01) & 0x8001) != 0;
         } else {
            down = mc.mouseHandler.isLeftPressed();
         }

         if (down && !this.wasDown) {
            pos = this.position(mc);
         }
      } catch (RuntimeException | LinkageError var5) {
         down = false;
      }

      boolean edge = down && !this.wasDown && System.currentTimeMillis() - this.created > 250L;
      this.wasDown = down;
      if (!edge || pos == null) {
         return null;
      }

      // der normale Klick kommt vor dem Zeichnen an – war er da, nichts nachholen
      if (System.currentTimeMillis() - this.lastEvent < 150L) {
         return null;
      }

      if (!reported) {
         reported = true;
         NebulaClient.LOGGER.info("Meowl-Menü: Mausklick kam nicht beim Bildschirm an – wird selbst ausgelöst");
      }

      return pos;
   }

   private double[] position(Minecraft mc) {
      com.mojang.blaze3d.platform.Window w = mc.getWindow();
      if (isWindows()) {
         com.sun.jna.platform.win32.WinDef.HWND hwnd = com.sun.jna.platform.win32.User32.INSTANCE.GetForegroundWindow();
         com.sun.jna.platform.win32.WinDef.POINT p = new com.sun.jna.platform.win32.WinDef.POINT();
         com.sun.jna.platform.win32.WinUser.WINDOWINFO info = new com.sun.jna.platform.win32.WinUser.WINDOWINFO();
         if (hwnd != null && com.sun.jna.platform.win32.User32.INSTANCE.GetCursorPos(p) && com.sun.jna.platform.win32.User32.INSTANCE.GetWindowInfo(hwnd, info)) {
            int cw = info.rcClient.right - info.rcClient.left;
            int ch = info.rcClient.bottom - info.rcClient.top;
            if (cw > 0 && ch > 0) {
               double x = (double)(p.x - info.rcClient.left) * w.getGuiScaledWidth() / cw;
               double y = (double)(p.y - info.rcClient.top) * w.getGuiScaledHeight() / ch;
               if (x >= 0 && y >= 0 && x <= w.getGuiScaledWidth() && y <= w.getGuiScaledHeight()) {
                  return new double[]{x, y};
               }

               return null;
            }
         }
      }

      return new double[]{mc.mouseHandler.getScaledXPos(w), mc.mouseHandler.getScaledYPos(w)};
   }

   private static boolean isWindows() {
      return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
   }
}
