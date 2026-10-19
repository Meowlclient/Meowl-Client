package com.lennart.nebula.mixin;

import com.lennart.nebula.duel.DuelManager;
import com.lennart.nebula.fx.FirstPersonFx;
import com.lennart.nebula.module.mods.PlasmaMode;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemInHandRenderer.class})
public abstract class HeldItemRendererMixin {
   @org.spongepowered.asm.mixin.Shadow
   private ItemStack mainHandItem;
   @org.spongepowered.asm.mixin.Shadow
   private ItemStack offHandItem;
   @org.spongepowered.asm.mixin.Shadow
   private float mainHandHeight;
   @org.spongepowered.asm.mixin.Shadow
   private float oMainHandHeight;
   @org.spongepowered.asm.mixin.Shadow
   private float offHandHeight;
   @org.spongepowered.asm.mixin.Shadow
   private float oOffHandHeight;

   /** Emote in der Ich-Ansicht: der ganze Körper wird gezeichnet, die normalen Ego-Hände wären doppelt. */
   @Inject(
      method = {"renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void meowl$emoteBody(float tickProgress, PoseStack matrices, SubmitNodeCollector queue, LocalPlayer player, int light, CallbackInfo ci) {
      if (com.lennart.nebula.emote.EmoteBody.showOwnBody()) {
         ci.cancel();
      }
   }

   /** Ingame-Vorschau aus dem Menü: Vorschau-Item statt des echten in die Hand legen (nur optisch). */
   @Inject(
      method = {"renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V"},
      at = {@At("HEAD")}
   )
   private void meowl$ingamePreview(float tickProgress, PoseStack matrices, SubmitNodeCollector queue, LocalPlayer player, int light, CallbackInfo ci) {
      ItemStack main = com.lennart.nebula.gui.IngamePreview.override(false);
      if (main != null) {
         this.mainHandItem = main;
         this.mainHandHeight = 1.0F;
         this.oMainHandHeight = 1.0F;
      }

      ItemStack off = com.lennart.nebula.gui.IngamePreview.override(true);
      if (off != null) {
         this.offHandItem = off;
         this.offHandHeight = 1.0F;
         this.oOffHandHeight = 1.0F;
      }
   }

   @Inject(
      method = {"renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void nebula$sheriff(float tickProgress, PoseStack matrices, SubmitNodeCollector queue, LocalPlayer player, int light, CallbackInfo ci) {
      if (DuelManager.isWeaponActive()) {
         DuelManager.renderFirstPerson(matrices, queue, tickProgress);
         Minecraft mc = Minecraft.getInstance();
         mc.gameRenderer.getFeatureRenderDispatcher().renderAllFeatures();
         mc.renderBuffers().bufferSource().endBatch();
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderArmWithItem"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void meowl$clapArm(
      AbstractClientPlayer player,
      float tickProgress,
      float pitch,
      InteractionHand hand,
      float swingProgress,
      ItemStack item,
      float equipProgress,
      PoseStack matrices,
      SubmitNodeCollector queue,
      int light,
      CallbackInfo ci
   ) {
      if (hand == InteractionHand.MAIN_HAND && !player.isInvisible()) {
         boolean right = player.getMainArm() == HumanoidArm.RIGHT;
         matrices.pushPose();
         if (FirstPersonFx.applyArm(matrices, tickProgress, right)) {
            ((HeldItemRendererAccessor)this).meowl$renderArm(matrices, queue, light, 0.0F, 0.0F, player.getMainArm());
            matrices.popPose();
            ci.cancel();
         } else {
            matrices.popPose();
         }
      }
   }

   /** Karambit: Hand wie ohne Item zeichnen und das Messer in die Faust setzen. */
   @Inject(
      method = {"renderArmWithItem"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void meowl$karambit(
      AbstractClientPlayer player,
      float tickProgress,
      float pitch,
      InteractionHand hand,
      float swingProgress,
      ItemStack item,
      float equipProgress,
      PoseStack matrices,
      SubmitNodeCollector queue,
      int light,
      CallbackInfo ci
   ) {
      if (hand == InteractionHand.MAIN_HAND && !player.isInvisible() && com.lennart.nebula.fx.KarambitHand.active(item)) {
         boolean right = player.getMainArm() == HumanoidArm.RIGHT;
         matrices.pushPose();
         com.lennart.nebula.fx.KarambitHand.armMotion(matrices, right);
         ((HeldItemRendererAccessor)this).meowl$renderArm(matrices, queue, light, equipProgress, swingProgress, player.getMainArm());
         com.lennart.nebula.fx.KarambitHand.drawKnife(matrices, queue, right);
         matrices.popPose();
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderArmWithItem"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void meowl$offArm(
      AbstractClientPlayer player,
      float tickProgress,
      float pitch,
      InteractionHand hand,
      float swingProgress,
      ItemStack item,
      float equipProgress,
      PoseStack matrices,
      SubmitNodeCollector queue,
      int light,
      CallbackInfo ci
   ) {
      if (hand == InteractionHand.OFF_HAND && item.isEmpty() && !player.isInvisible()) {
         boolean right = player.getMainArm() == HumanoidArm.RIGHT;
         matrices.pushPose();
         if (FirstPersonFx.applyOffArm(matrices, tickProgress, right)) {
            ((HeldItemRendererAccessor)this).meowl$renderArm(matrices, queue, light, 0.0F, 0.0F, player.getMainArm().getOpposite());
            matrices.popPose();
            ci.cancel();
         } else {
            matrices.popPose();
         }
      }
   }

   @Inject(
      method = {"renderArmWithItem"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"
      )}
   )
   private void meowl$inspect(
      AbstractClientPlayer player,
      float tickProgress,
      float pitch,
      InteractionHand hand,
      float swingProgress,
      ItemStack item,
      float equipProgress,
      PoseStack matrices,
      SubmitNodeCollector queue,
      int light,
      CallbackInfo ci
   ) {
      float totem = com.lennart.nebula.module.render.TotemSize.handScale(item);
      if (totem != 1.0F) {
         matrices.scale(totem, totem, totem);
      }

      if (hand == InteractionHand.MAIN_HAND) {
         FirstPersonFx.applyItem(matrices, tickProgress, player.getMainArm() == HumanoidArm.RIGHT);

      }
   }

   @Inject(
      method = {"renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void meowl$plasmaFirstPerson(
      LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack matrices, SubmitNodeCollector queue, int light, CallbackInfo ci
   ) {
      if (PlasmaMode.isSword(stack)
         && (context.firstPerson() || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
         Minecraft mc = Minecraft.getInstance();
         boolean local = entity == mc.player;
         float swing = local && context.firstPerson() ? com.lennart.nebula.compat.Compat.attackAnim(mc.player, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)) : 0.0F;
         if (PlasmaMode.renderSaber(matrices, queue, stack, entity, context.leftHand(), context.firstPerson(), swing)) {
            ci.cancel();
         }
      }
   }
}
