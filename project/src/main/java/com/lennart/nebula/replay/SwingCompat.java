package com.lennart.nebula.replay;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

/** Arm-Schwung lesen/abspielen (unterscheidet sich je Minecraft-Version). */
public final class SwingCompat {
   private SwingCompat() {
   }

   public static boolean isSwinging(LivingEntity e) {
      return e.swinging;
   }

   public static void swing(LivingEntity e) {
      e.swing(InteractionHand.MAIN_HAND);
   }
}