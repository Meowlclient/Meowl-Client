package com.lennart.nebula.mixin;

import com.lennart.nebula.module.mods.Freelook;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Freelook: Kamera nutzt die freien Winkel statt der Blickrichtung des Spielers. */
@Mixin({Camera.class})
public abstract class CameraFreelookMixin {
   @ModifyVariable(method = {"setRotation"}, at = @At("HEAD"), argsOnly = true, ordinal = 0)
   private float meowl$freelookYaw(float yaw) {
      return Freelook.active() ? Freelook.yaw() : yaw;
   }

   @ModifyVariable(method = {"setRotation"}, at = @At("HEAD"), argsOnly = true, ordinal = 1)
   private float meowl$freelookPitch(float pitch) {
      return Freelook.active() ? Freelook.pitch() : pitch;
   }
}
