package com.lennart.nebula.mixin;

import com.lennart.nebula.module.render.ArmorView;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Rüstungs-Modul: durchsichtige Rüstung (Render-Typ mit Transparenz und Alpha in der Farbe). */
@Mixin({EquipmentLayerRenderer.class})
public abstract class EquipmentLayerAlphaMixin {
   private static final String RENDER_LAYERS = "renderLayers(Lnet/minecraft/client/resources/model/EquipmentClientInfo$LayerType;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;II)V";

   @Redirect(
      method = RENDER_LAYERS,
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;armorCutoutNoCull(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;")
   )
   private RenderType meowl$translucentArmor(Identifier texture) {
      return ArmorView.currentAlpha < 0.999F ? RenderTypes.entityTranslucent(texture) : RenderTypes.armorCutoutNoCull(texture);
   }

   /** Rüstungs-Trims ebenfalls durchsichtig (gleicher Atlas, Render-Typ mit Transparenz). */
   @Redirect(
      method = RENDER_LAYERS,
      at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Sheets;armorTrimsSheet(Z)Lnet/minecraft/client/renderer/rendertype/RenderType;")
   )
   private RenderType meowl$translucentTrims(boolean decal) {
      return ArmorView.currentAlpha < 0.999F
         ? RenderTypes.entityTranslucent(net.minecraft.client.renderer.Sheets.ARMOR_TRIMS_SHEET)
         : net.minecraft.client.renderer.Sheets.armorTrimsSheet(decal);
   }

   @ModifyArg(
      method = RENDER_LAYERS,
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"
      ),
      index = 6
   )
   private int meowl$armorAlpha(int color) {
      return ArmorView.withAlpha(color);
   }
}
