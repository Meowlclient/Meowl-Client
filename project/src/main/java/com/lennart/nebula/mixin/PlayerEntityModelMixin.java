package com.lennart.nebula.mixin;

import com.lennart.nebula.afk.SitPose;
import com.lennart.nebula.emote.EmotePoses;
import com.lennart.nebula.emote.EmoteState;
import com.lennart.nebula.module.mods.Clap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({PlayerModel.class})
public abstract class PlayerEntityModelMixin {
   @Inject(
      method = {"setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V"},
      at = {@At("TAIL")}
   )
   private void meowl$clapPose(AvatarRenderState state, CallbackInfo ci) {
      SitPose.applyModel((HumanoidModel<?>)(Object)this, state, state.id);
      EmoteState.Active emote = EmoteState.forState(state, state.id);
      if (emote != null && !state.isPassenger && com.lennart.nebula.module.mods.OtherClients.showMeowlEmotes()) {
         EmotePoses.apply((HumanoidModel<?>)(Object)this, emote, EmoteState.now(), Math.min(1.0F, state.walkAnimationSpeed * 1.6F));
         // E-Scooter + Shift: Wheelie-Haltung (Ducken aufheben)
         if (emote.type == com.lennart.nebula.emote.EmoteType.ROLLER && state.isCrouching) {
            com.lennart.nebula.emote.Wheelie.pose((HumanoidModel<?>)(Object)this, com.lennart.nebula.emote.Wheelie.amount(state.id, true));
         }
      }

      // Emote in der Ich-Ansicht: die Kamera sitzt im Kopf, also Kopf ausblenden
      ((HumanoidModel<?>)(Object)this).head.visible = !com.lennart.nebula.emote.EmoteBody.hideHead(state.id);
      float[] p = Clap.modelPose(state.id);
      if (p != null) {
         float raise = p[0];
         float pull = p[1];
         float forward = p[2];
         float kick = p[3];
         HumanoidModel<?> model = (HumanoidModel<?>)(Object)this;
         boolean right = state.mainArm != HumanoidArm.LEFT;
         ModelPart arm = right ? model.rightArm : model.leftArm;
         float side = right ? 1.0F : -1.0F;
         float pitch = -2.55F - 0.45F * pull + 0.55F * forward - 0.15F * kick;
         float yaw = -0.25F * side * (1.0F - forward * 0.5F);
         float roll = 0.12F * side;
         arm.xRot = arm.xRot + (pitch - arm.xRot) * raise;
         arm.yRot = arm.yRot + (yaw - arm.yRot) * raise;
         arm.zRot = arm.zRot + (roll - arm.zRot) * raise;
      }
   }
}
