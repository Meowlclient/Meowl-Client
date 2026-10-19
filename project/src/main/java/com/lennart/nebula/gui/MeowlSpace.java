package com.lennart.nebula.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Weltraum-Hintergrund der Meowl-Menüs außerhalb einer Welt: Verlauf, Nebel, funkelnde Sterne mit Parallaxe,
 * Sternbilder zur Maus (nahe Sterne verbinden sich mit dem Zeiger), Klick-Wellen und Sternschnuppen.
 */
public final class MeowlSpace {
   private record Star(float x, float y, int size, float depth, float phase, int rgb) {
   }

   private record Ripple(float x, float y, long at) {
   }

   private record Shooting(float x, float y, float dx, float dy, long at) {
   }

   private static final Star[] STARS = makeStars();
   private static final List<Ripple> RIPPLES = new ArrayList<>();
   private static final List<Shooting> SHOOTING = new ArrayList<>();
   private static final long START = System.currentTimeMillis();

   private MeowlSpace() {
   }

   private static Star[] makeStars() {
      Random r = new Random(1707L);
      int[] tints = {0xFFFFFF, 0xFFFFFF, 0xFFFFFF, 0xCFC8FF, 0xFFE9C9, 0xE6D4FF};
      Star[] out = new Star[240];
      for (int i = 0; i < out.length; i++) {
         float depth = 0.2F + r.nextFloat() * 0.8F;
         out[i] = new Star(r.nextFloat(), r.nextFloat(), depth > 0.85F ? 2 : 1, depth, r.nextFloat() * 6.28F, tints[r.nextInt(tints.length)]);
      }

      return out;
   }

   public static int withAlpha(int rgb, float a) {
      return Math.round(Math.max(0.0F, Math.min(1.0F, a)) * 255.0F) << 24 | rgb & 16777215;
   }

   public static void disc(GuiGraphics ctx, float cx, float cy, float r, int color) {
      if (r < 0.5F) {
         return;
      }

      ctx.pose().pushMatrix();
      ctx.pose().translate(cx, cy);
      ctx.pose().scale(r / 64.0F, r / 64.0F);
      Render2D.circle(ctx, 0, 0, 64, color);
      ctx.pose().popMatrix();
   }

   /** Klick in den Himmel: Welle und eine Sternschnuppe, die vom Zeiger losfliegt. */
   public static void click(float x, float y) {
      long now = System.currentTimeMillis();
      RIPPLES.add(new Ripple(x, y, now));
      double a = Math.toRadians(20.0 + Math.random() * 30.0);
      SHOOTING.add(new Shooting(x, y, (float)Math.cos(a), (float)Math.sin(a), now));
      if (RIPPLES.size() > 8) {
         RIPPLES.remove(0);
      }

      if (SHOOTING.size() > 6) {
         SHOOTING.remove(0);
      }
   }

   /** 1 Pixel breite Linie als ein einziger gedrehter Balken (statt Punkt für Punkt). */
   static void line(GuiGraphics ctx, float x1, float y1, float x2, float y2, int color) {
      float dx = x2 - x1;
      float dy = y2 - y1;
      float len = (float)Math.sqrt(dx * dx + dy * dy);
      if (len < 0.5F) {
         return;
      }

      ctx.pose().pushMatrix();
      ctx.pose().translate(x1, y1);
      ctx.pose().rotate((float)Math.atan2(dy, dx));
      ctx.fill(0, 0, Math.round(len), 1, color);
      ctx.pose().popMatrix();
   }
   public static void draw(GuiGraphics ctx, int w, int h, int mx, int my) {
      float t = (System.currentTimeMillis() - START) / 1000.0F;
      ctx.fillGradient(0, 0, w, h, 0xFF05060F, 0xFF140A2C);
      float px = mx / (float)Math.max(1, w) - 0.5F;
      float py = my / (float)Math.max(1, h) - 0.5F;
      disc(ctx, w * 0.18F - px * 10.0F, h * 0.82F - py * 6.0F, h * 0.55F, 0x0C7B4DFF);
      disc(ctx, w * 0.86F - px * 14.0F, h * 0.12F - py * 8.0F, h * 0.45F, 0x0AFF5FB8);
      disc(ctx, w * 0.5F - px * 6.0F, h * 0.4F - py * 4.0F, h * 0.35F, 0x083E8BFF);
      long now = System.currentTimeMillis();

      for (Star s : STARS) {
         float x = (s.x() * w + t * s.depth() * 3.0F - px * 18.0F * s.depth()) % w;
         if (x < 0.0F) {
            x += w;
         }

         float y = s.y() * h - py * 12.0F * s.depth();
         float tw = 0.45F + 0.55F * (float)(0.5 + 0.5 * Math.sin(t * (1.2F + s.depth() * 2.0F) + s.phase()));
         // Sternbild: nahe Sterne verbinden sich mit dem Mauszeiger
         float d = (float)Math.hypot(x - mx, y - my);
         if (d < 95.0F && s.depth() > 0.45F) {
            line(ctx, x, y, mx, my, withAlpha(0xB57BFF, (1.0F - d / 95.0F) * 0.45F));
            tw = Math.min(1.0F, tw + (1.0F - d / 95.0F));
         }

         // Welle lässt Sterne kurz aufleuchten
         for (Ripple r : RIPPLES) {
            float rr = (now - r.at()) / 1000.0F * 260.0F;
            float dist = (float)Math.hypot(x - r.x(), y - r.y());
            if (Math.abs(dist - rr) < 10.0F) {
               tw = 1.0F;
            }
         }

         ctx.fill(Math.round(x), Math.round(y), Math.round(x) + s.size(), Math.round(y) + s.size(), withAlpha(s.rgb(), tw * (0.35F + 0.65F * s.depth())));
      }

      // Klick-Wellen
      RIPPLES.removeIf(r -> now - r.at() > 1400L);
      for (Ripple r : RIPPLES) {
         float k = (now - r.at()) / 1400.0F;
         float rr = k * 260.0F * 1.4F;
         int n = 64;
         for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2.0 / n;
            int x = Math.round(r.x() + (float)Math.cos(a) * rr);
            int y = Math.round(r.y() + (float)Math.sin(a) * rr);
            ctx.fill(x, y, x + 1, y + 1, withAlpha(0xD6B8FF, (1.0F - k) * 0.6F));
         }
      }

      // Sternschnuppen: eine von selbst alle 7 Sekunden, dazu die angeklickten
      float cycle = t % 7.0F;
      if (cycle < 1.1F) {
         float p = cycle / 1.1F;
         float sx = w * 0.12F + p * w * 0.4F + (float)(Math.floor(t / 7.0F) * 97.0 % (w * 0.3));
         float sy = h * 0.08F + p * h * 0.22F;
         trail(ctx, sx, sy, 0.894F, 0.447F, 1.0F - p);
      }

      SHOOTING.removeIf(s -> now - s.at() > 1000L);
      for (Shooting s : SHOOTING) {
         float p = (now - s.at()) / 1000.0F;
         trail(ctx, s.x() + s.dx() * p * 420.0F, s.y() + s.dy() * p * 420.0F, s.dx(), s.dy(), 1.0F - p);
      }
   }

   private static void trail(GuiGraphics ctx, float x, float y, float dx, float dy, float a) {
      for (int i = 0; i < 26; i++) {
         float k = i / 26.0F;
         int tx = Math.round(x - dx * k * 38.0F);
         int ty = Math.round(y - dy * k * 38.0F);
         ctx.fill(tx, ty, tx + 2, ty + 1, withAlpha(0xFFFFFF, (1.0F - k) * a * 0.9F));
      }
   }
}
