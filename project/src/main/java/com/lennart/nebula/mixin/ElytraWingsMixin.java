package com.lennart.nebula.mixin;

import com.lennart.nebula.module.mods.Wings;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Werden die Wings als Elytra getragen, wird die normale Elytra nicht gezeichnet. */
@Mixin(WingsLayer.class)
public class ElytraWingsMixin {
   @Inject(
      method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void nebula$wingsAsElytra(PoseStack matrices, SubmitNodeCollector queue, int light, HumanoidRenderState state, float a, float b, CallbackInfo ci) {
      if (state instanceof AvatarRenderState ps && Wings.replacesElytra(ps.id)) {
         ci.cancel();
      }
   }
}
