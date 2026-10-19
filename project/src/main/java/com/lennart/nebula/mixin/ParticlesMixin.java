package com.lennart.nebula.mixin;

import com.lennart.nebula.module.render.Particles;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Particles-Modul: Partikel je Art weglassen oder vervielfachen. */
@Mixin({ParticleEngine.class})
public abstract class ParticlesMixin {
   @Inject(
      method = {"createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void meowl$particleAmount(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
      Particles p = Particles.active();
      if (p != null) {
         int n = p.copies(options, x, y, z);
         if (n <= 0) {
            cir.setReturnValue(null);
         } else if (n > 1) {
            p.beginExtra();

            try {
               ParticleEngine self = (ParticleEngine)(Object)this;

               for (int i = 1; i < n; i++) {
                  double s = 0.15 * i;
                  self.createParticle(options, x + (Math.random() - 0.5) * s, y + (Math.random() - 0.5) * s, z + (Math.random() - 0.5) * s, vx, vy, vz);
               }
            } finally {
               p.endExtra();
            }
         }
      }
   }
}
