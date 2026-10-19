package com.lennart.nebula.mixin;

import com.lennart.nebula.gui.SwordIcon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.x: Items in der GUI zeichnet GuiGraphicsExtractor.item. */
@Mixin({GuiGraphicsExtractor.class})
public abstract class SwordIconMixin {
   @Inject(
      method = {"item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void meowl$swordIcon(LivingEntity entity, Level level, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
      if (SwordIcon.draw((GuiGraphicsExtractor)(Object)this, stack, x, y)) {
         ci.cancel();
      }
   }
}
