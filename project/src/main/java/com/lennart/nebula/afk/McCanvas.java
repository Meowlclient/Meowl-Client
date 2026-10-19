package com.lennart.nebula.afk;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class McCanvas implements Canvas {
   private final Map<String, Identifier> textures = new HashMap<>();
   private GuiGraphicsExtractor ctx;

   public McCanvas bind(GuiGraphicsExtractor ctx) {
      this.ctx = ctx;
      return this;
   }

   @Override
   public void fill(int x1, int y1, int x2, int y2, int argb) {
      this.ctx.fill(x1, y1, x2, y2, argb);
   }

   @Override
   public void gradient(int x1, int y1, int x2, int y2, int top, int bottom) {
      if (y2 > y1) {
         this.ctx.fillGradient(x1, y1, x2, y2, top, bottom);
      }
   }

   @Override
   public void texture(String blockTexture, int x, int y, int w, int h, int tint) {
      if (w > 0 && h > 0) {
         Identifier id = this.textures.computeIfAbsent(blockTexture, n -> Identifier.withDefaultNamespace("textures/block/" + n + ".png"));
         this.ctx.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0.0F, 0.0F, w, h, 16, 16, 16, 16, tint);
      }
   }

   @Override
   public void scissor(int x1, int y1, int x2, int y2) {
      this.ctx.enableScissor(x1, y1, x2, y2);
   }

   @Override
   public void noScissor() {
      this.ctx.disableScissor();
   }
}
