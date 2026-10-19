package com.lennart.nebula.mixin;

import com.lennart.nebula.module.render.ArmorView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rüstungs-Modul: Teile ausblenden und die Deckkraft für das Zeichnen merken (siehe {@link EquipmentLayerAlphaMixin}). */
@Mixin({HumanoidArmorLayer.class})
public abstract class ArmorLayerMixin {
   @Inject(method = "renderArmorPiece", at = @At("HEAD"), cancellable = true)
   private void meowl$armorHead(PoseStack matrices, SubmitNodeCollector queue, ItemStack stack, EquipmentSlot slot, int light, HumanoidRenderState state, CallbackInfo ci) {
      ArmorView.currentAlpha = 1.0F;
      if (state instanceof AvatarRenderState ps) {
         float a = ArmorView.alpha(ps.id);
         if (ArmorView.hidden(ps.id, slot) || a <= 0.01F) {
            ci.cancel();
            return;
         }

         ArmorView.currentAlpha = a;
      }
   }

   @Inject(method = "renderArmorPiece", at = @At("RETURN"))
   private void meowl$armorReturn(PoseStack matrices, SubmitNodeCollector queue, ItemStack stack, EquipmentSlot slot, int light, HumanoidRenderState state, CallbackInfo ci) {
      ArmorView.currentAlpha = 1.0F;
   }
}
