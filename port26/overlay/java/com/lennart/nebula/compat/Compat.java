package com.lennart.nebula.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

/** 26.x-Fassung der Versions-Hilfen. */
public final class Compat {
   private Compat() {
   }

   public static void message(Component text, boolean actionBar) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         if (actionBar) {
            mc.gui.hud.setOverlayMessage(text, false);
         } else {
            mc.player.sendSystemMessage(text);
         }
      }
   }

   public static int lightAt(Level level, BlockPos pos) {
      return LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos));
   }

   public static void swingMainHand(Player player) {
      player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
   }

   public static boolean isSwinging(LivingEntity entity) {
      return entity.isSwinging();
   }

   public static float attackAnim(LivingEntity entity, float tickDelta) {
      return entity.getSwingAnimation(tickDelta);
   }

   public static void toast(Component title, Component text) {
      SystemToast.add(Minecraft.getInstance().gui.toastManager(), SystemToastId.PERIODIC_NOTIFICATION, title, text);
   }

   public static boolean renderNames() {
      return !Minecraft.getInstance().gui.hud.isHidden();
   }
}
