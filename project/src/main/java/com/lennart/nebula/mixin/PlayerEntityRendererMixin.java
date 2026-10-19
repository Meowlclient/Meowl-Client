package com.lennart.nebula.mixin;

import com.lennart.nebula.afk.SitPose;
import com.lennart.nebula.emote.EmotePoses;
import com.lennart.nebula.emote.EmoteState;
import com.lennart.nebula.nametag.NametagLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({AvatarRenderer.class})
public abstract class PlayerEntityRendererMixin {
   @Inject(
      method = {"getRenderOffset(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/world/phys/Vec3;"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void meowl$sitOffset(AvatarRenderState state, CallbackInfoReturnable<Vec3> cir) {
      double dy = SitPose.yOffset(state.id, state.isPassenger);
      EmoteState.Active emote = state.isPassenger ? null : EmoteState.forState(state, state.id);
      if (emote != null) {
         dy += EmotePoses.yOffset(emote, EmoteState.now());
         if (emote.type == com.lennart.nebula.emote.EmoteType.ROLLER && state.isCrouching) {
            // Vanilla senkt beim Schleichen ab – auf dem Roller nicht (Wheelie statt Ducken)
            dy += state.scale * 2.0F / 16.0F;
         }
      }

      double back = 0.0;
      if (com.lennart.nebula.emote.EmoteBody.hideHead(state.id)) {
         // Ich-Ansicht: Körper etwas nach hinten, damit die Kamera nicht im Oberkörper steckt
         back = 0.22;
      }

      if (dy != 0.0 || back != 0.0) {
         double yaw = Math.toRadians(state.bodyRot);
         cir.setReturnValue(cir.getReturnValue().add(Math.sin(yaw) * back, dy, -Math.cos(yaw) * back));
      }
   }

   /** E-Scooter + Shift: Spieler samt Roller zum Wheelie hochkippen (sehen alle, Schleichen wird übertragen). */
   @Inject(
      method = {"setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V"},
      at = {@At("TAIL")}
   )
   private void meowl$wheelie(AvatarRenderState state, PoseStack matrices, float bodyRot, float scale, CallbackInfo ci) {
      EmoteState.Active emote = state.isPassenger ? null : EmoteState.forState(state, state.id);
      boolean roller = emote != null && emote.type == com.lennart.nebula.emote.EmoteType.ROLLER;
      float k = com.lennart.nebula.emote.Wheelie.amount(state.id, roller && state.isCrouching);
      if (roller) {
         com.lennart.nebula.emote.Wheelie.tilt(matrices, k, EmoteState.now());
      }
   }

   @Inject(
      method = {"submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void meowl$nametagRow(
      AvatarRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera, CallbackInfo ci
   ) {
      if (NametagLayout.render(state, matrices, queue, camera)) {
         // Meowl zeichnet den Namen selbst – Symbole anderer Mods (z.B. Voice Chat) trotzdem zeichnen lassen
         NametagLayout.drawVoiceIcon(state, matrices, queue, camera);
         ci.cancel();
      }
   }
}
