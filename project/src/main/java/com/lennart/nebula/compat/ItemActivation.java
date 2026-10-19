package com.lennart.nebula.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** 26.x: die Item-Animation hängt am lokalen Spieler. */
public final class ItemActivation {
   private ItemActivation() {
   }

   public static void show(ItemStack stack) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.player.displayItemActivation(stack);
      }
   }
}
