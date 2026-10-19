package com.lennart.nebula.mixin;

import com.lennart.nebula.util.OptionsWriteGuard;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Options.class})
public abstract class GameOptionsMixin {
   @Inject(
      method = {"save"},
      at = {@At("HEAD")}
   )
   private void meowl$beginWrite(CallbackInfo ci) {
      OptionsWriteGuard.setWriting(true);
   }

   @Inject(
      method = {"save"},
      at = {@At("RETURN")}
   )
   private void meowl$endWrite(CallbackInfo ci) {
      OptionsWriteGuard.setWriting(false);
   }
}
