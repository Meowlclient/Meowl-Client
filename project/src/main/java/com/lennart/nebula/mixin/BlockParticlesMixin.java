package com.lennart.nebula.mixin;

import com.lennart.nebula.module.render.Particles;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Particles-Modul: Blocksplitter beim Abbauen und Zerbrechen. */
@Mixin({ClientLevel.class})
public abstract class BlockParticlesMixin {
   @Inject(method = {"addDestroyBlockEffect", "addBreakingBlockEffects"}, at = {@At("HEAD")}, cancellable = true)
   private void meowl$blockParticles(CallbackInfo ci) {
      Particles p = Particles.active();
      if (p != null && !p.blockEffects()) {
         ci.cancel();
      }
   }
}
