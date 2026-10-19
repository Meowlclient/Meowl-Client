package com.lennart.nebula.mixin;

import com.lennart.nebula.module.mods.ThirdPersonNametag;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Minecraft blendet den Nametag der Kamera-Figur aus – in F5 wieder zeigen (Modul „3rd Person Nametag“). */
@Mixin({LivingEntityRenderer.class})
public abstract class ThirdPersonNametagMixin {
   @Inject(method = {"shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z"}, at = {@At("RETURN")}, cancellable = true)
   private void meowl$ownNametag(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
      if (!cir.getReturnValueZ() && ThirdPersonNametag.showOwn(entity)) {
         cir.setReturnValue(true);
      }
   }
}