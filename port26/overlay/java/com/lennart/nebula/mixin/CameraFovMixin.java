package com.lennart.nebula.mixin;

import com.lennart.nebula.module.mods.DesertAfk;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 26.x: das Sichtfeld der Welt berechnet jetzt die Kamera (Hand-FOV ist getrennt). */
@Mixin({Camera.class})
public abstract class CameraFovMixin {
   @Inject(method = {"getFov()F"}, at = {@At("RETURN")}, cancellable = true)
   private void meowl$afkFov(CallbackInfoReturnable<Float> cir) {
      float factor = com.lennart.nebula.fx.FovFx.factor();
      if (factor != 1.0F) {
         cir.setReturnValue(cir.getReturnValue() * factor);
      }
   }
}
