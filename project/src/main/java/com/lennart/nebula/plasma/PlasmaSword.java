package com.lennart.nebula.plasma;

import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class PlasmaSword {
   private PlasmaSword() {
   }

   public static void render(PoseStack matrices, SubmitNodeCollector queue, boolean leftHanded, PlasmaSword.Look look) {
      if ((look.model() == KARAMBIT || look.model() == BUTTERFLY) && !look.firstPerson()) {
         // dritte Person: Item-Drehung von Minecraft rückgängig machen und genau wie in der Ich-Ansicht in die Faust setzen
         matrices.pushPose();
         matrices.translate(-(leftHanded ? -1 : 1) / 16.0F, -0.125F, 0.625F);
         matrices.rotate(Axis.YP.rotationDegrees(-180.0F));
         matrices.rotate(Axis.XP.rotationDegrees(90.0F));
         if (leftHanded) {
            matrices.scale(-1.0F, 1.0F, 1.0F);
         }

         matrices.translate(-1.0F / 16.0F, 9.0F / 16.0F, -0.6F / 16.0F);
         if (look.model() == BUTTERFLY) {
            // dritte Person: offen gehalten (Klinge oben, Griffe als V)
            // Klinge zeigt nach vorne, Schneide nach unten, Faust am Griff
            matrices.rotate(new org.joml.Quaternionf().setFromNormalized(new org.joml.Matrix3f(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, -1.0F, 0.0F, 1.0F, 0.0F)));
            matrices.scale(0.85F, 0.85F, 0.85F);
            matrices.translate(0.0F, 0.13F, 0.0F);
            submitButterfly(matrices, queue, look, 180.0F, 152.0F);
            matrices.popPose();
            return;
         }

         // wie in der Ich-Ansicht: flach (Klingenfläche nach oben), nach vorne, Haken nach außen
         matrices.rotate(new org.joml.Quaternionf().setFromNormalized(new org.joml.Matrix3f(0.0F, -1.0F, 0.0F, 0.0F, 0.0F, -1.0F, 1.0F, 0.0F, 0.0F)));
         matrices.scale(0.85F, 0.85F, 0.85F);
         matrices.translate(0.0F, 0.16F, 0.035F);
         float scale = look.bladeLength() / 0.95F;
         queue.submitCustomGeometry(matrices, WorldRender.solidColor(), (entry, vc) -> karambit(entry.pose(), vc, look, scale, false));
         queue.submitCustomGeometry(matrices, WorldRender.glow(), (entry, vc) -> karambit(entry.pose(), vc, look, scale, true));
         matrices.popPose();
         return;
      }

      matrices.pushPose();
      if (look.firstPerson()) {
         transform(matrices, leftHanded, 1.13F, 3.2F, 1.13F, 0.0F, -90.0F, 25.0F, 0.68F);
      } else {
         transform(matrices, leftHanded, 0.0F, 4.0F, 0.5F, 0.0F, -90.0F, 55.0F, 0.85F);
      }

      matrices.translate(0.3F, 0.3F, 0.5F);
      matrices.rotate(Axis.ZP.rotationDegrees(-45.0F));
      if (look.swing() > 0.0F && look.firstPerson()) {
         float s = (float)Math.sin(look.swing() * Math.PI);
         matrices.rotate(Axis.ZP.rotationDegrees(-s * 28.0F));
         matrices.rotate(Axis.XP.rotationDegrees((leftHanded ? -1 : 1) * s * 18.0F));
      }

      if (look.model() != SABER) {
         // Messer-Modelle: feste Klinge, Schneide leuchtet leicht in der Klingenfarbe
         float scale = look.bladeLength() / 0.95F;
         if (look.model() == BUTTERFLY) {
            submitButterfly(matrices, queue, look, 180.0F, 152.0F);
         } else if (look.model() == KARAMBIT) {
            queue.submitCustomGeometry(matrices, WorldRender.solidColor(), (entry, vc) -> karambit(entry.pose(), vc, look, scale, false));
            queue.submitCustomGeometry(matrices, WorldRender.glow(), (entry, vc) -> karambit(entry.pose(), vc, look, scale, true));
         } else {
            queue.submitCustomGeometry(matrices, WorldRender.solidColor(), (entry, vc) -> katana(entry.pose(), vc, look, scale, false));
            queue.submitCustomGeometry(matrices, WorldRender.glow(), (entry, vc) -> katana(entry.pose(), vc, look, scale, true));
         }

         matrices.popPose();
         return;
      }

      queue.submitCustomGeometry(matrices, WorldRender.solidColor(), (entry, vc) -> hilt(entry.pose(), vc, look));
      float len = look.bladeLength() * look.extend();
      if (len > 0.01F) {
         queue.submitCustomGeometry(matrices, WorldRender.glow(), (entry, vc) -> blade(entry.pose(), vc, look, len, 1.0F));
         if (look.trail() && look.swing() > 0.05F && look.swing() < 0.95F) {
            for (int k = 1; k <= 3; k++) {
               float back = k * 9.0F * (float)Math.sin(look.swing() * Math.PI);
               matrices.pushPose();
               matrices.rotate(Axis.ZP.rotationDegrees(back));
               float fade = 0.5F / k;
               queue.submitCustomGeometry(matrices, WorldRender.glow(), (entry, vc) -> blade(entry.pose(), vc, look, len, fade));
               matrices.popPose();
            }
         }
      }

      matrices.popPose();
   }

   public static void drawPreview(Matrix4f mat, VertexConsumer vc, PlasmaSword.Look look) {
      hilt(mat, vc, look);
      blade(mat, vc, look, look.bladeLength() * look.extend(), 1.0F);
   }

   private static void transform(PoseStack m, boolean left, float tx, float ty, float tz, float rx, float ry, float rz, float scale) {
      float x = left ? -tx : tx;
      m.translate(x / 16.0F, ty / 16.0F, tz / 16.0F);
      m.rotate(new Quaternionf().rotationXYZ((float)Math.toRadians(rx), (float)Math.toRadians(ry), (float)Math.toRadians(rz)));
      m.scale(scale, scale, scale);
      m.translate(-0.5F, -0.5F, -0.5F);
   }

   private static void hilt(Matrix4f mat, VertexConsumer vc, PlasmaSword.Look look) {
      cube(vc, mat, 0.06F, -0.26F, -0.17F, -15395561, 0.9F);
      cube(vc, mat, 0.05F, -0.17F, -0.15F, -4680129, 1.0F);
      cube(vc, mat, 0.045F, -0.15F, 0.04F, -7434349, 1.0F);
      cube(vc, mat, 0.047F, -0.06F, -0.02F, -13882320, 1.0F);

      for (int i = 0; i < 3; i++) {
         float y = 0.04F + i * 0.028F;
         cube(vc, mat, 0.041F, y, y + 0.016F, -9605517, 1.0F);
         cube(vc, mat, 0.036F, y + 0.016F, y + 0.028F, -12960960, 1.0F);
      }

      cube(vc, mat, 0.056F, 0.124F, 0.2F, -13355460, 1.0F);
      int glass = mix(-10503991, 0xFF000000 | look.color(), 0.35F);
      cube(vc, mat, 0.058F, 0.15F, 0.175F, glass, 1.15F);
      float len = look.bladeLength() * look.extend();
      if (len > 0.01F) {
         int core = mix(-1, 0xFF000000 | look.color(), 0.12F);
         cube(vc, mat, 0.026F, 0.2F, 0.2F + len, core, 1.25F);
         cube(vc, mat, 0.018F, 0.2F + len, 0.2F + len + 0.025F, core, 1.25F);
      }
   }

   private static void blade(Matrix4f mat, VertexConsumer vc, PlasmaSword.Look look, float len, float fade) {
      float flicker = 0.92F + 0.08F * (float)Math.sin(System.nanoTime() / 1.0E9 * 37.0);
      float g = look.glow() * flicker * fade;
      int rgb = look.color() & 16777215;
      glowCube(vc, mat, 0.04F, 0.2F, 0.2F + len + 0.035F, rgb, 0.85F * g, 0.4F);
      glowCube(vc, mat, 0.065F, 0.19F, 0.2F + len + 0.055F, rgb, 0.45F * g, 0.08F);
      glowCube(vc, mat, 0.1F, 0.18F, 0.2F + len + 0.08F, rgb, 0.2F * g, 0.0F);
      glowCube(vc, mat, 0.15F, 0.17F, 0.2F + len + 0.11F, rgb, 0.08F * g, 0.0F);
   }

   private static int mix(int a, int b, float t) {
      int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
      int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
      int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
      return 0xFF000000 | r << 16 | g << 8 | bl;
   }

   private static int shade(int argb, float k) {
      int r = Math.min(255, Math.round((argb >> 16 & 0xFF) * k));
      int g = Math.min(255, Math.round((argb >> 8 & 0xFF) * k));
      int b = Math.min(255, Math.round((argb & 0xFF) * k));
      return argb & 0xFF000000 | r << 16 | g << 8 | b;
   }

   private static void cube(VertexConsumer vc, Matrix4f m, float hw, float y1, float y2, int argb, float bright) {
      int top = shade(argb, bright);
      int side1 = shade(argb, bright * 0.82F);
      int side2 = shade(argb, bright * 0.64F);
      int bottom = shade(argb, bright * 0.5F);
      quad(vc, m, top, -hw, y2, -hw, -hw, y2, hw, hw, y2, hw, hw, y2, -hw);
      quad(vc, m, bottom, -hw, y1, -hw, hw, y1, -hw, hw, y1, hw, -hw, y1, hw);
      quad(vc, m, side1, -hw, y1, -hw, -hw, y2, -hw, hw, y2, -hw, hw, y1, -hw);
      quad(vc, m, side1, -hw, y1, hw, hw, y1, hw, hw, y2, hw, -hw, y2, hw);
      quad(vc, m, side2, -hw, y1, -hw, -hw, y1, hw, -hw, y2, hw, -hw, y2, -hw);
      quad(vc, m, side2, hw, y1, -hw, hw, y2, -hw, hw, y2, hw, hw, y1, hw);
   }

   private static void glowCube(VertexConsumer vc, Matrix4f m, float hw, float y1, float y2, int rgb, float alpha, float whiten) {
      int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
      int r = rgb >> 16 & 0xFF;
      int g = rgb >> 8 & 0xFF;
      int b = rgb & 0xFF;
      r = Math.round(r + (255 - r) * whiten);
      g = Math.round(g + (255 - g) * whiten);
      b = Math.round(b + (255 - b) * whiten);
      int c = a << 24 | r << 16 | g << 8 | b;
      quad(vc, m, c, -hw, y2, -hw, -hw, y2, hw, hw, y2, hw, hw, y2, -hw);
      quad(vc, m, c, -hw, y1, -hw, hw, y1, -hw, hw, y1, hw, -hw, y1, hw);
      quad(vc, m, c, -hw, y1, -hw, -hw, y2, -hw, hw, y2, -hw, hw, y1, -hw);
      quad(vc, m, c, -hw, y1, hw, hw, y1, hw, hw, y2, hw, -hw, y2, hw);
      quad(vc, m, c, -hw, y1, -hw, -hw, y1, hw, -hw, y2, hw, -hw, y2, -hw);
      quad(vc, m, c, hw, y1, -hw, hw, y2, -hw, hw, y2, hw, hw, y1, hw);
   }

   private static void quad(
      VertexConsumer vc,
      Matrix4f m,
      int color,
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
      vc.addVertex(m, ax, ay, az).setColor(color);
      vc.addVertex(m, bx, by, bz).setColor(color);
      vc.addVertex(m, cx, cy, cz).setColor(color);
      vc.addVertex(m, dx, dy, dz).setColor(color);
   }

   public static final int SABER = 0;
   public static final int KARAMBIT = 1;
   public static final int KATANA = 2;
   public static final int BUTTERFLY = 3;

   public record Look(int color, float glow, float bladeLength, float extend, float swing, boolean trail, boolean firstPerson, int model, int finish) {
      public Look(int color, float glow, float bladeLength, float extend, float swing, boolean trail, boolean firstPerson) {
         this(color, glow, bladeLength, extend, swing, trail, firstPerson, SABER, DOPPLER);
      }

      public Look(int color, float glow, float bladeLength, float extend, float swing, boolean trail, boolean firstPerson, int model) {
         this(color, glow, bladeLength, extend, swing, trail, firstPerson, model, DOPPLER);
      }
   }

   // ================================================================== Finishes (Karambit)

   public static final String[] FINISHES = {"Doppler", "Marble Fade", "Fade", "Gold", "Tiger Tooth", "Saphir", "Rubin", "Smaragd", "Lavendel"};
   public static final int DOPPLER = 0;
   public static final int MARBLE_FADE = 1;
   public static final int FADE = 2;
   public static final int GOLD = 3;
   public static final int TIGER_TOOTH = 4;
   public static final int SAPPHIRE = 5;
   public static final int RUBY = 6;
   public static final int EMERALD = 7;
   public static final int LAVENDER = 8;

   public static int finishId(String name) {
      for (int i = 0; i < FINISHES.length; i++) {
         if (FINISHES[i].equals(name)) {
            return i;
         }
      }

      return DOPPLER;
   }

   private static int ramp(float v, int... colors) {
      v = Math.max(0.0F, Math.min(0.999F, v));
      float f = v * (colors.length - 1);
      int i = (int)f;
      return mix(colors[i], colors[i + 1], f - i);
   }

   /** Farbe der Klinge an einer Stelle: q = entlang (0 Griff … 1 Spitze), s = quer (0 Schneide … 1 Rücken). */
   public static int finish(int id, int base, float q, float s) {
      // Marmor-Muster (fest, damit es nicht flackert)
      float marble = (float)(Math.sin(q * 11.0 + s * 4.0) * 0.5 + Math.sin(q * 23.0 - s * 7.0) * 0.25);
      return switch (id) {
         case MARBLE_FADE -> ramp(q * 0.85F + 0.18F * marble + 0.1F, 0xFF2F5BFF, 0xFFFFD23A, 0xFFE5262B, 0xFFFFD23A);
         case FADE -> ramp(1.0F - q + 0.05F * marble, 0xFFFFE25A, 0xFFFF5FB8, 0xFF8A4BFF);
         case GOLD -> mix(0xFFE8B83A, 0xFFFFF0A0, Math.max(0.0F, s - 0.4F) + 0.15F * Math.max(0.0F, marble));
         case TIGER_TOOTH -> Math.sin(q * 34.0 + s * 5.0) > 0.55 ? 0xFF8C4512 : mix(0xFFF2A93B, 0xFFFFD27A, s * 0.5F);
         case SAPPHIRE -> mix(0xFF1B2FD8, 0xFF6FB8FF, 0.5F + 0.5F * marble);
         case RUBY -> mix(0xFFB80C28, 0xFFFF6A7A, 0.5F + 0.5F * marble);
         case EMERALD -> mix(0xFF0B8A42, 0xFF5BFFB0, 0.5F + 0.5F * marble);
         // pastelliges Lila mit hellem Schimmer zur Spitze (wie das Butterfly im Video)
         case LAVENDER -> mix(0xFF9E7EEA, 0xFFEBE0FF, 0.25F + 0.45F * q + 0.2F * s + 0.12F * Math.max(0.0F, marble));
         default -> mix(mix(base, 0xFF2F6BFF, q * 0.85F), 0xFF0C0A1E, Math.max(0.0F, marble) * 0.35F);
      };
   }

   // ================================================================== Messer

   private static final int STEEL = 0xFFC9CED8;
   private static final int STEEL_DARK = 0xFF8E95A3;
   private static final int WRAP = 0xFF1C1C22;
   private static final int BRASS = 0xFFC9A040;

   /** Quader mit einfacher Schattierung (Längsachse y). */
   private static void box(VertexConsumer vc, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, int argb, float bright) {
      int top = shade(argb, bright);
      int side1 = shade(argb, bright * 0.82F);
      int side2 = shade(argb, bright * 0.64F);
      int bottom = shade(argb, bright * 0.5F);
      quad(vc, m, top, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
      quad(vc, m, bottom, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
      quad(vc, m, side1, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
      quad(vc, m, side1, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
      quad(vc, m, side2, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
      quad(vc, m, side2, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
   }

   private static void glowBox(VertexConsumer vc, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, int rgb, float alpha) {
      int a = Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * 255.0F);
      int c = a << 24 | rgb & 16777215;
      quad(vc, m, c, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
      quad(vc, m, c, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
      quad(vc, m, c, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
      quad(vc, m, c, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
      quad(vc, m, c, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
      quad(vc, m, c, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
   }

   /** Katana: umwickelter Griff, goldenes Stichblatt, lange, leicht gebogene Klinge mit leuchtender Schneide. */
   private static void katana(Matrix4f m, VertexConsumer vc, Look look, float scale, boolean glow) {
      int rgb = look.color() & 16777215;
      if (!glow) {
         // Griff mit Wicklung und Knauf
         box(vc, m, -0.032F, -0.3F, -0.036F, 0.032F, 0.12F, 0.036F, WRAP, 1.0F);
         for (int i = 0; i < 6; i++) {
            float y = -0.27F + i * 0.065F;
            box(vc, m, -0.035F, y, -0.039F, 0.035F, y + 0.022F, 0.039F, mix(WRAP, 0xFF000000 | rgb, 0.45F), 1.0F);
         }

         box(vc, m, -0.036F, -0.33F, -0.04F, 0.036F, -0.3F, 0.04F, BRASS, 1.0F);
         // Stichblatt (Tsuba) und Klingenhülse
         box(vc, m, -0.075F, 0.12F, -0.085F, 0.075F, 0.145F, 0.085F, BRASS, 1.0F);
         box(vc, m, -0.022F, 0.145F, -0.04F, 0.022F, 0.19F, 0.04F, BRASS, 1.15F);
      }

      // Klinge: Segmente, die sich zum Rücken hin biegen
      int n = 12;
      float len = 1.0F * scale;
      for (int i = 0; i < n; i++) {
         float q0 = (float)i / n;
         float q1 = (float)(i + 1) / n;
         float y0 = 0.19F + q0 * len;
         float y1 = 0.19F + q1 * len;
         float bend = 0.07F * q0 * q0;
         float w = 0.036F * (1.0F - q0 * 0.25F);
         if (i == n - 1) {
            w *= 0.55F;
         }

         if (!glow) {
            box(vc, m, -0.011F, y0, -w + bend, 0.011F, y1, w * 0.8F + bend, STEEL, 1.1F);
            box(vc, m, -0.013F, y0, w * 0.45F + bend, 0.013F, y1, w * 0.8F + bend, STEEL_DARK, 1.0F);
         }
      }
   }

   /**
    * Karambit wie in CS: Griff und Klinge bilden zusammen einen Bogen (Schneide innen), runder Fingerring am Knauf,
    * Griffschalen mit Stahl-Liner und Schrauben, Fingerschutz am Klingenansatz, breite Krallenklinge mit Daumenrillen.
    */
   private static void karambit(Matrix4f m, VertexConsumer vc, Look look, float scale, boolean glow) {
      if (glow) {
         return;
      }

      int base = 0xFF000000 | look.color() & 16777215;
      int finish = look.finish();
      // Mittellinie des Griffs, vom Knauf (0) zum Klingenansatz (n), leicht zur Schneide hin gebogen
      int n = 12;
      float len = 0.275F;
      float[] py = new float[n + 1];
      float[] pz = new float[n + 1];
      float[] ny = new float[n + 1];
      float[] nz = new float[n + 1];
      float[] we = new float[n + 1];
      float[] ws = new float[n + 1];
      float[] se = new float[n + 1];
      float[] ss = new float[n + 1];
      float y = 0.06F;
      float z = 0.0F;
      for (int i = n; i >= 0; i--) {
         float q = (float)i / n;
         float th = -0.35F * (1.0F - q);
         float dy = (float)Math.cos(th);
         float dz = -(float)Math.sin(th);
         py[i] = y;
         pz[i] = z;
         ny[i] = dz;
         nz[i] = -dy;
         float guard = q > 0.8F ? 0.034F * smoothstep((q - 0.8F) / 0.2F) : 0.0F;
         we[i] = 0.033F + 0.005F * (float)Math.sin(Math.PI * q) + guard;
         ws[i] = 0.031F + 0.003F * (float)Math.sin(Math.PI * q);
         se[i] = (0.033F + 0.005F * (float)Math.sin(Math.PI * q)) * 0.84F;
         ss[i] = ws[i] * 0.84F;
         y -= dy * len / n;
         z -= dz * len / n;
      }

      int scaleCol = finish == LAVENDER ? BF_HANDLE : 0xFF1A1A20;
      // Stahl-Liner (mit Fingerschutz) und Griffschalen
      slab(vc, m, py, pz, ny, nz, we, ws, 0.011F, STEEL_DARK);
      slab(vc, m, py, pz, ny, nz, se, ss, 0.024F, scaleCol);
      // Schrauben auf beiden Seiten
      for (int i : new int[]{3, 8}) {
         float cy = py[i] - ny[i] * 0.002F;
         float cz = pz[i] - nz[i] * 0.002F;
         box(vc, m, -0.027F, cy - 0.008F, cz - 0.008F, 0.027F, cy + 0.008F, cz + 0.008F, STEEL, 1.15F);
      }

      // Bolzen am Klingenansatz
      box(vc, m, -0.02F, 0.055F, -0.06F, 0.02F, 0.088F, 0.032F, STEEL, 1.05F);
      // runder Fingerring hinter dem Knauf
      float dy0 = (float)Math.cos(-0.35F);
      float dz0 = -(float)Math.sin(-0.35F);
      ring(vc, m, py[0] - dy0 * 0.058F, pz[0] - dz0 * 0.058F, 0.064F, 0.041F, 0.016F, STEEL_DARK);
      // breite Krallenklinge
      curvedBlade(vc, m, finish, base, 0.085F, 0.46F * scale, 40, PlasmaSword::karambitWidth, q -> (float)Math.toRadians(112.0 * Math.pow(q, 1.25)));
      // Daumenrillen auf dem Rücken nahe am Griff
      for (int k = 0; k < 5; k++) {
         float ty = 0.105F + k * 0.013F;
         float q = (ty - 0.085F) / (0.46F * scale);
         float tz = karambitWidth(q) * 0.38F;
         box(vc, m, -0.0065F, ty, tz - 0.002F, 0.0065F, ty + 0.0055F, tz + 0.0045F, mix(finish(finish, base, q, 1.0F), 0xFF000000, 0.25F), 1.0F);
      }
   }

   /** Breite Krallenklinge wie in CS: lange breit, erst im letzten Drittel zur Spitze. */
   private static float karambitWidth(float q) {
      float w = q < 0.55F ? 0.104F * (1.0F - 0.18F * q) : 0.104F * 0.9F * (float)Math.pow(Math.max(0.0F, 1.0F - (q - 0.55F) / 0.45F), 0.7F);
      return w + 0.0015F;
   }

   private static float smoothstep(float x) {
      x = Math.max(0.0F, Math.min(1.0F, x));
      return x * x * (3.0F - 2.0F * x);
   }

   /** Flaches Profil entlang einer Mittellinie (Breite zur Schneide {@code we}, zum Rücken {@code ws}, Dicke ±t). */
   private static void slab(VertexConsumer vc, Matrix4f m, float[] py, float[] pz, float[] ny, float[] nz, float[] we, float[] ws, float t, int col) {
      int n = py.length - 1;
      for (int i = 0; i < n; i++) {
         int j = i + 1;
         float ey0 = py[i] + ny[i] * we[i], ez0 = pz[i] + nz[i] * we[i];
         float ey1 = py[j] + ny[j] * we[j], ez1 = pz[j] + nz[j] * we[j];
         float sy0 = py[i] - ny[i] * ws[i], sz0 = pz[i] - nz[i] * ws[i];
         float sy1 = py[j] - ny[j] * ws[j], sz1 = pz[j] - nz[j] * ws[j];
         quad2(vc, m, shade(col, 1.12F), t, sy0, sz0, t, sy1, sz1, t, ey1, ez1, t, ey0, ez0);
         quad2(vc, m, shade(col, 0.78F), -t, sy0, sz0, -t, sy1, sz1, -t, ey1, ez1, -t, ey0, ez0);
         quad2(vc, m, shade(col, 0.95F), t, sy0, sz0, t, sy1, sz1, -t, sy1, sz1, -t, sy0, sz0);
         quad2(vc, m, shade(col, 0.62F), t, ey0, ez0, t, ey1, ez1, -t, ey1, ez1, -t, ey0, ez0);
      }

      for (int i : new int[]{0, n}) {
         quad2(vc, m, shade(col, 0.85F), t, py[i] - ny[i] * ws[i], pz[i] - nz[i] * ws[i], t, py[i] + ny[i] * we[i], pz[i] + nz[i] * we[i],
            -t, py[i] + ny[i] * we[i], pz[i] + nz[i] * we[i], -t, py[i] - ny[i] * ws[i], pz[i] - nz[i] * ws[i]);
      }
   }

   /** Runder Ring in der y-z-Ebene (Fingerring des Karambits). */
   private static void ring(VertexConsumer vc, Matrix4f m, float yc, float zc, float rOut, float rIn, float t, int col) {
      int seg = 28;
      for (int k = 0; k < seg; k++) {
         double a0 = Math.PI * 2.0 * k / seg;
         double a1 = Math.PI * 2.0 * (k + 1) / seg;
         float c0 = (float)Math.cos(a0), s0 = (float)Math.sin(a0), c1 = (float)Math.cos(a1), s1 = (float)Math.sin(a1);
         float oy0 = yc + rOut * c0, oz0 = zc + rOut * s0, oy1 = yc + rOut * c1, oz1 = zc + rOut * s1;
         float iy0 = yc + rIn * c0, iz0 = zc + rIn * s0, iy1 = yc + rIn * c1, iz1 = zc + rIn * s1;
         float light = 0.9F + 0.15F * (float)Math.sin(a0 + 0.8);
         quad2(vc, m, shade(col, 1.1F * light), t, iy0, iz0, t, oy0, oz0, t, oy1, oz1, t, iy1, iz1);
         quad2(vc, m, shade(col, 0.75F * light), -t, iy0, iz0, -t, oy0, oz0, -t, oy1, oz1, -t, iy1, iz1);
         quad2(vc, m, shade(col, 0.92F * light), t, oy0, oz0, t, oy1, oz1, -t, oy1, oz1, -t, oy0, oz0);
         quad2(vc, m, shade(col, 0.6F), t, iy0, iz0, t, iy1, iz1, -t, iy1, iz1, -t, iy0, iz0);
      }
   }
   /**
    * Durchgehende, gebogene Klinge: Mittellinie in der y-z-Ebene ab y0, Breite und Biegung je Position q (0 Ansatz …
    * 1 Spitze). Querschnitt wie ein echtes Messer: dicker Rücken, flacher Klingenspiegel, deutliche Schliff-Kante und
    * polierte Schneide innen (−z). Farben werden pro Eckpunkt berechnet (Finish, gebürsteter Stahl, Glanzband), dadurch
    * wirkt die Oberfläche wie eine Textur.
    */
   private static void curvedBlade(
      VertexConsumer vc, Matrix4f m, int finish, int base, float y0, float len, int n, java.util.function.Function<Float, Float> width, java.util.function.Function<Float, Float> bend
   ) {
      n = Math.max(n, 40);
      float spineT = 0.0115F;
      float edgeT = 0.0012F;
      // Querschnitt: s = 0 Schneide … 1 Rücken, mit Dicke je Station
      float[] st = {0.0F, 0.07F, 0.42F, 0.6F, 0.85F, 1.0F};
      float[] th = {edgeT, edgeT * 2.2F, spineT * 0.92F, spineT, spineT, spineT * 0.8F};
      float[] py = new float[n + 1];
      float[] pz = new float[n + 1];
      float[] ny = new float[n + 1];
      float[] nz = new float[n + 1];
      float[] w = new float[n + 1];
      float y = y0;
      float z = 0.0F;
      float ds = len / n;
      for (int i = 0; i <= n; i++) {
         float q = (float)i / n;
         float a = bend.apply(q);
         float dy = (float)Math.cos(a);
         float dz = -(float)Math.sin(a);
         py[i] = y;
         pz[i] = z;
         ny[i] = dz;
         nz[i] = -dy;
         w[i] = width.apply(q);
         y += dy * ds;
         z += dz * ds;
      }

      int k = st.length;
      for (int i = 0; i < n; i++) {
         int j = i + 1;
         float tipK = j == n ? 0.15F : 1.0F;
         for (int c = 0; c < k - 1; c++) {
            int d = c + 1;
            float yi0 = py[i] + ny[i] * w[i] * (0.62F - st[c]), zi0 = pz[i] + nz[i] * w[i] * (0.62F - st[c]);
            float yi1 = py[i] + ny[i] * w[i] * (0.62F - st[d]), zi1 = pz[i] + nz[i] * w[i] * (0.62F - st[d]);
            float yj0 = py[j] + ny[j] * w[j] * (0.62F - st[c]), zj0 = pz[j] + nz[j] * w[j] * (0.62F - st[c]);
            float yj1 = py[j] + ny[j] * w[j] * (0.62F - st[d]), zj1 = pz[j] + nz[j] * w[j] * (0.62F - st[d]);
            float ti0 = th[c], ti1 = th[d], tj0 = Math.max(edgeT, th[c] * tipK), tj1 = Math.max(edgeT, th[d] * tipK);
            float qi = (float)i / n, qj = (float)j / n;
            // Vorderseite (+x) und Rückseite (−x), jede Ecke mit eigener Farbe
            quadC(vc, m,
               surface(finish, base, qi, st[c], true), ti0, yi0, zi0,
               surface(finish, base, qj, st[c], true), tj0, yj0, zj0,
               surface(finish, base, qj, st[d], true), tj1, yj1, zj1,
               surface(finish, base, qi, st[d], true), ti1, yi1, zi1);
            quadC(vc, m,
               surface(finish, base, qi, st[c], false), -ti0, yi0, zi0,
               surface(finish, base, qj, st[c], false), -tj0, yj0, zj0,
               surface(finish, base, qj, st[d], false), -tj1, yj1, zj1,
               surface(finish, base, qi, st[d], false), -ti1, yi1, zi1);
         }

         // Rücken (gerundet wirkender, dunklerer Streifen) und polierte Schneide
         float ys0 = py[i] - ny[i] * w[i] * 0.38F, zs0 = pz[i] - nz[i] * w[i] * 0.38F;
         float ys1 = py[j] - ny[j] * w[j] * 0.38F, zs1 = pz[j] - nz[j] * w[j] * 0.38F;
         float sT0 = th[k - 1], sT1 = Math.max(edgeT, th[k - 1] * tipK);
         int spine = mix(finish(finish, base, (float)i / n, 1.0F), 0xFF15121E, 0.4F);
         quad2(vc, m, spine, sT0, ys0, zs0, sT1, ys1, zs1, -sT1, ys1, zs1, -sT0, ys0, zs0);
         float ye0 = py[i] + ny[i] * w[i] * 0.62F, ze0 = pz[i] + nz[i] * w[i] * 0.62F;
         float ye1 = py[j] + ny[j] * w[j] * 0.62F, ze1 = pz[j] + nz[j] * w[j] * 0.62F;
         quad2(vc, m, 0xFFF4F6FA, edgeT, ye0, ze0, edgeT, ye1, ze1, -edgeT, ye1, ze1, -edgeT, ye0, ze0);
      }

      // Abschluss am Ansatz
      for (int c = 0; c < k - 1; c++) {
         int d = c + 1;
         int col = mix(finish(finish, base, 0.0F, 0.5F), 0xFF000000, 0.35F);
         quad2(vc, m, col,
            th[c], py[0] + ny[0] * w[0] * (0.62F - st[c]), pz[0] + nz[0] * w[0] * (0.62F - st[c]),
            th[d], py[0] + ny[0] * w[0] * (0.62F - st[d]), pz[0] + nz[0] * w[0] * (0.62F - st[d]),
            -th[d], py[0] + ny[0] * w[0] * (0.62F - st[d]), pz[0] + nz[0] * w[0] * (0.62F - st[d]),
            -th[c], py[0] + ny[0] * w[0] * (0.62F - st[c]), pz[0] + nz[0] * w[0] * (0.62F - st[c]));
      }
   }

   /** Oberflächenfarbe der Klinge an (q entlang, s quer): Finish + Schliff-Fase + gebürstete Riefen + Glanzband. */
   private static int surface(int finish, int base, float q, float s, boolean front) {
      int col = finish(finish, base, q, s);
      // feine Schleif-Riefen entlang der Klinge
      float brushed = (float)(Math.sin(s * 140.0 + Math.sin(q * 9.0) * 2.0) * 0.035 + Math.sin(s * 57.0 + q * 3.0) * 0.03);
      float light = front ? 1.06F : 0.8F;
      if (s < 0.42F) {
         // Schliff-Fase zur Schneide: anderer Winkel, heller und silbriger
         float b = 1.0F - s / 0.42F;
         col = mix(col, 0xFFE9EDF4, 0.18F + 0.3F * b);
         light *= front ? 1.12F : 0.92F;
      }

      // Glanzband, das über die Klinge wandert (Reflexion)
      float band = (float)Math.exp(-Math.pow((s - 0.68F - 0.18F * Math.sin(q * 3.3F)) / 0.13F, 2.0));
      col = mix(col, 0xFFFFFFFF, (front ? 0.32F : 0.12F) * band);
      return shade(col, light + brushed);
   }

   /** Viereck mit Farbe pro Ecke, von beiden Seiten sichtbar. */
   private static void quadC(
      VertexConsumer vc, Matrix4f m, int ca, float ax, float ay, float az, int cb, float bx, float by, float bz, int cc, float cx, float cy, float cz, int cd, float dx, float dy, float dz
   ) {
      vc.addVertex(m, ax, ay, az).setColor(ca);
      vc.addVertex(m, bx, by, bz).setColor(cb);
      vc.addVertex(m, cx, cy, cz).setColor(cc);
      vc.addVertex(m, dx, dy, dz).setColor(cd);
      vc.addVertex(m, dx, dy, dz).setColor(cd);
      vc.addVertex(m, cx, cy, cz).setColor(cc);
      vc.addVertex(m, bx, by, bz).setColor(cb);
      vc.addVertex(m, ax, ay, az).setColor(ca);
   }
   /** Viereck (x, y, z je Ecke) von beiden Seiten sichtbar. */
   private static void quad2(
      VertexConsumer vc, Matrix4f m, int c, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz
   ) {
      quad(vc, m, c, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz);
      quad(vc, m, c, dx, dy, dz, cx, cy, cz, bx, by, bz, ax, ay, az);
   }
   // ================================================================== Butterfly (Balisong)

   private static final int BF_HANDLE = 0xFFA98BEE;
   private static final int BF_HANDLE_DARK = 0xFF6F55B8;
   private static final int BF_PIN = 0xFFEDE6FF;

   /**
    * Butterfly-Messer im eigenen Raum: Drehpunkt im Ursprung, Klinge entlang +y. Die Griffe drehen sich um die x-Achse:
    * 0° = über der Klinge (zugeklappt), 180° = nach unten (offen in der Hand).
    */
   public static void submitButterfly(PoseStack matrices, SubmitNodeCollector queue, Look look, float handleA, float handleB) {
      float scale = look.bladeLength() / 0.95F;
      queue.submitCustomGeometry(matrices, WorldRender.solidColor(), (entry, vc) -> butterfly(entry.pose(), vc, look, scale, handleA, handleB, false));
      queue.submitCustomGeometry(matrices, WorldRender.glow(), (entry, vc) -> butterfly(entry.pose(), vc, look, scale, handleA, handleB, true));
   }

   private static void butterfly(Matrix4f m, VertexConsumer vc, Look look, float scale, float handleA, float handleB, boolean glow) {
      int base = 0xFF000000 | look.color() & 16777215;
      int finish = look.finish();
      if (glow) {
         return;
      }

      // Klinge wie im Video: lang und schlank, im letzten Drittel zur Hakenspitze geschwungen
      curvedBlade(vc, m, finish, base, 0.03F * scale, 0.36F * scale, 22, q -> (q < 0.55F ? 0.05F : 0.05F * (float)Math.pow(Math.max(0.0F, 1.0F - (q - 0.55F) / 0.45F), 1.1F)) * scale + 0.0015F, q -> (float)Math.toRadians(q < 0.45F ? 0.0 : 85.0 * Math.pow(Math.max(0.0, (q - 0.45) / 0.55), 1.5)));      // Klingenansatz (Tang) zwischen den Griffen
      box(vc, m, -0.006F, -0.012F * scale, -0.022F * scale, 0.006F, 0.035F * scale, 0.022F * scale, finish(finish, base, 0.0F, 0.5F), 1.0F);
      // zwei Griffe links und rechts der Klinge
      handle(vc, m, scale, handleA, 0.0072F, 0.019F, true, finish, base);
      handle(vc, m, scale, handleB, -0.019F, -0.0072F, false, finish, base);
      // Drehbolzen
      box(vc, m, -0.021F, -0.006F * scale, -0.006F * scale, 0.021F, 0.006F * scale, 0.006F * scale, BF_PIN, 1.1F);
   }

   private static void handle(VertexConsumer vc, Matrix4f m, float scale, float angle, float x0, float x1, boolean latch, int finish, int base) {
      Matrix4f h = new Matrix4f(m).rotateX((float)Math.toRadians(angle));
      float len = 0.41F * scale;
      float half = 0.02F * scale;
      int col = finish == LAVENDER ? BF_HANDLE : mix(finish(finish, base, 0.3F, 0.5F), 0xFF1C1C22, 0.35F);
      int dark = finish == LAVENDER ? BF_HANDLE_DARK : mix(col, 0xFF000000, 0.45F);
      // Schienen mit Aussparung in der Mitte (wie echte Balisong-Griffe)
      box(vc, h, x0, 0.0F, -half, x1, len, -half + 0.008F * scale, col, 1.1F);
      box(vc, h, x0, 0.0F, half - 0.008F * scale, x1, len, half, col, 1.0F);
      box(vc, h, x0 + (x1 - x0) * 0.25F, 0.02F * scale, -half + 0.008F * scale, x1 - (x1 - x0) * 0.25F, len - 0.02F * scale, half - 0.008F * scale, dark, 0.9F);
      // Kappe am Ende und Bolzen
      box(vc, h, x0, len, -half, x1, len + 0.022F * scale, half, col, 1.15F);
      box(vc, h, x0 - 0.001F, len - 0.012F * scale, -0.004F * scale, x1 + 0.001F, len - 0.004F * scale, 0.004F * scale, BF_PIN, 1.1F);
      if (latch) {
         // Verschluss am Griffende
         box(vc, h, x1, len - 0.05F * scale, -0.005F * scale, x1 + 0.004F, len + 0.01F * scale, 0.005F * scale, BF_PIN, 1.0F);
      }
   }
   /**
    * Karambit in der Hand der Ich-Ansicht (Matrizen stehen im Raum der Hand). {@code spin} dreht das Messer um den
    * Fingerring (Inspect).
    */
   public static void submitKarambit(PoseStack matrices, SubmitNodeCollector queue, Look look, float spin) {
      matrices.pushPose();
      if (spin != 0.0F) {
         matrices.translate(0.0F, -0.262F, -0.067F);
         matrices.rotate(Axis.XP.rotation(spin));
         matrices.translate(0.0F, 0.262F, 0.067F);
      }

      float scale = look.bladeLength() / 0.95F;
      queue.submitCustomGeometry(matrices, WorldRender.solidColor(), (entry, vc) -> karambit(entry.pose(), vc, look, scale, false));
      queue.submitCustomGeometry(matrices, WorldRender.glow(), (entry, vc) -> karambit(entry.pose(), vc, look, scale, true));
      matrices.popPose();
   }
}
