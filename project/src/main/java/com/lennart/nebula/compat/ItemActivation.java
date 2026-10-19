package com.lennart.nebula.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Große Item-Animation (wie beim Totem-Pop) abspielen. */
public final class ItemActivation {
   private ItemActivation() {
   }

   public static void show(ItemStack stack) {
      Minecraft.getInstance().gameRenderer.displayItemActivation(stack);
   }
}
