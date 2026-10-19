package com.lennart.nebula.compat;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;

/**
 * 26.x-Fassung: Farbziel- und Tiefenzustand werden als eigene Objekte angegeben. Achtung: 26.x nutzt einen umgedrehten
 * Tiefenpuffer (näher = größer), darum GREATER_THAN_OR_EQUAL wie bei Vanilla.
 */
public final class Pipelines {
   private Pipelines() {
   }

   public static RenderPipeline solid(Identifier location) {
      return RenderPipelines.register(
         RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(location)
            .withColorTargetState(0, ColorTargetState.DEFAULT)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(true)
            .build()
      );
   }

   public static RenderPipeline glow(Identifier location) {
      return RenderPipelines.register(
         RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(location)
            .withColorTargetState(0, new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .withCull(false)
            .build()
      );
   }

   public static TextureAtlasSprite guiSprite(Identifier id) {
      return Minecraft.getInstance().getAtlasManager().get(new SpriteId(Sheets.GUI_SHEET, id));
   }
}
