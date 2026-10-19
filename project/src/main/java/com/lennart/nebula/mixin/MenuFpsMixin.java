package com.lennart.nebula.mixin;

import com.lennart.nebula.gui.MenuFps;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Meowl-Menü (Rechts-Shift): höchstens 60 FPS, solange es offen ist. */
@Mixin({FramerateLimitTracker.class})
public abstract class MenuFpsMixin {
   @Inject(method = {"getFramerateLimit"}, at = {@At("RETURN")}, cancellable = true)
   private void meowl$menuFps(CallbackInfoReturnable<Integer> cir) {
      int cap = MenuFps.cap();
      if (cap > 0 && cir.getReturnValue() > cap) {
         cir.setReturnValue(cap);
      }
   }
}
