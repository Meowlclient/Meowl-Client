package com.lennart.nebula.watch;

import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.time.LocalTime;
import org.joml.Matrix4f;

public final class WatchModel {
   public static final String GEQI = "GEQI Watch";
   public static final String CLASSIC = "Klassik";
   public static final String SPORT = "Sport";
   public static final String DIGITAL = "Digital";
   public static final String ICED = "Iced Out";
   public static final String[] MODELS = new String[]{"GEQI Watch", "Klassik", "Sport", "Digital", "Iced Out"};
   private static final float WRIST_Y = 8.2F;
   private static final float BAND_Y1 = 7.3F;
   private static final float BAND_Y2 = 9.1F;
   private static final int[][] SEGMENTS = new int[][]{
      {1, 1, 1, 0, 1, 1, 1},
      {0, 0, 1, 0, 0, 1, 0},
      {1, 0, 1, 1, 1, 0, 1},
      {1, 0, 1, 1, 0, 1, 1},
      {0, 1, 1, 1, 0, 1, 0},
      {1, 1, 0, 1, 0, 1, 1},
      {1, 1, 0, 1, 1, 1, 1},
      {1, 0, 1, 0, 0, 1, 0},
      {1, 1, 1, 1, 1, 1, 1},
      {1, 1, 1, 1, 0, 1, 1}
   };

   private WatchModel() {
   }

   public static void draw(PoseStack m, VertexConsumer vc, String model, boolean right, boolean slim, float scale, float offset, float light, LocalTime time) {
      float side = right ? 1.0F : -1.0F;
      float s = -side;
      float xMin = right ? (slim ? -2.0F : -3.0F) : -1.0F;
      float xMax = right ? 1.0F : (slim ? 2.0F : 3.0F);
      float face = right ? xMin : xMax;
      m.pushPose();
      m.translate(0.0F, offset / 16.0F, 0.0F);
      m.scale(0.0625F, 0.0625F, 0.0625F);
      // über dem Ärmel (2. Skin-Ebene, 0,25 px dick) statt darunter: um die Armmitte etwas aufweiten
      float cx = (xMin + xMax) / 2.0F;
      m.translate(cx, 0.0F, 0.0F);
      m.scale(1.16F, 1.0F, 1.16F);
      m.translate(-cx, 0.0F, 0.0F);

      int strap = switch (model) {
         case "Klassik" -> -9748958;
         case "Sport" -> -14869217;
         case "Digital" -> -14539994;
         case "Iced Out" -> 0xFFD9DDE3;
         default -> -14277077;
      };
      float t = 0.22F;
      WorldRender.box(m, vc, xMin - t, 7.3F, -2.0F - t, xMax + t, 9.1F, -2.0F, strap, light);
      WorldRender.box(m, vc, xMin - t, 7.3F, 2.0F, xMax + t, 9.1F, 2.0F + t, strap, light);
      WorldRender.box(m, vc, xMin - t, 7.3F, -2.0F, xMin, 9.1F, 2.0F, strap, light);
      WorldRender.box(m, vc, xMax, 7.3F, -2.0F, xMax + t, 9.1F, 2.0F, strap, light);
      if ("Iced Out".equals(model)) {
         icedBand(m, vc, xMin - t, xMax + t, t);
      }
      m.pushPose();
      m.translate(face, 8.2F, 0.0F);
      m.scale(scale, scale, scale);
      switch (model) {
         case "Klassik":
            classic(m, vc, s, light, time);
            break;
         case "Sport":
            sport(m, vc, s, light, time);
            break;
         case "Digital":
            digital(m, vc, s, light, time, side);
            break;
         case "Iced Out":
            iced(m, vc, s, light, time, side);
            break;
         default:
            geqi(m, vc, s, light, time, side);
      }

      m.popPose();
      m.popPose();
   }

   private static void part(PoseStack m, VertexConsumer vc, float s, float a1, float a2, float y1, float y2, float z1, float z2, int argb, float light) {
      float x1 = s * a1;
      float x2 = s * a2;
      WorldRender.box(m, vc, Math.min(x1, x2), y1, z1, Math.max(x1, x2), y2, z2, argb, light);
   }

