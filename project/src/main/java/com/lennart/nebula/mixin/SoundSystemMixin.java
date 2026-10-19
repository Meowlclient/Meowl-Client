package com.lennart.nebula.mixin;

import com.lennart.nebula.module.sound.SoundController;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngine.PlayResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({SoundEngine.class})
public abstract class SoundSystemMixin {
   @Inject(
      method = {"play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void nebula$muteSound(SoundInstance sound, CallbackInfoReturnable<PlayResult> cir) {
      if (sound != null && SoundController.factorFor(sound) <= 0.0F) {
         cir.setReturnValue(PlayResult.NOT_STARTED);
      }
   }

   @Redirect(
      method = {"play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/resources/sounds/SoundInstance;getVolume()F"
      )
   )
   private float nebula$scaleStartVolume(SoundInstance sound) {
      return sound.getVolume() * SoundController.factorFor(sound);
   }

   @Inject(
      method = {"calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void nebula$scaleRunningVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
      cir.setReturnValue(Math.min(1.0F, cir.getReturnValueF() * SoundController.factorFor(sound)));
   }
}
