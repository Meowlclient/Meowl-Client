package com.lennart.nebula.mixin;

import com.lennart.nebula.module.hud.InventoryScale;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.x: Bildschirme wechselt {@link Gui} (Fenstergröße: {@link InventoryScaleResizeMixin}). */
@Mixin({Gui.class})
public abstract class InventoryScaleMixin {
   @Inject(method = {"setScreen"}, at = {@At("HEAD")})
   private void meowl$inventoryScale(Screen next, CallbackInfo ci) {
      InventoryScale.beforeScreen(next);
   }
}
