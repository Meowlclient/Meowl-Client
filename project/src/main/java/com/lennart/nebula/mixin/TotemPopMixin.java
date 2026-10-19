package com.lennart.nebula.mixin;

import com.lennart.nebula.module.render.TotemSize;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.x-Fassung: die Totem-Animation bekommt zusätzlich den Render-State des Spielers. */
@Mixin({ScreenEffectRenderer.class})
public abstract class TotemPopMixin {
   @Inject(method = {"renderItemActivationAnimation"}, at = {@At("HEAD")}, cancellable = true)
   private void meowl$hidePop(PlayerRenderState player, PoseStack matrices, float tickDelta, SubmitNodeCollector queue, CallbackInfo ci) {
      if (TotemSize.popScale() <= 0.0F) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderItemActivationAnimation"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
      )}
   )
   private void meowl$popSize(PlayerRenderState player, PoseStack matrices, float tickDelta, SubmitNodeCollector queue, CallbackInfo ci) {
      float s = TotemSize.popScale();
      if (s != 1.0F) {
         matrices.scale(s, s, s);
      }
   }
}
