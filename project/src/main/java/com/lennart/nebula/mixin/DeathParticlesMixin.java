package com.lennart.nebula.mixin;

import com.lennart.nebula.module.mods.DeathAnimation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Death Animation: „Vanilla-Todesanimation aus“ → keine Vanilla-Rauchwolke beim Tod von Spielern. */
@Mixin({LivingEntity.class})
public abstract class DeathParticlesMixin {
   @Inject(method = {"makePoofParticles"}, at = {@At("HEAD")}, cancellable = true)
   private void meowl$noPoof(CallbackInfo ci) {
      if ((Object)this instanceof Player && ((LivingEntity)(Object)this).level().isClientSide() && DeathAnimation.vanillaDisabled()) {
         ci.cancel();
      }
   }
}
