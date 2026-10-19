package com.lennart.nebula.mixin;

import com.lennart.nebula.module.mods.DeathAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Death Animation: „Vanilla-Todesanimation aus“ → sterbende Spieler kippen nicht um, sondern verschwinden sofort. */
@Mixin({LivingEntityRenderer.class})
public abstract class VanillaDeathMixin {
   @Inject(
      method = {"submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void meowl$hideDyingPlayer(LivingEntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera, CallbackInfo ci) {
      if (state instanceof AvatarRenderState && state.deathTime > 0.0F && DeathAnimation.vanillaDisabled()) {
         ci.cancel();
      }
   }
}
