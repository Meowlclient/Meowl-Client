package com.lennart.nebula.nametag;

import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fStack;

public final class GuiCanvas implements NameCanvas {
   private final GuiGraphicsExtractor ctx;
   private final Font tr = Minecraft.getInstance().font;
   private final int backgroundColor;
   private final BiConsumer<Object, float[]> onElement;

   public GuiCanvas(GuiGraphicsExtractor ctx, int backgroundColor, BiConsumer<Object, float[]> onElement) {
      this.ctx = ctx;
      this.backgroundColor = backgroundColor;
      this.onElement = onElement;
   }

   @Override
   public int backgroundColor() {
      return this.backgroundColor;
   }

   private static int opaque(int c) {
      return c >>> 24 == 0 ? c | 0xFF000000 : c;
   }

   @Override
   public void text(FormattedCharSequence text, float x, float y, int color) {
      Matrix3x2fStack m = this.ctx.pose();
      m.pushMatrix();
      m.translate(x, y);
      this.ctx.text(this.tr, text, 0, 0, opaque(color), false);
      m.popMatrix();
   }

   @Override
   public void vanillaName(FormattedCharSequence text, float x, float y, int color, boolean background) {
      if (background) {
         this.background(x - 1.0F, y - 1.0F, x + this.tr.width(text), y + 9.0F, this.backgroundColor);
      }

      this.text(text, x, y, color);
   }

   @Override
   public void icon(Identifier texture, float x, float y, float size, int tint) {
      Matrix3x2fStack m = this.ctx.pose();
      m.pushMatrix();
      m.translate(x, y);
      m.scale(size, size);
      this.ctx.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0F, 0.0F, 1, 1, 1, 1, 1, 1, opaque(tint));
      m.popMatrix();
   }

   @Override
   public void background(float x1, float y1, float x2, float y2, int color) {
      if (color >>> 24 != 0 && x2 > x1) {
         Matrix3x2fStack m = this.ctx.pose();
         m.pushMatrix();
         m.translate(x1, y1);
         m.scale(x2 - x1, y2 - y1);
         this.ctx.fill(0, 0, 1, 1, color);
         m.popMatrix();
      }
   }

   @Override
   public void element(Object tag, float x, float y, float w, float h) {
      if (this.onElement != null) {
         this.onElement.accept(tag, new float[]{x, y, w, h});
      }
   }
}
