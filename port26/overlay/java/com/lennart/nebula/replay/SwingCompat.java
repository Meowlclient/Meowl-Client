package com.lennart.nebula.replay;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;

/** Arm-Schwung lesen/abspielen (26.x). */
public final class SwingCompat {
   private SwingCompat() {
   }

   public static boolean isSwinging(LivingEntity e) {
      return e.isSwinging();
   }

   public static void swing(LivingEntity e) {
      e.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
   }
}