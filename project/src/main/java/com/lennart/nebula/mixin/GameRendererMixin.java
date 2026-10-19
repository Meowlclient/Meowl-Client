package com.lennart.nebula.mixin;

import com.lennart.nebula.fx.CameraFx;
import com.lennart.nebula.module.mods.DeathAnimation;
import com.lennart.nebula.module.mods.DesertAfk;
import com.lennart.nebula.module.render.ColorSaturation;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({GameRenderer.class})
public abstract class GameRendererMixin {
   @Shadow
   @Final
   private CrossFrameResourcePool resourcePool;

   @Inject(
      method = {"render(Lnet/minecraft/client/DeltaTracker;Z)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V",
         shift = Shift.AFTER
      )}
   )
   private void meowl$saturation(DeltaTracker tickCounter, boolean tick, CallbackInfo ci) {
      ColorSaturation.apply(Minecraft.getInstance(), this.resourcePool);
   }

   /** Death Animation: „Vanilla-Todesanimation aus“ → beim eigenen Tod kein Kippen der Kamera. */
   @Inject(
      method = {"bobHurt"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void meowl$noDeathTilt(PoseStack matrices, float tickProgress, CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.player.deathTime > 0 && DeathAnimation.vanillaDisabled()) {
         CameraFx.apply(matrices);
         ci.cancel();
      }
   }

   @Inject(
      method = {"bobHurt"},
      at = {@At("TAIL")}
   )
   private void meowl$cameraFx(PoseStack matrices, float tickProgress, CallbackInfo ci) {
      CameraFx.apply(matrices);
   }

   @Inject(
      method = {"getFov(Lnet/minecraft/client/Camera;FZ)F"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void meowl$afkFov(Camera camera, float tickProgress, boolean changingFov, CallbackInfoReturnable<Float> cir) {
      float factor = com.lennart.nebula.fx.FovFx.factor();
      if (factor != 1.0F && changingFov) {
         cir.setReturnValue(cir.getReturnValue() * factor);
      }
   }
}
