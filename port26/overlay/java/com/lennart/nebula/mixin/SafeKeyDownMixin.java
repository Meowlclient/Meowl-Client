package com.lennart.nebula.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.x: Tastenabfrage über die SDL-Tastentabelle. Alte Einstellungen (z.B. „key.keyboard.-1“ aus 1.21) liefern
 * ungültige Nummern – statt abzustürzen gilt die Taste dann einfach als nicht gedrückt.
 */
@Mixin({InputConstants.class})
public abstract class SafeKeyDownMixin {
   @Inject(method = {"isKeyDown(I)Z"}, at = {@At("HEAD")}, cancellable = true)
   private static void meowl$safeKeyDown(int key, CallbackInfoReturnable<Boolean> cir) {
      if (key < 0 || key >= 512) {
         cir.setReturnValue(false);
      }
   }
}
