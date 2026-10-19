package com.lennart.nebula.mixin;

import com.lennart.nebula.emote.EmoteCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.x-Fassung: die Kamera richtet sich in alignWithEntity am Spieler aus. */
@Mixin({Camera.class})
public abstract class CameraEmoteMixin {
   @Shadow
   private boolean detached;
   @Shadow
   private Vec3 position;
   @Shadow
   private Entity entity;

   @Shadow
   protected abstract void setPosition(Vec3 pos);

   @Inject(method = {"alignWithEntity"}, at = {@At("TAIL")})
   private void meowl$vehicleView(float partialTick, CallbackInfo ci) {
      if (!this.detached && this.entity != null) {
         double dy = EmoteCamera.lift(this.entity, this.entity.level(), this.position);
         if (dy != 0.0) {
            this.setPosition(this.position.add(0.0, dy, 0.0));
         }
      }
   }
}