   private static void geqi(PoseStack m, VertexConsumer vc, float s, float light, LocalTime time, float side) {
      int carbon = -15132387;
      int carbon2 = -14013904;
      int red = -2939334;
      int ti = -3552046;
      part(m, vc, s, 0.0F, 0.75F, -1.45F, 1.45F, -1.25F, 1.25F, carbon, light);
      part(m, vc, s, 0.0F, 0.7F, -1.2F, 1.2F, -1.5F, -1.25F, carbon2, light);
      part(m, vc, s, 0.0F, 0.7F, -1.2F, 1.2F, 1.25F, 1.5F, carbon2, light);
      part(m, vc, s, 0.0F, 0.65F, -1.65F, -1.45F, -0.95F, 0.95F, carbon2, light);
      part(m, vc, s, 0.0F, 0.65F, 1.45F, 1.65F, -0.95F, 0.95F, carbon2, light);
      part(m, vc, s, 0.75F, 0.82F, -1.45F, 1.45F, -1.25F, -1.1F, red, light);
      part(m, vc, s, 0.75F, 0.82F, -1.45F, 1.45F, 1.1F, 1.25F, red, light);

      for (float yy : new float[]{-1.2F, 1.2F}) {
         for (float zz : new float[]{-1.0F, 1.0F}) {
            part(m, vc, s, 0.75F, 0.86F, yy - 0.1F, yy + 0.1F, zz - 0.1F, zz + 0.1F, ti, light);
         }
      }

      part(m, vc, s, 0.25F, 0.5F, -side * 1.9F - 0.15F, -side * 1.9F + 0.15F, -0.2F, 0.2F, ti, light);
      part(m, vc, s, 0.75F, 0.78F, -1.3F, 1.3F, -1.05F, 1.05F, -13947597, light);
      part(m, vc, s, 0.78F, 0.8F, -0.9F, -0.25F, -0.7F, -0.05F, -3563190, light);
      part(m, vc, s, 0.78F, 0.8F, 0.3F, 0.95F, 0.15F, 0.75F, red, light);
      part(m, vc, s, 0.78F, 0.8F, 0.2F, 0.6F, -0.8F, -0.4F, -7434090, light);
      markers(m, vc, s, 0.8F, 0.95F, -789517, 0.06F);
      hands(m, vc, s, 0.82F, side, time, -789517, -789517, red, 0.62F, 0.9F);
   }

   // ------------------------------------------------------------------ Iced Out

   private static final int ICE_WHITE = 0xFFEAF6FF;
   private static final int ICE_BLUE = 0xFFB8E6FF;
   private static final int WHITE_GOLD = 0xFFD9DDE3;
   private static final int WHITE_GOLD_DARK = 0xFFA9AFB8;

   /**
    * Funkeln eines Steins: ein weicher Lichtpunkt wandert langsam einmal um die Uhr (ca. 4 s), dazu ein sehr leichtes
    * Eigenglitzern – dezent, ohne Flackern.
    */
   private static int gem(int index, int count, int base) {
      double now = System.currentTimeMillis() / 1000.0;
      double sweep = (now / 4.0 % 1.0) * count;
      double d = Math.abs(index - sweep);
      d = Math.min(d, count - d);
      float glint = (float)Math.max(0.0, 1.0 - d / 2.2) * 0.75F;
      glint += 0.08F * (float)(0.5 + 0.5 * Math.sin(now * 1.3 + index * 2.39));
      return lerpColor(base, 0xFFFFFFFF, Math.min(1.0F, glint));
   }

