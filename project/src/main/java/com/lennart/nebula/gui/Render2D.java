package com.lennart.nebula.gui;

import com.lennart.nebula.util.DynamicTextures;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class Render2D {
   private Render2D() {
   }

   public static void rect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
      ctx.fill(x, y, x + w, y + h, color);
   }

   public static void roundRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int radius, int color) {
      int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
      if (r == 0) {
         rect(ctx, x, y, w, h, color);
      } else {
         Render2D.Shape shape = Render2D.Shape.of(r);
         if (shape == null) {
            roundRectScanline(ctx, x, y, w, h, r, color);
         } else {
            ctx.fill(x + r, y, x + w - r, y + h, color);
            ctx.fill(x, y + r, x + r, y + h - r, color);
            ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
            int R = shape.half;
            ctx.blit(RenderPipelines.GUI_TEXTURED, shape.id, x, y, 0.0F, 0.0F, r, r, R, R, shape.size, shape.size, color);
            ctx.blit(RenderPipelines.GUI_TEXTURED, shape.id, x + w - r, y, R, 0.0F, r, r, R, R, shape.size, shape.size, color);
            ctx.blit(RenderPipelines.GUI_TEXTURED, shape.id, x, y + h - r, 0.0F, R, r, r, R, R, shape.size, shape.size, color);
            ctx.blit(RenderPipelines.GUI_TEXTURED, shape.id, x + w - r, y + h - r, R, R, r, r, R, R, shape.size, shape.size, color);
         }
      }
   }

   private static void roundRectScanline(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int r, int color) {
      ctx.fill(x + r, y, x + w - r, y + h, color);
      ctx.fill(x, y + r, x + r, y + h - r, color);
      ctx.fill(x + w - r, y + r, x + w, y + h - r, color);

      for (int i = 0; i < r; i++) {
         double dy = r - i - 0.5;
         int inset = (int)Math.round(r - Math.sqrt(Math.max(0.0, r * r - dy * dy)));
         ctx.fill(x + inset, y + i, x + r, y + i + 1, color);
         ctx.fill(x + w - r, y + i, x + w - inset, y + i + 1, color);
         ctx.fill(x + inset, y + h - i - 1, x + r, y + h - i, color);
         ctx.fill(x + w - r, y + h - i - 1, x + w - inset, y + h - i, color);
      }
   }

   public static void toggleSwitch(GuiGraphicsExtractor ctx, int x, int y, float progress) {
      int w = 26;
      int h = 14;
      roundRect(ctx, x, y, w, h, 7, Theme.lerp(-14012603, -7643914, progress));
      int knobX = x + 2 + Math.round((w - 14) * progress);
      roundRect(ctx, knobX, y + 2, 10, 10, 5, Theme.lerp(-4604205, -1, progress));
   }

   public static int pill(GuiGraphicsExtractor ctx, Font tr, String text, int rightX, int y, int bg, int fg) {
      int w = tr.width(text) + 10;
      roundRect(ctx, rightX - w, y, w, 13, 4, bg);
      ctx.text(tr, text, rightX - w + 5, y + 3, fg, false);
      return w;
   }

   public static void circle(GuiGraphicsExtractor ctx, int cx, int cy, int r, int color) {
      if (r > 0) {
         Render2D.Shape shape = Render2D.Shape.of(r);
         if (shape == null) {
            roundRect(ctx, cx - r, cy - r, r * 2, r * 2, r, color);
         } else {
            ctx.blit(
               RenderPipelines.GUI_TEXTURED, shape.id, cx - r, cy - r, 0.0F, 0.0F, r * 2, r * 2, shape.size, shape.size, shape.size, shape.size, color
            );
         }
      }
   }

   public static void ring(GuiGraphicsExtractor ctx, int cx, int cy, int r, int thickness, int color, int inner) {
      circle(ctx, cx, cy, r, color);
      circle(ctx, cx, cy, r - thickness, inner);
   }

   public static void arc(GuiGraphicsExtractor ctx, int cx, int cy, int r, float progress, int color) {
      if (!(progress <= 0.0F)) {
         int steps = Math.max(12, r * 5);
         int filled = Math.round(steps * Math.min(1.0F, progress));

         for (int i = 0; i < filled; i++) {
            double ang = (-Math.PI / 2) + i * ((Math.PI * 2) / steps);
            int x = cx + (int)Math.round(Math.cos(ang) * r);
            int y = cy + (int)Math.round(Math.sin(ang) * r);
            ctx.fill(x, y, x + 1, y + 1, color);
         }
      }
   }

   public static void glow(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int radius, int rgb, float strength) {
      if (!(strength <= 0.01F)) {
         for (int i = 3; i >= 1; i--) {
            int a = Math.round(18.0F * strength / i);
            roundRect(ctx, x - i * 2, y - i * 2, w + i * 4, h + i * 4, radius + i * 2, a << 24 | rgb & 16777215);
         }
      }
   }

   public static int alpha(int argb, float factor) {
      int a = Math.round((argb >>> 24 & 0xFF) * Math.max(0.0F, Math.min(1.0F, factor)));
      return a << 24 | argb & 16777215;
   }

   public static boolean inside(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }

   private static final class Shape {
      private static final Map<Integer, Render2D.Shape> CACHE = new HashMap<>();
      final Identifier id;
      final int size;
      final int half;

      private Shape(Identifier id, int size) {
         this.id = id;
         this.size = size;
         this.half = size / 2;
      }

      static Render2D.Shape of(int radius) {
         int scale = Math.max(1, Math.round((float)Minecraft.getInstance().getWindow().getGuiScale()));
         if (radius <= 64 && scale <= 8) {
            int key = radius * 16 + scale;
            Render2D.Shape cached = CACHE.get(key);
            if (cached != null) {
               return cached;
            } else {
               int size = radius * 2 * scale;
               double c = size / 2.0;
               double rr = size / 2.0;
               int[] px = new int[size * size];

               for (int py = 0; py < size; py++) {
                  for (int pxi = 0; pxi < size; pxi++) {
                     double d = Math.hypot(pxi + 0.5 - c, py + 0.5 - c);
                     double a = Math.max(0.0, Math.min(1.0, rr - d + 0.5));
                     px[py * size + pxi] = (int)Math.round(a * 255.0) << 24 | 16777215;
                  }
               }

               Identifier id = Identifier.fromNamespaceAndPath("nebula", "shape/circle_" + radius + "_" + scale);
               DynamicTextures.upload(id, null, px, size, size);
               Render2D.Shape shape = new Render2D.Shape(id, size);
               CACHE.put(key, shape);
               return shape;
            }
         } else {
            return null;
         }
      }
   }
}
