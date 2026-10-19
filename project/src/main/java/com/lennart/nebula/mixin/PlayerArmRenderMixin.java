package com.lennart.nebula.mixin;

import com.lennart.nebula.module.mods.Watches;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AvatarRenderer.class})
public abstract class PlayerArmRenderMixin {
   @Inject(
      method = {"renderHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Lnet/minecraft/client/model/geom/ModelPart;Z)V"},
      at = {@At("TAIL")}
   )
   private void meowl$watch(PoseStack matrices, SubmitNodeCollector queue, int light, Identifier skin, ModelPart arm, boolean sleeve, CallbackInfo ci) {
      PlayerModel model = (PlayerModel)((AvatarRenderer)(Object)this).getModel();
      Watches w = Watches.active();
      if (w != null) {
         w.renderFirstPersonArm(matrices, queue, arm, arm == model.rightArm);
      }

      net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
      boolean right = arm == model.rightArm;
      if (player != null && right == (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT)) {
         com.lennart.nebula.cosmetic.mods.Hands.renderFirstPerson(matrices, queue, arm, right);
      }
   }
}
