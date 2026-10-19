package com.lennart.nebula.mixin;

import com.lennart.nebula.module.mods.ClientSettings;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.x: Minecraft kann selbst randloses Vollbild – Meowl schaltet nur das exklusive Vollbild ab (Discord-Streams). */
@Mixin({Window.class})
public abstract class BorderlessFullscreenMixin {
   @Shadow
   private boolean exclusiveFullscreen;

   @Inject(method = {"setMode"}, at = {@At("HEAD")})
   private void meowl$borderlessMode(CallbackInfo ci) {
      if (ClientSettings.borderless()) {
         this.exclusiveFullscreen = false;
      }
   }
}
