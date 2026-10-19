package com.lennart.nebula.compat;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.Identifier;

/** Render-Pipelines und Atlas-Zugriff (je Minecraft-Version unterschiedlich aufgebaut). */
public final class Pipelines {
   private Pipelines() {
   }

   /** Undurchsichtige Farbflächen mit Tiefenpuffer und Rückseiten-Culling. */
   public static RenderPipeline solid(Identifier location) {
      return RenderPipelines.register(
         RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET).withLocation(location).withoutBlend().withDepthWrite(true).withCull(true).build()
      );
   }

   /** Leuchtende Flächen: additiv gemischt, ohne Tiefe zu schreiben, beidseitig. */
   public static RenderPipeline glow(Identifier location) {
      return RenderPipelines.register(
         RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET).withLocation(location).withBlend(BlendFunction.LIGHTNING).withDepthWrite(false).withCull(false).build()
      );
   }

   public static TextureAtlasSprite guiSprite(Identifier id) {
      return Minecraft.getInstance().getAtlasManager().get(new Material(Sheets.GUI_SHEET, id));
   }
}
