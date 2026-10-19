package com.lennart.nebula.mixin;

import com.lennart.nebula.util.InputBlocker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Minecraft.class})
public abstract class MinecraftClientInputMixin {
   @Inject(
      method = {"startUseItem"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void nebula$blockUse(CallbackInfo ci) {
      if (InputBlocker.isUseBlocked()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"startAttack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void nebula$blockAttack(CallbackInfoReturnable<Boolean> cir) {
      if (InputBlocker.isAttackBlocked()) {
         cir.setReturnValue(false);
      }
   }

   @Inject(
      method = {"continueAttack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void nebula$blockBreaking(boolean breaking, CallbackInfo ci) {
      if (breaking && InputBlocker.isAttackBlocked()) {
         ci.cancel();
      }
   }
}
