package com.lennart.nebula.cosmetic;

import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;

/** Einfache Bausteine für Kosmetik (Pixel-Einheiten, y nach unten). */
public final class Shapes {
   private Shapes() {
   }

   public static void box(PoseStack m, VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2, int argb, float bright) {
      WorldRender.box(m, vc, Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2), argb | 0xFF000000, bright);
   }

   /** Leuchtender Quader: Alpha 0..1 = Stärke. */
   public static void glow(PoseStack m, VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2, int rgb, float alpha) {
      float a = Mth.clamp(alpha, 0.0F, 1.0F);
      if (a > 0.01F) {
         WorldRender.box(
            m, vc, Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2), Math.round(a * 255.0F) << 24 | rgb & 16777215, 1.0F
         );
      }
   }

   /** Würfel mit Mittelpunkt und halber Kantenlänge. */
   public static void cube(PoseStack m, VertexConsumer vc, float x, float y, float z, float r, int argb, float bright) {
      box(m, vc, x - r, y - r, z - r, x + r, y + r, z + r, argb, bright);
   }

   public static void glowCube(PoseStack m, VertexConsumer vc, float x, float y, float z, float r, int rgb, float alpha) {
      glow(m, vc, x - r, y - r, z - r, x + r, y + r, z + r, rgb, alpha);
   }

   /** Blockige Kugel aus drei Quadern. */
   public static void ball(PoseStack m, VertexConsumer vc, float x, float y, float z, float r, int argb, float bright) {
      float s = r * 0.62F;
      box(m, vc, x - r, y - s, z - s, x + r, y + s, z + s, argb, bright);
      box(m, vc, x - s, y - r, z - s, x + s, y + r, z + s, argb, bright);
      box(m, vc, x - s, y - s, z - r, x + s, y + s, z + r, argb, bright);
   }

   /**
    * Abgerundeter Körper (Ellipsoid) aus waagerechten Scheiben – weicher als ein Quader, bleibt im Pixel-Stil.
    * Mittelpunkt (x, y, z), Halbachsen rx/ry/rz.
    */
   public static void blob(PoseStack m, VertexConsumer vc, float x, float y, float z, float rx, float ry, float rz, int argb, float bright) {
      int n = Math.max(3, Math.min(9, Math.round(ry * 2.4F)));
      float step = ry * 2.0F / n;

      for (int i = 0; i < n; i++) {
         float y1 = y - ry + i * step;
         float y2 = y1 + step;
         float mid = (y1 + y2) / 2.0F - y;
         float k = (float)Math.sqrt(Math.max(0.0F, 1.0F - mid * mid / (ry * ry)));
         float hx = Math.max(0.15F, rx * k);
         float hz = Math.max(0.15F, rz * k);
         box(m, vc, x - hx, y1, z - hz, x + hx, y2, z + hz, argb, bright);
      }
   }

   public static void glowBlob(PoseStack m, VertexConsumer vc, float x, float y, float z, float rx, float ry, float rz, int rgb, float alpha) {
      int n = Math.max(3, Math.min(7, Math.round(ry * 2.0F)));
      float step = ry * 2.0F / n;

      for (int i = 0; i < n; i++) {
         float y1 = y - ry + i * step;
         float y2 = y1 + step;
         float mid = (y1 + y2) / 2.0F - y;
         float k = (float)Math.sqrt(Math.max(0.0F, 1.0F - mid * mid / (ry * ry)));
         glow(m, vc, x - rx * k, y1, z - rz * k, x + rx * k, y2, z + rz * k, rgb, alpha);
      }
   }

   public static void glowBall(PoseStack m, VertexConsumer vc, float x, float y, float z, float r, int rgb, float alpha) {
      float s = r * 0.62F;
      glow(m, vc, x - r, y - s, z - s, x + r, y + s, z + s, rgb, alpha);
      glow(m, vc, x - s, y - r, z - s, x + s, y + r, z + s, rgb, alpha * 0.7F);
   }

   /** Waagerechter Ring (x-z-Ebene) aus n Segmenten, gedreht um rot (Grad). */
   public static void ring(PoseStack m, VertexConsumer vc, float cy, float r, float w, float h, int n, float rot, int argb, float bright, boolean glow, float alpha) {
      float seg = (float)(Math.PI * 2.0 * r / n) * 0.6F + 0.05F;

      for (int i = 0; i < n; i++) {
         m.pushPose();
         m.mulPose(Axis.YP.rotationDegrees(rot + i * 360.0F / n));
         if (glow) {
            glow(m, vc, r - w / 2.0F, cy - h / 2.0F, -seg, r + w / 2.0F, cy + h / 2.0F, seg, argb, alpha);
         } else {
            box(m, vc, r - w / 2.0F, cy - h / 2.0F, -seg, r + w / 2.0F, cy + h / 2.0F, seg, argb, bright);
         }

         m.popPose();
      }
   }

   /** Kegel aus gestapelten Quadern, Spitze nach oben (y wird kleiner). */
   public static void cone(PoseStack m, VertexConsumer vc, float x, float y, float z, float r, float height, int steps, int bottom, int top, float bright) {
      for (int i = 0; i < steps; i++) {
         float t = (float)i / steps;
         float rr = r * (1.0F - t * 0.92F);
         float y0 = y - height * t;
         float y1 = y - height * (t + 1.0F / steps);
         box(m, vc, x - rr, y1, z - rr, x + rr, y0, z + rr, mix(bottom, top, t), bright);
      }
   }

   /** Balken von a nach b in der x-y-Ebene (bei z). */
   public static void bar(PoseStack m, VertexConsumer vc, float x1, float y1, float x2, float y2, float z, float w, float t, int argb, float bright, boolean glow, float alpha) {
      float dx = x2 - x1;
      float dy = y2 - y1;
      float len = (float)Math.sqrt(dx * dx + dy * dy);
      if (len > 0.01F) {
         m.pushPose();
         m.translate(x1, y1, z);
         m.mulPose(Axis.ZP.rotation((float)Math.atan2(dy, dx)));
         if (glow) {
            glow(m, vc, 0.0F, -w / 2.0F, -t / 2.0F, len, w / 2.0F, t / 2.0F, argb, alpha);
         } else {
            box(m, vc, 0.0F, -w / 2.0F, -t / 2.0F, len, w / 2.0F, t / 2.0F, argb, bright);
         }

         m.popPose();
      }
   }

   public static int mix(int a, int b, float t) {
      t = Mth.clamp(t, 0.0F, 1.0F);
      int r = (int)((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
      int g = (int)((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
      int bl = (int)((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
      return 0xFF000000 | r << 16 | g << 8 | bl;
   }

   public static int hsv(float h, float s, float v) {
      return com.lennart.nebula.wings.WingsModel.hsv(h, s, v);
   }
}
