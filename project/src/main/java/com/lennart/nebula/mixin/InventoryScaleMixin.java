package com.lennart.nebula.mixin;

import com.lennart.nebula.module.hud.InventoryScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Eigene GUI-Größe für offene Inventare (siehe {@link InventoryScale}). */
@Mixin({Minecraft.class})
public abstract class InventoryScaleMixin {
   @Shadow
   public Screen screen;

   @Inject(method = {"setScreen"}, at = {@At("HEAD")})
   private void meowl$inventoryScale(Screen next, CallbackInfo ci) {
      InventoryScale.beforeScreen(next);
   }

   @Inject(method = {"resizeDisplay"}, at = {@At("TAIL")})
   private void meowl$inventoryScaleResize(CallbackInfo ci) {
      InventoryScale.afterResize(this.screen);
   }
}
