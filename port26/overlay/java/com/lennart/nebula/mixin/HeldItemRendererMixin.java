package com.lennart.nebula.mixin;

import com.lennart.nebula.duel.DuelManager;
import com.lennart.nebula.fx.FirstPersonFx;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.x-Fassung: die Hand wird aus Render-States gezeichnet, Spielerdaten kommen direkt vom lokalen Spieler. */
@Mixin({FirstPersonHandsAndItemsRenderer.class})
public abstract class HeldItemRendererMixin {
   /** Emote in der Ich-Ansicht: der ganze Körper wird gezeichnet, die normalen Ego-Hände wären doppelt. */
   @Inject(method = {"submitHandsWithItems"}, at = {@At("HEAD")}, cancellable = true)
   private void meowl$emoteBody(
      float tickProgress, PoseStack matrices, SubmitNodeCollector queue, PlayerRenderState state, FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci
   ) {
      if (com.lennart.nebula.emote.EmoteBody.showOwnBody()) {
         ci.cancel();
      }
   }

   /** Ingame-Vorschau aus dem Menü: Vorschau-Item statt des echten in die Hand legen (nur optisch). */
   @Inject(method = {"submitHandsWithItems"}, at = {@At("HEAD")})
   private void meowl$ingamePreview(
      float tickProgress, PoseStack matrices, SubmitNodeCollector queue, PlayerRenderState state, FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci
   ) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null) {
         return;
      }

      boolean rightMain = player.getMainArm() == HumanoidArm.RIGHT;
      ItemStack main = com.lennart.nebula.gui.IngamePreview.override(false);
      if (main != null) {
         hands.mainHandItem = main;
         hands.mainHandHeight = 1.0F;
         hands.oldMainHandHeight = 1.0F;
         mc.getItemModelResolver()
            .updateForTopItem(
               hands.mainHandRenderState,
               main,
               rightMain ? net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
               player.level(),
               player,
               0
            );
      }

      ItemStack off = com.lennart.nebula.gui.IngamePreview.override(true);
      if (off != null) {
         hands.offHandItem = off;
         hands.offHandHeight = 1.0F;
         hands.oldOffHandHeight = 1.0F;
         mc.getItemModelResolver()
            .updateForTopItem(
               hands.offHandRenderState,
               off,
               rightMain ? net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND : net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
               player.level(),
               player,
               0
            );
      }
   }

   @Inject(method = {"submitHandsWithItems"}, at = {@At("HEAD")}, cancellable = true)
   private void nebula$sheriff(
      float tickProgress, PoseStack matrices, SubmitNodeCollector queue, PlayerRenderState state, FirstPersonHandsAndItemsRenderState hands, CallbackInfo ci
   ) {
      if (DuelManager.isWeaponActive()) {
         DuelManager.renderFirstPerson(matrices, queue, tickProgress);
         ci.cancel();
      }
   }

   @Inject(method = {"submitArmWithItem"}, at = {@At("HEAD")}, cancellable = true)
   private void meowl$arms(
      PlayerRenderState state,
      FirstPersonHandsAndItemsRenderState hands,
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
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null || player.isInvisible()) {
         return;
      }
      HumanoidArm main = player.getMainArm();
      boolean right = main == HumanoidArm.RIGHT;
      boolean mainHand = hand == InteractionHand.MAIN_HAND;
      if (mainHand && com.lennart.nebula.fx.KarambitHand.active(item)) {
         // Karambit: Hand wie ohne Item, Messer im Rückwärtsgriff in der Faust
         matrices.pushPose();
         com.lennart.nebula.fx.KarambitHand.armMotion(matrices, right);
         ((HeldItemRendererAccessor)this).meowl$renderArm(matrices, queue, light, equipProgress, swingProgress, main, state);
         com.lennart.nebula.fx.KarambitHand.drawKnife(matrices, queue, right);
         matrices.popPose();
         ci.cancel();
         return;
      }

      if (!mainHand && !item.isEmpty()) {
         return;
      }
      matrices.pushPose();
      boolean posed = mainHand ? FirstPersonFx.applyArm(matrices, tickProgress, right) : FirstPersonFx.applyOffArm(matrices, tickProgress, right);
      if (posed) {
         ((HeldItemRendererAccessor)this).meowl$renderArm(matrices, queue, light, 0.0F, 0.0F, mainHand ? main : main.getOpposite(), state);
         matrices.popPose();
         ci.cancel();
      } else {
         matrices.popPose();
      }
   }

   /** Gerade gezeichnete Hand und ihr Item (für das Laserschwert und die Totem-Größe beim Zeichnen des Items). */
   private static InteractionHand meowl$hand;
   private static ItemStack meowl$item = ItemStack.EMPTY;

   @Inject(method = {"submitArmWithItem"}, at = {@At("HEAD")})
   private void meowl$remember(
      PlayerRenderState state,
      FirstPersonHandsAndItemsRenderState hands,
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
      meowl$hand = hand;
      meowl$item = item;
   }

   /** Item in der Hand: Laserschwert selbst zeichnen, Totem-Größe und Uhr-Inspect anwenden. */
   @Redirect(
      method = {"submitArmWithItem"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
      )
   )
   private void meowl$item(
      net.minecraft.client.renderer.item.ItemStackRenderState itemState, PoseStack matrices, SubmitNodeCollector queue, int light, int overlay, int outline
   ) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      ItemStack item = meowl$item;
      if (player != null && item != null && !item.isEmpty()) {
         HumanoidArm arm = meowl$hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
         float totem = com.lennart.nebula.module.render.TotemSize.handScale(item);
         if (totem != 1.0F) {
            matrices.scale(totem, totem, totem);
         }

         if (meowl$hand == InteractionHand.MAIN_HAND) {
            FirstPersonFx.applyItem(matrices, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true), player.getMainArm() == HumanoidArm.RIGHT);
         }

         if (com.lennart.nebula.module.mods.PlasmaMode.isSword(item)) {
            float swing = com.lennart.nebula.compat.Compat.attackAnim(player, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
            if (com.lennart.nebula.module.mods.PlasmaMode.renderSaber(matrices, queue, item, player, arm == HumanoidArm.LEFT, true, swing)) {
               return;
            }
         }
      }

      itemState.submit(matrices, queue, light, overlay, outline);
   }
}