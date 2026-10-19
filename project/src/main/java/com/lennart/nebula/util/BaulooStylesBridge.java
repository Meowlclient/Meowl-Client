package com.lennart.nebula.util;

import com.lennart.nebula.NebulaClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class BaulooStylesBridge {
   public static final String MOD_ID = "bauloo_styles";
   private static final String SCREEN_CLASS = "de.bauloo.styles.BaulooStylesConfigScreen";

   private BaulooStylesBridge() {
   }

   public static boolean isAvailable() {
      return FabricLoader.getInstance().isModLoaded("bauloo_styles");
   }

   public static boolean open(Screen parent) {
      if (!isAvailable()) {
         ChatUtil.actionBar("§cBauloo Styles ist nicht installiert");
         return false;
      } else {
         try {
            Class<?> type = Class.forName("de.bauloo.styles.BaulooStylesConfigScreen");
            Screen screen = (Screen)type.getConstructor(Screen.class).newInstance(parent);
            Minecraft.getInstance().gui.setScreen(screen);
            return true;
         } catch (LinkageError | ReflectiveOperationException | ClassCastException var31) {
            NebulaClient.LOGGER.error("Bauloo-Styles-Menü konnte nicht geöffnet werden", (Throwable)var31);
            ChatUtil.actionBar("§cBauloo-Styles-Menü konnte nicht geöffnet werden");
            return false;
         }
      }
   }
}
