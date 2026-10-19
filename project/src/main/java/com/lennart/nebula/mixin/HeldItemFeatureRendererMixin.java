package com.lennart.nebula.mixin;

import com.lennart.nebula.module.mods.PlasmaMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemInHandLayer.class})
public abstract class HeldItemFeatureRendererMixin {
   @Inject(
      method = {"submitArmWithItem(Lnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void meowl$plasma(
      ArmedEntityRenderState state,
      ItemStackRenderState itemState,
      ItemStack stack,
      HumanoidArm arm,
      PoseStack matrices,
      SubmitNodeCollector queue,
      int light,
      CallbackInfo ci
   ) {
      if (PlasmaMode.isSword(stack) && state instanceof AvatarRenderState p) {
         Minecraft mc = Minecraft.getInstance();
         Entity holder = mc.level == null ? null : mc.level.getEntity(p.id);
         matrices.pushPose();
         ((ArmedModel)((RenderLayer)(Object)this).getParentModel()).translateToHand(state, arm, matrices);
         matrices.mulPose(Axis.XP.rotationDegrees(-90.0F));
         matrices.mulPose(Axis.YP.rotationDegrees(180.0F));
         boolean left = arm == HumanoidArm.LEFT;
         matrices.translate((left ? -1 : 1) / 16.0F, 0.125F, -0.625F);
         com.lennart.nebula.cosmetic.PreviewOverrides.Entry pe = com.lennart.nebula.cosmetic.PreviewOverrides.get(state);
         boolean drawn = pe != null && pe.target() instanceof PlasmaMode
            ? PlasmaMode.renderPreview(matrices, queue, left, pe.style())
            : PlasmaMode.renderSaber(matrices, queue, stack, holder, left, false, 0.0F);
         matrices.popPose();
         if (drawn) {
            ci.cancel();
         }
      }
   }
}
