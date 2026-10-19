package com.lennart.nebula.mixin;

import com.lennart.nebula.module.render.ShieldStyle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 26.x-Fassung des Shield-Moduls: das normale Schild wird mit eigenem Render-Typ (durchsichtig) und Farbe gezeichnet.
 * Die Version verrät nicht, ob es die Ego-Sicht ist – die Einstellung gilt dann überall in der Hand.
 */
@Mixin({ShieldSpecialRenderer.class})
public abstract class ShieldRendererMixin {
   @SuppressWarnings({"unchecked", "rawtypes"})
   @Redirect(
      method = {"submit(Lnet/minecraft/core/component/DataComponentMap;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;IIILnet/minecraft/client/resources/model/sprite/SpriteId;Lnet/minecraft/client/resources/model/sprite/SpriteGetter;I)V"
      )
   )
   private void meowl$shield(
      SubmitNodeCollector queue, Model model, Object state, PoseStack matrices, int light, int overlay, int tint, SpriteId sprite, SpriteGetter sprites, int outline
   ) {
      int color = ShieldStyle.color(null, tint);
      if (color == tint) {
         queue.submitModel(model, state, matrices, light, overlay, tint, sprite, sprites, outline);
      } else {
         RenderType type = ShieldStyle.translucent(null) ? RenderTypes.entityTranslucent(sprite.atlasLocation()) : model.renderType(sprite.atlasLocation());
         queue.submitModel(model, state, matrices, type, light, overlay, color, sprites.get(sprite), outline);
      }
   }
}
