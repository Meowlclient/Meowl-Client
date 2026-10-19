package com.lennart.nebula.mixin;

import com.lennart.nebula.fx.CameraFx;
import com.lennart.nebula.module.mods.DeathAnimation;
import com.lennart.nebula.module.render.ColorSaturation;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.x-Fassung: Rendern ist in extract() und render() getrennt, Kamera-Wackeln arbeitet auf dem Render-State. */
@Mixin({GameRenderer.class})
public abstract class GameRendererMixin {
   @Shadow
   @Final
   private CrossFrameResourcePool resourcePool;

   @Inject(
      method = {"render()V"},
      at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;blitEntityOutline()V", shift = Shift.AFTER)}
   )
   private void meowl$saturation(CallbackInfo ci) {
      ColorSaturation.apply(Minecraft.getInstance(), this.resourcePool);
   }

   @Inject(method = {"bobHurt"}, at = {@At("HEAD")}, cancellable = true)
   private void meowl$noDeathTilt(CameraRenderState camera, PoseStack matrices, CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.player.deathTime > 0 && DeathAnimation.vanillaDisabled()) {
         CameraFx.apply(matrices);
         ci.cancel();
      }
   }

   @Inject(method = {"bobHurt"}, at = {@At("TAIL")})
   private void meowl$cameraFx(CameraRenderState camera, PoseStack matrices, CallbackInfo ci) {
      CameraFx.apply(matrices);
   }
}
