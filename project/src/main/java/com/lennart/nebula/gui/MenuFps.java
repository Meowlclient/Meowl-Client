package com.lennart.nebula.gui;

import net.minecraft.client.Minecraft;

/** FPS-Grenze für das Meowl-Menü und seine Unterseiten (spart Leistung, Menü läuft trotzdem flüssig). */
public final class MenuFps {
   public static final int MENU_FPS = 60;

   private MenuFps() {
   }

   /** 0 = keine Grenze. */
   public static int cap() {
      Minecraft mc = Minecraft.getInstance();
      return mc.gui.screen() instanceof MeowlScreen || mc.gui.screen() instanceof HudEditorScreen ? MENU_FPS : 0;
   }
}
