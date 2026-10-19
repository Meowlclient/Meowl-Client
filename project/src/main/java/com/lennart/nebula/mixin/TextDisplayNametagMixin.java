package com.lennart.nebula.mixin;

import com.lennart.nebula.nametag.ServerNametags;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.DisplayRenderer.TextDisplayRenderer;
import net.minecraft.client.renderer.entity.state.TextDisplayEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({TextDisplayRenderer.class})
public abstract class TextDisplayNametagMixin {
   @Inject(
      method = {"submitInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void meowl$replaceServerNametag(
      TextDisplayEntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, int light, float tickProgress, CallbackInfo ci
   ) {
      if (ServerNametags.hideTextDisplay(state)) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"submitInner(Lnet/minecraft/client/renderer/entity/state/TextDisplayEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IF)V"},
      at = {@At("TAIL")}
   )
   private void meowl$serverNametag(
      TextDisplayEntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, int light, float tickProgress, CallbackInfo ci
   ) {
      ServerNametags.renderTextDisplay(state, matrices, queue, light);
   }
}
