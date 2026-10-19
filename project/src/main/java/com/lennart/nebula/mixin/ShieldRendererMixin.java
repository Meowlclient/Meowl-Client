package com.lennart.nebula.mixin;

import com.lennart.nebula.module.render.ShieldStyle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.object.equipment.ShieldModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.ShieldSpecialRenderer;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shield-Modul: Schild durchsichtig (Render-Typ mit Transparenz) und eingefärbt. */
@Mixin({ShieldSpecialRenderer.class})
public abstract class ShieldRendererMixin {
   @Unique
   private static ItemDisplayContext meowl$context;

   @Inject(
      method = {"submit(Lnet/minecraft/core/component/DataComponentMap;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V"},
      at = {@At("HEAD")}
   )
   private void meowl$remember(
      DataComponentMap components, ItemDisplayContext context, PoseStack matrices, SubmitNodeCollector queue, int light, int overlay, boolean glint, int outline, CallbackInfo ci
   ) {
      meowl$context = context;
   }

   @Redirect(
      method = {"submit(Lnet/minecraft/core/component/DataComponentMap;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/model/object/equipment/ShieldModel;renderType(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"
      )
   )
   private RenderType meowl$seeThrough(ShieldModel model, Identifier atlas) {
      return ShieldStyle.translucent(meowl$context) ? RenderTypes.entityTranslucent(atlas) : model.renderType(atlas);
   }

   @ModifyArg(
      method = {"submit(Lnet/minecraft/core/component/DataComponentMap;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIZI)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModelPart(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ZZILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;I)V"
      ),
      index = 8
   )
   private int meowl$tint(int color) {
      return ShieldStyle.color(meowl$context, color);
   }
}
