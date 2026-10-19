package com.lennart.nebula.mixin;

import com.lennart.nebula.module.render.Fullbright;
import com.lennart.nebula.util.OptionsWriteGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({OptionInstance.class})
public abstract class SimpleOptionMixin {
   @Inject(
      method = {"get"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void meowl$fullbrightGamma(CallbackInfoReturnable<Object> cir) {
      if (!OptionsWriteGuard.isWriting()) {
         Minecraft client = Minecraft.getInstance();
         if (client != null && client.options != null && (Object)this == client.options.gamma()) {
            Double override = Fullbright.gammaOverride();
            if (override != null) {
               cir.setReturnValue(override);
            }
         }
      }
   }
}