   private static int lerpColor(int a, int b, float t) {
      int r = (int)((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
      int g = (int)((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
      int bl = (int)((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
      return 0xFF000000 | r << 16 | g << 8 | bl;
   }

   /** Steine rundum auf dem Armband (vier Seiten). */
   private static void icedBand(PoseStack m, VertexConsumer vc, float x1, float x2, float t) {
      int i = 0;
      float sz = 0.16F;

      for (float y = 7.55F; y <= 8.9F; y += 0.42F) {
         for (float z = -1.7F; z <= 1.75F; z += 0.55F) {
            WorldRender.box(m, vc, x1 - 0.06F, y - sz, z - sz, x1, y + sz, z + sz, gem(i++, 40, ICE_WHITE), 1.0F);
            WorldRender.box(m, vc, x2, y - sz, z - sz, x2 + 0.06F, y + sz, z + sz, gem(i++, 40, ICE_BLUE), 1.0F);
         }

         for (float x = x1 + 0.35F; x <= x2 - 0.3F; x += 0.55F) {
            WorldRender.box(m, vc, x - sz, y - sz, -2.0F - t - 0.06F, x + sz, y + sz, -2.0F - t, gem(i++, 40, ICE_WHITE), 1.0F);
            WorldRender.box(m, vc, x - sz, y - sz, 2.0F + t, x + sz, y + sz, 2.0F + t + 0.06F, gem(i++, 40, ICE_BLUE), 1.0F);
         }
      }
   }

   private static void iced(PoseStack m, VertexConsumer vc, float s, float light, LocalTime time, float side) {
      // Gehäuse aus Weißgold mit Bandanstößen
      part(m, vc, s, 0.0F, 0.7F, -1.5F, 1.5F, -1.3F, 1.3F, WHITE_GOLD, light);
      part(m, vc, s, 0.0F, 0.62F, -1.1F, 1.1F, -1.6F, -1.3F, WHITE_GOLD_DARK, light);
      part(m, vc, s, 0.0F, 0.62F, -1.1F, 1.1F, 1.3F, 1.6F, WHITE_GOLD_DARK, light);
      part(m, vc, s, 0.0F, 0.62F, -1.75F, -1.5F, -0.85F, 0.85F, WHITE_GOLD_DARK, light);
      part(m, vc, s, 0.0F, 0.62F, 1.5F, 1.75F, -0.85F, 0.85F, WHITE_GOLD_DARK, light);
      // Krone mit Stein
      part(m, vc, s, 0.2F, 0.5F, -side * 2.0F - 0.17F, -side * 2.0F + 0.17F, -0.2F, 0.2F, WHITE_GOLD, light);
      part(m, vc, s, 0.25F, 0.45F, -side * 2.2F - 0.08F, -side * 2.2F + 0.08F, -0.1F, 0.1F, gem(0, 24, ICE_BLUE), 1.0F);
      // Lünette: Kranz aus 24 Diamanten
      for (int i = 0; i < 24; i++) {
         double ang = i * Math.PI * 2.0 / 24.0;
         float y = (float)Math.sin(ang) * 1.22F;
         float z = (float)Math.cos(ang) * 1.05F;
         float sz = 0.13F;
         part(m, vc, s, 0.7F, 0.84F, y - sz, y + sz, z - sz, z + sz, gem(i, 24, i % 3 == 0 ? ICE_BLUE : ICE_WHITE), 1.0F);
      }

      // Zifferblatt: Pavé aus kleinen Steinen
      part(m, vc, s, 0.7F, 0.72F, -0.95F, 0.95F, -0.8F, 0.8F, 0xFF2A3440, light);
      int k = 0;

      for (float y = -0.75F; y <= 0.76F; y += 0.25F) {
         for (float z = -0.6F; z <= 0.61F; z += 0.25F) {
            if (y * y / 0.75F + z * z / 0.45F <= 1.0F) {
               part(m, vc, s, 0.72F, 0.75F, y - 0.08F, y + 0.08F, z - 0.08F, z + 0.08F, gem(k++, 48, k % 4 == 0 ? ICE_BLUE : ICE_WHITE), 1.0F);
            }
         }
      }

      // Stundenmarker als größere Steine, Zeiger in Weißgold
      markers(m, vc, s, 0.75F, 0.9F, 0xFFFFFFFF, 0.07F);
      hands(m, vc, s, 0.78F, side, time, WHITE_GOLD, WHITE_GOLD, 0xFF7FD3FF, 0.55F, 0.82F);
   }

   private static void classic(PoseStack m, VertexConsumer vc, float s, float light, LocalTime time) {
      int gold = -2838729;
      int gold2 = -5206742;
      part(m, vc, s, 0.0F, 0.6F, -1.2F, 1.2F, -1.2F, 1.2F, gold, light);
      part(m, vc, s, 0.0F, 0.55F, -0.85F, 0.85F, -1.45F, -1.2F, gold2, light);
      part(m, vc, s, 0.0F, 0.55F, -0.85F, 0.85F, 1.2F, 1.45F, gold2, light);
      part(m, vc, s, 0.0F, 0.55F, -1.45F, -1.2F, -0.85F, 0.85F, gold2, light);
      part(m, vc, s, 0.0F, 0.55F, 1.2F, 1.45F, -0.85F, 0.85F, gold2, light);
      part(m, vc, s, 0.6F, 0.63F, -1.0F, 1.0F, -1.0F, 1.0F, -724504, light);
      markers(m, vc, s, 0.63F, 0.85F, -15066598, 0.05F);
      hands(m, vc, s, 0.65F, 1.0F, time, -15066598, -15066598, -5234646, 0.5F, 0.78F);
   }

   private static void sport(PoseStack m, VertexConsumer vc, float s, float light, LocalTime time) {
      int steel = -4670268;
      int blue = -14729590;
      part(m, vc, s, 0.0F, 0.6F, -1.3F, 1.3F, -1.3F, 1.3F, steel, light);
      part(m, vc, s, 0.6F, 0.75F, -1.3F, 1.3F, -1.3F, -1.0F, blue, light);
      part(m, vc, s, 0.6F, 0.75F, -1.3F, 1.3F, 1.0F, 1.3F, blue, light);
      part(m, vc, s, 0.6F, 0.75F, -1.3F, -1.0F, -1.0F, 1.0F, blue, light);
      part(m, vc, s, 0.6F, 0.75F, 1.0F, 1.3F, -1.0F, 1.0F, blue, light);
      part(m, vc, s, 0.75F, 0.78F, 1.0F, 1.3F, -0.12F, 0.12F, -921103, light);
      part(m, vc, s, 0.6F, 0.63F, -1.0F, 1.0F, -1.0F, 1.0F, -15658474, light);
      markers(m, vc, s, 0.63F, 0.82F, -2558008, 0.07F);
      hands(m, vc, s, 0.65F, 1.0F, time, -1509152, -1509152, -2080722, 0.5F, 0.78F);
   }

   private static void digital(PoseStack m, VertexConsumer vc, float s, float light, LocalTime time, float side) {
      part(m, vc, s, 0.0F, 0.65F, -1.6F, 1.6F, -1.3F, 1.3F, -14539994, light);
      part(m, vc, s, 0.65F, 0.68F, -1.3F, 1.3F, -0.85F, 0.85F, -6442102, 1.0F);
      int h = time.getHour();
      int min = time.getMinute();
      int[] digits = new int[]{h / 10, h % 10, min / 10, min % 10};

      for (int i = 0; i < 4; i++) {
         float cy = -side * (-0.95F + i * 0.6F + (i >= 2 ? 0.1F : 0.0F));
         segDigit(m, vc, s, 0.69F, cy, digits[i], side);
      }

      if (time.getSecond() % 2 == 0) {
         part(m, vc, s, 0.68F, 0.7F, -0.04F, 0.04F, 0.12F, 0.2F, -14800360, 1.0F);
         part(m, vc, s, 0.68F, 0.7F, -0.04F, 0.04F, -0.2F, -0.12F, -14800360, 1.0F);
      }
   }

   private static void segDigit(PoseStack m, VertexConsumer vc, float s, float a, float cy, int d, float side) {
      int[] on = SEGMENTS[d];
      float w = 0.2F;
      float hh = 0.32F;
      float th = 0.05F;
      int c = -14800360;
      float r = -side;
      float[][] segs = new float[][]{
         {-w, w, hh - th, hh},
         {-w, -w + th, 0.0F, hh},
         {w - th, w, 0.0F, hh},
         {-w, w, -th / 2.0F, th / 2.0F},
         {-w, -w + th, -hh, 0.0F},
         {w - th, w, -hh, 0.0F},
         {-w, w, -hh, -hh + th}
      };

      for (int i = 0; i < 7; i++) {
         if (on[i] == 1) {
            float[] g = segs[i];
            float y1 = cy + r * g[0];
            float y2 = cy + r * g[1];
            part(m, vc, s, a, a + 0.02F, Math.min(y1, y2), Math.max(y1, y2), g[2], g[3], c, 1.0F);
         }
      }
   }

   private static void markers(PoseStack m, VertexConsumer vc, float s, float a, float radius, int color, float size) {
      for (int i = 0; i < 12; i++) {
         double ang = i * Math.PI / 6.0;
         float z = (float)Math.cos(ang) * radius;
         float y = (float)Math.sin(ang) * radius;
         float sz = i % 3 == 0 ? size * 1.6F : size;
         part(m, vc, s, a, a + 0.02F, y - sz, y + sz, z - sz, z + sz, color, 1.0F);
      }
   }

   private static void hands(
      PoseStack m, VertexConsumer vc, float s, float a, float side, LocalTime time, int hourColor, int minColor, int secColor, float hourLen, float minLen
   ) {
      double sec = time.getSecond() + time.getNano() / 1.0E9;
      double minute = time.getMinute() + sec / 60.0;
      double hour = time.getHour() % 12 + minute / 60.0;
      hand(m, vc, s, a, side, hour / 12.0 * Math.PI * 2.0, hourLen, 0.09F, hourColor);
      hand(m, vc, s, a + 0.015F, side, minute / 60.0 * Math.PI * 2.0, minLen, 0.06F, minColor);
      hand(m, vc, s, a + 0.03F, side, sec / 60.0 * Math.PI * 2.0, minLen * 1.05F, 0.025F, secColor);
      part(m, vc, s, a + 0.03F, a + 0.06F, -0.07F, 0.07F, -0.07F, 0.07F, secColor, 1.0F);
   }

   private static void hand(PoseStack m, VertexConsumer vc, float s, float a, float side, double theta, float len, float width, int color) {
      Matrix4f mat = m.last().pose();
      float dy = (float)(-side * Math.sin(theta));
      float dz = (float)Math.cos(theta);
      float py = (float)(-side * Math.cos(theta)) * width;
      float pz = (float)(-Math.sin(theta)) * width;
      float x = s * (a + 0.02F);
      float by = -dy * 0.15F;
      float bz = -dz * 0.15F;
      float ty = dy * len;
      float tz = dz * len;
      quad2(vc, mat, color, x, by - py, bz - pz, x, by + py, bz + pz, x, ty + py * 0.5F, tz + pz * 0.5F, x, ty - py * 0.5F, tz - pz * 0.5F);
   }

   private static void quad2(
      VertexConsumer vc,
      Matrix4f m,
      int c,
      float ax,
      float ay,
      float az,
      float bx,
      float by,
      float bz,
      float cx,
      float cy,
      float cz,
      float dx,
      float dy,
      float dz
   ) {
      vc.addVertex(m, ax, ay, az).setColor(c);
      vc.addVertex(m, bx, by, bz).setColor(c);
      vc.addVertex(m, cx, cy, cz).setColor(c);
      vc.addVertex(m, dx, dy, dz).setColor(c);
      vc.addVertex(m, dx, dy, dz).setColor(c);
      vc.addVertex(m, cx, cy, cz).setColor(c);
      vc.addVertex(m, bx, by, bz).setColor(c);
      vc.addVertex(m, ax, ay, az).setColor(c);
   }
}
