package com.lennart.nebula.module.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class VanillaSlots {
   public static final Identifier HOTBAR = Identifier.withDefaultNamespace("hud/hotbar");
   public static final int CELL = 20;
   public static final int HEIGHT = 22;

   private VanillaSlots() {
   }

   public static int stripWidth(int n) {
      return n * 20 + 2;
   }

   public static void strip(GuiGraphics ctx, int x, int y, int n, int color) {
      n = Math.max(1, Math.min(9, n));
      int w = n * 20 + 1;
      ctx.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, 182, 22, 0, 0, x, y, w, 22, color);
      ctx.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, 182, 22, 181, 0, x + w, y, 1, 22, color);
   }

   public static void cell(GuiGraphics ctx, int x, int y, int color) {
      strip(ctx, x, y, 1, color);
   }

   public static int white(float alpha) {
      return Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F) << 24 | 16777215;
   }
}
