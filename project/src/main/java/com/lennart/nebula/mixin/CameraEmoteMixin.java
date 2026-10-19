package com.lennart.nebula.mixin;

import com.lennart.nebula.emote.EmoteCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fahrzeug-Emotes: Ich-Ansicht auf der Sitzhöhe im Fahrzeug (siehe {@link EmoteCamera}). */
@Mixin({Camera.class})
public abstract class CameraEmoteMixin {
   @Shadow
   private boolean detached;
   @Shadow
   private Vec3 position;

   @Shadow
   protected abstract void setPosition(Vec3 pos);

   @Inject(method = {"setup"}, at = {@At("TAIL")})
   private void meowl$vehicleView(Level level, Entity entity, boolean detached, boolean mirror, float partialTick, CallbackInfo ci) {
      if (!this.detached) {
         double dy = EmoteCamera.lift(entity, level, this.position);
         if (dy != 0.0) {
            this.setPosition(this.position.add(0.0, dy, 0.0));
         }
      }
   }
}
