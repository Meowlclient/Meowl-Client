package com.lennart.nebula.cosmetic.mods;

import com.lennart.nebula.cosmetic.Art;
import com.lennart.nebula.cosmetic.Cosmetic;
import com.lennart.nebula.cosmetic.CosmeticContext;
import com.lennart.nebula.cosmetic.CosmeticValue;
import com.lennart.nebula.cosmetic.Shapes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.Items;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;

/** Hüte und Kopfschmuck – alle Meowl-Spieler sehen sie. */
public class Hats extends Cosmetic {
   public static final String[] STYLES = {
      "Zylinder", "Krone", "Partyhut", "Hexenhut", "Cowboyhut", "Wikingerhelm", "Propellermütze", "Weihnachtsmütze", "Kochmütze", "Katzenohren",
      "Hasenohren", "Teufelshörner", "Blumenkranz", "Pilzhut", "Astronautenhelm", "Kopfhörer", "Konoha-Stirnband", "Strohhut", "Piratenhut",
      "Zaubererhut", "Einhorn", "Geweih", "Kürbiskopf", "Sakura-Geist"
   };

   public Hats() {
      super("Hüte", "Hüte und Kopfschmuck: Zylinder, Krone, Hexenhut, Propellermütze, Katzenohren, Astronautenhelm und mehr.", "hat", Items.LEATHER_HELMET, STYLES);
      this.groups();
   }

   @Override
   public void render(CosmeticContext c, CosmeticValue v) {
      float t = c.time;
      float b = c.bright;
      float s = v.size();
      String st = v.style();
      c.onHead(() -> {
         c.solid((m, vc) -> {
            scale(m, s);
            this.solid(m, vc, st, v, t, b);
         });
         c.glow((m, vc) -> {
            scale(m, s);
            this.glow(m, vc, st, v, t);
         });
      });
   }

   private static void scale(PoseStack m, float s) {
      m.translate(0.0F, -8.0F, 0.0F);
      m.scale(s, s, s);
      m.translate(0.0F, 8.0F, 0.0F);
   }

   private void solid(PoseStack m, VertexConsumer vc, String st, CosmeticValue v, float t, float b) {
      switch (st) {
         case "Krone": {
            int gold = v.tint(0xFFE6C27C);
            int light = Art.bright(gold, 1.12F);
            int velvet = v.tint(0xFFC85A7C);
            // Samtkappe, darüber das achteckige Goldband mit Wulsten oben und unten
            Art.dome(m, vc, 0.0F, -8.0F, 0.0F, 4.3F, 2.9F, 4.3F, 3, 8, velvet, b);
            Art.ring(m, vc, 0.0F, 0.0F, -10.3F, -7.9F, 4.4F, 4.85F, 1.0F, 8, 22.5F, gold, b);
            Art.ring(m, vc, 0.0F, 0.0F, -10.65F, -10.2F, 4.3F, 5.05F, 1.0F, 8, 22.5F, light, b);
            Art.ring(m, vc, 0.0F, 0.0F, -8.25F, -7.75F, 4.5F, 5.15F, 1.0F, 8, 22.5F, light, b);
            for (int i = 0; i < 8; i++) {
               double a = Math.toRadians(i * 45.0 + 22.5);
               float px = (float)Math.cos(a) * 4.7F, pz = (float)Math.sin(a) * 4.7F;
               float h = i % 2 == 0 ? 3.6F : 2.4F;
               // Zacke (vierkantig) mit Perle
               Art.taper(m, vc, px, -10.55F, pz, 0.8F, px * 1.03F, -10.55F - h, pz * 1.03F, 0.0F, 4, (float)Math.toDegrees(a) + 45.0F, gold, b, false, false);
               Art.sphere(m, vc, px * 1.03F, -10.85F - h, pz * 1.03F, 0.48F, 0.48F, 0.48F, 3, 6, 0xFFF8F1E8, b);
               // Edelstein mitten auf der Seite
               double c = Math.toRadians(i * 45.0);
               float gx = (float)Math.cos(c) * 4.95F, gz = (float)Math.sin(c) * 4.95F;
               Art.gem(m, vc, gx, -9.1F, gz, 0.55F, 0.75F, 0.75F, 6, 0.0F, i % 2 == 0 ? 0xFFE87A96 : 0xFF8FB4EA, b);
            }

            // kleines Kreuz aus Gold oben auf der Kappe
            Art.bevel(m, vc, -0.3F, -12.6F, -0.3F, 0.3F, -10.4F, 0.3F, 0.08F, gold, b);
            Art.bevel(m, vc, -0.9F, -12.0F, -0.3F, 0.9F, -11.45F, 0.3F, 0.08F, gold, b);
            break;
         }
         case "Partyhut": {
            m.pushPose();
            m.rotate(Axis.ZP.rotationDegrees(8.0F));
            int[] cols = {0xFFF7B3C8, 0xFFB6DDF0, 0xFFFFF0C2, 0xFFCDB8EC, 0xFFF7B3C8, 0xFFB6DDF0};
            for (int i = 0; i < 6; i++) {
               float q0 = i / 6.0F, q1 = (i + 1) / 6.0F;
               float r0 = 3.7F * (1.0F - q0 * 0.93F), r1 = i == 5 ? 0.0F : 3.7F * (1.0F - q1 * 0.93F);
               Art.taper(m, vc, 0.0F, -8.1F - q0 * 9.6F, 0.0F, r0, 0.0F, -8.1F - q1 * 9.6F, 0.0F, r1, 10, i * 9.0F, v.tint(cols[i]), b, false, false);
            }

            // Rüschenkragen und Bommel
            Art.ring(m, vc, 0.0F, 0.0F, -8.6F, -8.0F, 3.3F, 4.4F, 1.0F, 16, 0.0F, v.tint(0xFFFBF3EE), b);
            for (int i = 0; i < 12; i++) {
               double a = Math.PI * 2.0 * i / 12.0;
               Art.gem(m, vc, (float)Math.cos(a) * 4.45F, -8.3F, (float)Math.sin(a) * 4.45F, 0.62F, 0.35F, 0.35F, 5, i * 30.0F, v.tint(i % 2 == 0 ? 0xFFF7C8D6 : 0xFFFBF3EE), b);
            }

            Art.sphere(m, vc, 0.0F, -18.3F, 0.0F, 1.15F, 1.1F, 1.15F, 3, 7, 0xFFFFE7A6, b);
            Art.sphere(m, vc, 0.6F, -18.8F, 0.4F, 0.65F, 0.6F, 0.65F, 3, 6, 0xFFF7B3C8, b);
            Art.sphere(m, vc, -0.5F, -18.9F, -0.4F, 0.6F, 0.55F, 0.6F, 3, 6, 0xFFB6DDF0, b);
            m.popPose();
            break;
         }
         case "Hexenhut": {
            int hat = v.tint(0xFF4B3D6B);
            int dark = Art.bright(hat, 0.8F);
            Art.ring(m, vc, 0.0F, 0.0F, -8.6F, -8.1F, 3.9F, 7.4F, 1.0F, 14, 0.0F, hat, b);
            Art.ring(m, vc, 0.0F, 0.0F, -8.35F, -7.75F, 6.9F, 7.8F, 1.0F, 14, 0.0F, dark, b);
            float px = 0.0F, py = -8.5F, pz = 0.0F, pr = 4.1F;
            for (int i = 0; i < 8; i++) {
               float q = (i + 1) / 8.0F;
               float nr = i == 7 ? 0.0F : 4.1F * (1.0F - q * 0.88F);
               float nx = 0.0F, ny = -8.5F - q * 11.5F, nz = q * q * 4.2F;
               if (i >= 6) {
                  ny += (q - 0.75F) * 6.0F;
               }

               Art.taper(m, vc, px, py, pz, pr, nx, ny, nz, nr, 8, i * 6.0F, i % 2 == 0 ? hat : Art.bright(hat, 0.93F), b, false, false);
               px = nx;
               py = ny;
               pz = nz;
               pr = nr;
            }

            // rosa Band mit goldener Schnalle
            Art.ring(m, vc, 0.0F, 0.0F, -10.2F, -8.6F, 3.95F, 4.35F, 1.0F, 8, 0.0F, v.tint(0xFFE8A0B8), b);
            Art.bevel(m, vc, -1.3F, -10.5F, -4.65F, 1.3F, -8.3F, -4.25F, 0.18F, Art.GOLD, b);
            Art.box(m, vc, -0.65F, -9.85F, -4.72F, 0.65F, -8.95F, -4.6F, dark, b);
            Art.gem(m, vc, px, py + 0.6F, pz + 0.3F, 0.4F, 0.4F, 0.6F, 5, 0.0F, Art.GOLD, b);
            break;
         }
         case "Cowboyhut": {
            int leather = v.tint(0xFF9A6A4A);
            int dark = v.tint(0xFF5E4033);
            // Krempe mit hochgebogenen Seiten
            int n = 18;
            for (int i = 0; i < n; i++) {
               double a0 = Math.PI * 2.0 * i / n, a1 = Math.PI * 2.0 * (i + 1) / n;
               float c0 = (float)Math.cos(a0), s0 = (float)Math.sin(a0), c1 = (float)Math.cos(a1), s1 = (float)Math.sin(a1);
               float y0 = -8.3F - 1.9F * c0 * c0, y1 = -8.3F - 1.9F * c1 * c1;
               float ix = c0 * 3.9F, iz = s0 * 3.9F, jx = c1 * 3.9F, jz = s1 * 3.9F;
               float ox = c0 * 7.4F, oz = s0 * 6.3F, qx = c1 * 7.4F, qz = s1 * 6.3F;
               float mx = (ix + qx) / 2.0F, mz = (iz + qz) / 2.0F, my = (y0 + y1) / 2.0F - 0.25F;
               Art.face(m, vc, leather, b, mx, my + 0.6F, mz, ix, -8.3F, iz, ox, y0, oz, qx, y1, qz, jx, -8.3F, jz);
               Art.face(m, vc, dark, b, mx, my - 0.6F, mz, ix, -7.85F, iz, ox, y0 + 0.45F, oz, qx, y1 + 0.45F, qz, jx, -7.85F, jz);
               Art.face(m, vc, dark, b, mx * 0.8F, my, mz * 0.8F, ox, y0, oz, qx, y1, qz, qx, y1 + 0.45F, qz, ox, y0 + 0.45F, oz);
            }

            // Krone mit Knick oben
            Art.frustum(m, vc, 0.0F, 0.0F, -8.3F, -12.4F, 3.95F, 4.35F, 3.5F, 3.9F, 10, 0.0F, leather, b, false, false);
            for (int i = 0; i < 10; i++) {
               double a0 = Math.PI * 2.0 * i / 10.0, a1 = Math.PI * 2.0 * (i + 1) / 10.0;
               Art.face(
                  m, vc, leather, b, 0.0F, -10.0F, 0.0F, 0.0F, -11.6F, 0.0F, (float)Math.cos(a0) * 3.5F, -12.4F, (float)Math.sin(a0) * 3.9F, (float)Math.cos(a1) * 3.5F,
                  -12.4F, (float)Math.sin(a1) * 3.9F
               );
            }

            Art.ring(m, vc, 0.0F, 0.0F, -9.7F, -8.4F, 3.9F, 4.3F, 1.1F, 10, 0.0F, dark, b);
            Art.gem(m, vc, -4.35F, -9.05F, 0.0F, 0.55F, 0.4F, 0.4F, 6, 0.0F, Art.SILVER, b);
            break;
         }
         case "Wikingerhelm": {
            int steel = v.tint(0xFFB4BAC4);
            int bronze = v.tint(0xFFC0925E);
            Art.dome(m, vc, 0.0F, -7.4F, 0.0F, 4.85F, 4.4F, 4.85F, 4, 8, steel, b);
            Art.ring(m, vc, 0.0F, 0.0F, -8.3F, -6.9F, 4.55F, 5.0F, 1.0F, 12, 0.0F, bronze, b);
            for (int i = 0; i < 10; i++) {
               double a = Math.PI * 2.0 * i / 10.0;
               Art.sphere(m, vc, (float)Math.cos(a) * 5.05F, -7.6F, (float)Math.sin(a) * 5.05F, 0.28F, 0.28F, 0.28F, 2, 4, Art.bright(bronze, 1.15F), b);
            }

            // Kamm über den Helm und Nasenschutz
            Art.bevel(m, vc, -0.45F, -12.1F, -3.0F, 0.45F, -11.1F, 3.0F, 0.15F, bronze, b);
            Art.bevel(m, vc, -0.55F, -8.1F, -5.05F, 0.55F, -3.6F, -4.55F, 0.15F, steel, b);
            for (int side = -1; side <= 1; side += 2) {
               int bone = 0xFFF1E8D6;
               Art.ring(m, vc, side * 4.75F, 0.0F, -9.7F, -8.7F, 0.0F, 1.15F, 1.0F, 6, 0.0F, bronze, b);
               Art.limb(m, vc, side * 4.6F, -9.2F, 0.0F, side * 6.7F, -10.7F, -0.4F, 1.0F, 0.8F, 6, bone, b);
               Art.limb(m, vc, side * 6.7F, -10.7F, -0.4F, side * 7.5F, -13.1F, -0.6F, 0.8F, 0.5F, 6, bone, b);
               Art.limb(m, vc, side * 7.5F, -13.1F, -0.6F, side * 7.1F, -15.2F, -0.4F, 0.5F, 0.0F, 6, Art.mix(bone, Art.CHARCOAL, 0.55F), b);
            }
            break;
         }
         case "Propellermütze": {
            int[] q = {v.tint(0xFFF29AAE), v.tint(0xFF9CCBEA), v.tint(0xFFF6E3A0), v.tint(0xFFA9D8B4)};
            // Kappe aus vier Farbfeldern
            for (int i = 0; i < 4; i++) {
               for (int k = 0; k < 2; k++) {
                  int seg = i * 2 + k;
                  for (int lat = 0; lat < 3; lat++) {
                     double t0 = Math.PI / 2.0 * lat / 3.0, t1 = Math.PI / 2.0 * (lat + 1) / 3.0;
                     double p0 = Math.PI * 2.0 * seg / 8.0, p1 = Math.PI * 2.0 * (seg + 1) / 8.0;
                     float y0 = -7.8F - (float)Math.cos(t0) * 3.1F, y1 = -7.8F - (float)Math.cos(t1) * 3.1F;
                     float r0 = (float)Math.sin(t0) * 4.55F, r1 = (float)Math.sin(t1) * 4.55F;
                     float[] pts = lat == 0
                        ? new float[]{0.0F, y0, 0.0F, (float)Math.cos(p0) * r1, y1, (float)Math.sin(p0) * r1, (float)Math.cos(p1) * r1, y1, (float)Math.sin(p1) * r1}
                        : new float[]{
                           (float)Math.cos(p0) * r0, y0, (float)Math.sin(p0) * r0, (float)Math.cos(p1) * r0, y0, (float)Math.sin(p1) * r0, (float)Math.cos(p1) * r1, y1,
                           (float)Math.sin(p1) * r1, (float)Math.cos(p0) * r1, y1, (float)Math.sin(p0) * r1
                        };
                     Art.face(m, vc, q[i], b, 0.0F, -7.6F, 0.0F, pts);
                  }
               }
            }

            // Schirm vorne, Knopf und Propeller
            Art.bevel(m, vc, -4.1F, -8.25F, -7.3F, 4.1F, -7.75F, -3.8F, 0.2F, q[0], b);
            Art.limb(m, vc, 0.0F, -10.8F, 0.0F, 0.0F, -12.6F, 0.0F, 0.32F, 0.28F, 6, Art.SILVER, b);
            Art.gem(m, vc, 0.0F, -10.9F, 0.0F, 0.75F, 0.35F, 0.3F, 8, 0.0F, Art.WHITE, b);
            m.pushPose();
            m.translate(0.0F, -12.7F, 0.0F);
            m.rotate(Axis.YP.rotationDegrees(t * 900.0F));
            for (int k = 0; k < 2; k++) {
               m.pushPose();
               m.rotate(Axis.YP.rotationDegrees(k * 180.0F));
               Art.sheet(m, vc, 0xFFF4F1EC, b, 0.6F, -0.15F, -0.7F, 5.2F, 0.25F, -0.55F, 5.2F, -0.25F, 0.55F, 0.6F, 0.15F, 0.7F);
               m.popPose();
            }

            Art.sphere(m, vc, 0.0F, 0.0F, 0.0F, 0.6F, 0.45F, 0.6F, 2, 6, q[0], b);
            m.popPose();
            break;
         }
         case "Weihnachtsmütze": {
            int red = v.tint(0xFFD8495F);
            int fur = 0xFFF8F3EC;
            float px = 0.0F, py = -9.4F, pz = 0.0F, pr = 4.15F;
            for (int i = 0; i < 7; i++) {
               float q = (i + 1) / 7.0F;
               float nr = i == 6 ? 0.25F : 4.15F * (1.0F - q * 0.82F);
               float droop = q * q * 6.4F;
               float nx = droop, ny = -9.4F - q * 8.0F + droop * 0.95F, nz = 0.0F;
               Art.taper(m, vc, px, py, pz, pr, nx, ny, nz, nr, 9, i * 8.0F, i % 2 == 0 ? red : Art.bright(red, 0.93F), b, false, i == 6);
               px = nx;
               py = ny;
               pz = nz;
               pr = nr;
            }

            Art.ring(m, vc, 0.0F, 0.0F, -9.9F, -7.6F, 3.9F, 4.85F, 1.0F, 12, 0.0F, fur, b);
            Art.ring(m, vc, 0.0F, 0.0F, -9.6F, -7.9F, 4.2F, 5.05F, 1.0F, 12, 15.0F, Art.bright(fur, 0.97F), b);
            Art.sphere(m, vc, px + 0.4F, py + 0.2F, pz, 1.35F, 1.3F, 1.35F, 3, 7, fur, b);
            Art.sphere(m, vc, px + 0.9F, py - 0.4F, pz + 0.5F, 0.8F, 0.75F, 0.8F, 3, 6, Art.bright(fur, 0.96F), b);
            break;
         }
         case "Kochmütze": {
            int white = v.tint(0xFFFAF8F4);
            Art.frustum(m, vc, 0.0F, 0.0F, -7.7F, -11.3F, 4.45F, 4.45F, 4.6F, 4.6F, 12, 0.0F, white, b, false, true);
            for (int i = 0; i < 12; i++) {
               double a = Math.PI * 2.0 * (i + 0.5) / 12.0;
               float x = (float)Math.cos(a) * 4.62F, z = (float)Math.sin(a) * 4.62F;
               Art.limb(m, vc, x, -7.8F, z, x * 1.02F, -11.2F, z * 1.02F, 0.28F, 0.28F, 4, Art.bright(white, 0.94F), b);
            }

            // gepuffte Haube aus mehreren Wölbungen
            Art.sphere(m, vc, 0.0F, -14.0F, 0.0F, 5.0F, 3.3F, 5.0F, 4, 9, white, b);
            for (int i = 0; i < 4; i++) {
               double a = Math.PI * 2.0 * i / 4.0 + 0.4;
               Art.sphere(m, vc, (float)Math.cos(a) * 3.2F, -13.0F, (float)Math.sin(a) * 3.2F, 3.0F, 2.6F, 3.0F, 3, 7, Art.bright(white, 0.97F), b);
            }

            Art.sphere(m, vc, 0.4F, -16.6F, -0.3F, 2.6F, 1.6F, 2.6F, 3, 7, white, b);
            break;
         }
         case "Katzenohren":
         case "Hasenohren": {
            boolean bunny = st.equals("Hasenohren");
            int fur = v.tint(bunny ? 0xFFF4EFEA : 0xFF3A353E);
            int inner = v.tint(0xFFF4A9BF);
            for (int side = -1; side <= 1; side += 2) {
               float tw = Mth.sin(t * 1.3F + side * 1.7F);
               float twitch = tw > 0.0F ? (float)Math.pow(tw, 20.0) * 22.0F : 0.0F;
               m.pushPose();
               m.translate(side * 2.6F, -8.0F, 0.0F);
               m.rotate(Axis.ZP.rotationDegrees(side * (bunny ? 8.0F + Mth.sin(t * 1.7F + side) * 5.0F : 12.0F) + side * twitch * 0.4F));
               m.rotate(Axis.XP.rotationDegrees(-twitch));
               m.scale(1.0F, 1.0F, 0.45F);
               if (bunny) {
                  Art.taper(m, vc, 0.0F, 0.2F, 0.0F, 1.2F, 0.0F, -4.8F, 0.0F, 1.4F, 6, 0.0F, fur, b, false, false);
                  Art.taper(m, vc, 0.0F, -4.8F, 0.0F, 1.4F, side * 0.3F, -8.4F, 0.0F, 0.7F, 6, 0.0F, fur, b, false, true);
                  m.translate(0.0F, 0.0F, -0.9F);
                  Art.taper(m, vc, 0.0F, -0.6F, 0.0F, 0.7F, 0.0F, -4.8F, 0.0F, 0.85F, 6, 0.0F, inner, b, false, false);
                  Art.taper(m, vc, 0.0F, -4.8F, 0.0F, 0.85F, side * 0.3F, -7.6F, 0.0F, 0.4F, 6, 0.0F, inner, b, false, true);
               } else {
                  Art.taper(m, vc, 0.0F, 0.2F, 0.0F, 2.1F, side * 0.4F, -3.7F, 0.0F, 0.0F, 4, 45.0F, fur, b, true, false);
                  m.translate(0.0F, 0.0F, -0.9F);
                  Art.taper(m, vc, 0.0F, -0.4F, 0.0F, 1.2F, side * 0.35F, -2.9F, 0.0F, 0.0F, 4, 45.0F, inner, b, true, false);
                  Art.gem(m, vc, side * 0.38F, -3.6F, 0.9F, 0.3F, 0.5F, 0.2F, 4, 45.0F, Art.bright(fur, 1.35F), b);
               }

               m.popPose();
            }
            break;
         }
         case "Teufelshörner":
            for (int side = -1; side <= 1; side += 2) {
               int red = v.tint(0xFFC0394A);
               int ridge = Art.bright(red, 0.78F);
               float[][] p = {{side * 2.7F, -7.8F, -1.0F, 1.0F}, {side * 3.5F, -9.9F, -1.3F, 0.85F}, {side * 3.6F, -11.8F, -1.0F, 0.62F}, {side * 3.0F, -13.2F, -0.4F, 0.38F}, {side * 2.3F, -14.0F, 0.2F, 0.0F}};
               for (int i = 0; i < 4; i++) {
                  Art.limb(m, vc, p[i][0], p[i][1], p[i][2], p[i + 1][0], p[i + 1][1], p[i + 1][2], p[i][3], p[i + 1][3], 6, i == 3 ? Art.CHARCOAL : red, b);
                  if (i < 3) {
                     Art.ring(m, vc, p[i + 1][0], p[i + 1][2], p[i + 1][1] - 0.2F, p[i + 1][1] + 0.2F, 0.0F, p[i + 1][3] + 0.15F, 1.0F, 6, 0.0F, ridge, b);
                  }
               }
            }
            break;
         case "Blumenkranz": {
            int twig = v.tint(0xFF7A5C50);
            int n = 12;
            for (int i = 0; i < n; i++) {
               double a0 = Math.PI * 2.0 * i / n, a1 = Math.PI * 2.0 * (i + 1) / n;
               float y0 = -8.5F + 0.35F * Mth.sin(i * 1.7F), y1 = -8.5F + 0.35F * Mth.sin((i + 1) * 1.7F);
               Art.limb(m, vc, (float)Math.cos(a0) * 4.75F, y0, (float)Math.sin(a0) * 4.75F, (float)Math.cos(a1) * 4.75F, y1, (float)Math.sin(a1) * 4.75F, 0.38F, 0.38F, 5, twig, b);
            }

            int[] petals = {Art.SAKURA, Art.BLOSSOM, 0xFFF6BCCD, Art.WHITE};
            for (int i = 0; i < 9; i++) {
               double a = Math.PI * 2.0 * i / 9.0 + 0.2;
               m.pushPose();
               m.translate((float)Math.cos(a) * 4.9F, -8.9F, (float)Math.sin(a) * 4.9F);
               m.rotate(Axis.YP.rotation((float)(-a)));
               m.rotate(Axis.ZP.rotationDegrees(-38.0F));
               Art.sakura(m, vc, 0.0F, 0.0F, 0.0F, i % 3 == 0 ? 1.55F : 1.25F, i * 23.0F, v.tint(petals[i % 4]), b);
               m.popPose();
               double c = a + Math.PI / 9.0;
               float lx = (float)Math.cos(c) * 4.85F, lz = (float)Math.sin(c) * 4.85F;
               Art.petal(m, vc, lx, -8.6F, lz, 1.4F, (float)Math.toDegrees(c) + 70.0F, 20.0F, v.tint(Art.LEAF), b);
               Art.gem(m, vc, lx * 1.02F, -9.0F, lz * 1.02F, 0.3F, 0.45F, 0.15F, 5, 0.0F, v.tint(Art.ROSE), b);
            }
            break;
         }
         case "Pilzhut": {
            int cap = v.tint(0xFFD9475A);
            Art.dome(m, vc, 0.0F, -8.9F, 0.0F, 6.7F, 4.4F, 6.7F, 4, 12, cap, b);
            Art.ring(m, vc, 0.0F, 0.0F, -8.95F, -8.6F, 2.5F, 6.55F, 1.0F, 16, 0.0F, 0xFFF1E4D2, b);
            for (int i = 0; i < 16; i++) {
               double a = Math.PI * 2.0 * i / 16.0;
               Art.limb(m, vc, (float)Math.cos(a) * 2.8F, -8.85F, (float)Math.sin(a) * 2.8F, (float)Math.cos(a) * 6.3F, -8.85F, (float)Math.sin(a) * 6.3F, 0.12F, 0.12F, 3, 0xFFDCCBB4, b);
            }

            float[][] dots = {{0.6F, 0.4F, 1.0F}, {0.9F, 2.2F, 0.8F}, {1.0F, 3.9F, 0.75F}, {0.45F, 3.0F, 0.7F}, {0.8F, 5.2F, 0.85F}, {1.1F, 1.2F, 0.6F}, {0.25F, 1.6F, 0.65F}};
            for (float[] d : dots) {
               float th = d[0], ph = d[1];
               float x = (float)Math.sin(th) * (float)Math.cos(ph) * 6.75F, z = (float)Math.sin(th) * (float)Math.sin(ph) * 6.75F;
               float y = -8.9F - (float)Math.cos(th) * 4.15F;
               Art.sphere(m, vc, x, y, z, d[2], d[2] * 0.45F, d[2], 2, 6, 0xFFFBF6EE, b);
            }
            break;
         }
         case "Astronautenhelm": {
            int shell = v.tint(0xFFF0EFF3);
            int trim = Art.SILVER;
            // Kragen, Rahmen um das Visier, Ohrstücke, Antenne
            Art.ring(m, vc, 0.0F, 0.0F, -0.4F, 1.5F, 4.4F, 5.8F, 1.0F, 12, 0.0F, shell, b);
            Art.ring(m, vc, 0.0F, 0.0F, 0.9F, 1.7F, 5.4F, 6.0F, 1.0F, 12, 0.0F, trim, b);
            Art.bevel(m, vc, -5.6F, -9.4F, -5.7F, 5.6F, -8.4F, -4.9F, 0.25F, shell, b);
            Art.bevel(m, vc, -5.6F, -1.4F, -5.7F, 5.6F, -0.4F, -4.9F, 0.25F, shell, b);
            Art.bevel(m, vc, -5.7F, -9.4F, -5.7F, -4.7F, -0.4F, -4.9F, 0.25F, shell, b);
            Art.bevel(m, vc, 4.7F, -9.4F, -5.7F, 5.7F, -0.4F, -4.9F, 0.25F, shell, b);
            Art.bevel(m, vc, -5.6F, -9.6F, -4.9F, 5.6F, -8.6F, 5.4F, 0.4F, shell, b);
            Art.bevel(m, vc, -5.6F, -8.6F, 4.6F, 5.6F, -0.4F, 5.6F, 0.35F, shell, b);
            for (int side = -1; side <= 1; side += 2) {
               Art.bevel(m, vc, side * 4.7F, -8.6F, -4.9F, side * 5.6F, -0.4F, 4.6F, 0.3F, shell, b);
               m.pushPose();
               m.translate(side * 5.6F, -4.6F, 0.0F);
               m.rotate(Axis.ZP.rotationDegrees(side * 90.0F));
               Art.frustum(m, vc, 0.0F, 0.0F, 0.0F, -1.0F, 1.9F, 1.9F, 1.6F, 1.6F, 8, 22.5F, trim, b, false, true);
               m.popPose();
            }

            Art.limb(m, vc, 4.6F, -9.4F, 2.0F, 5.2F, -13.0F, 2.6F, 0.2F, 0.14F, 5, trim, b);
            Art.sphere(m, vc, 5.25F, -13.2F, 2.65F, 0.45F, 0.45F, 0.45F, 2, 6, v.tint(0xFFE86A7E), b);
            break;
         }
         case "Kopfhörer": {
            int band = v.tint(0xFF34303A);
            int pad = 0xFFF2E9EE;
            int n = 10;
            for (int i = 0; i < n; i++) {
               double a0 = Math.PI * i / n, a1 = Math.PI * (i + 1) / n;
               float x0 = -(float)Math.cos(a0) * 5.05F, y0 = -4.6F - (float)Math.sin(a0) * 4.8F;
               float x1 = -(float)Math.cos(a1) * 5.05F, y1 = -4.6F - (float)Math.sin(a1) * 4.8F;
               Art.limb(m, vc, x0, y0, 0.0F, x1, y1, 0.0F, 0.55F, 0.55F, 6, band, b);
            }

            Art.bevel(m, vc, -2.6F, -9.2F, -0.9F, 2.6F, -8.4F, 0.9F, 0.3F, pad, b);
            for (int side = -1; side <= 1; side += 2) {
               m.pushPose();
               m.translate(side * 4.2F, -3.6F, 0.0F);
               m.rotate(Axis.ZP.rotationDegrees(side * 90.0F));
               Art.frustum(m, vc, 0.0F, 0.0F, 0.0F, -0.9F, 2.6F, 2.6F, 2.5F, 2.5F, 12, 0.0F, pad, b, true, false);
               Art.frustum(m, vc, 0.0F, 0.0F, -0.8F, -2.4F, 2.55F, 2.55F, 2.2F, 2.2F, 12, 0.0F, band, b, false, true);
               Art.sakura(m, vc, 0.0F, -2.45F, 0.0F, 1.25F, 18.0F, v.tint(Art.SAKURA), b);
               m.popPose();
            }
            break;
         }
         case "Konoha-Stirnband": {
            int cloth = v.tint(0xFF2A3A6A);
            int metal = 0xFFC9CED7;
            Art.bevel(m, vc, -4.45F, -6.7F, -4.45F, 4.45F, -4.9F, -4.05F, 0.12F, cloth, b);
            Art.bevel(m, vc, -4.45F, -6.7F, 4.05F, 4.45F, -4.9F, 4.45F, 0.12F, cloth, b);
            Art.bevel(m, vc, -4.45F, -6.7F, -4.45F, -4.05F, -4.9F, 4.45F, 0.12F, cloth, b);
            Art.bevel(m, vc, 4.05F, -6.7F, -4.45F, 4.45F, -4.9F, 4.45F, 0.12F, cloth, b);
            Art.bevel(m, vc, -1.2F, -6.6F, 4.3F, 1.2F, -5.0F, 4.95F, 0.3F, Art.bright(cloth, 0.85F), b);
            Art.bevel(m, vc, -3.2F, -7.0F, -4.95F, 3.2F, -4.6F, -4.45F, 0.25F, metal, b);
            for (int k = -1; k <= 1; k += 2) {
               for (int j = -1; j <= 1; j += 2) {
                  Art.sphere(m, vc, k * 2.75F, -5.8F + j * 0.8F, -4.98F, 0.2F, 0.2F, 0.15F, 2, 4, Art.bright(metal, 0.85F), b);
               }
            }

            // Blatt-Symbol: Spirale mit Spitze
            int eng = 0xFF4A4E58;
            float cx = -0.1F, cy = -5.8F;
            float lx = cx + 1.0F, ly = cy;
            for (int i = 1; i <= 14; i++) {
               double a = i * 0.55;
               float r = 0.12F + i * 0.075F;
               float nx = cx + (float)Math.cos(a) * r, ny = cy - (float)Math.sin(a) * r;
               if (i > 1) {
                  Art.limb(m, vc, lx, ly, -5.0F, nx, ny, -5.0F, 0.09F, 0.09F, 3, eng, b);
               }

               lx = nx;
               ly = ny;
            }

            Art.limb(m, vc, lx, ly, -5.0F, lx + 1.0F, ly - 0.7F, -5.0F, 0.1F, 0.0F, 3, eng, b);
            float flow = Mth.sin(t * 3.0F) * 10.0F;
            for (int k = -1; k <= 1; k += 2) {
               m.pushPose();
               m.translate(k * 0.8F, -5.8F, 4.9F);
               m.rotate(Axis.XP.rotationDegrees(35.0F + flow + k * 6.0F));
               float w = Mth.sin(t * 5.0F + k) * 0.4F;
               Art.sheet(m, vc, cloth, b, -0.55F, 0.0F, 0.0F, 0.55F, 0.0F, 0.0F, 0.55F + w, 2.6F, 0.1F, -0.55F + w, 2.6F, 0.1F);
               Art.sheet(m, vc, cloth, b, -0.55F + w, 2.6F, 0.1F, 0.55F + w, 2.6F, 0.1F, 0.5F - w, 5.0F, -0.1F, -0.4F - w, 5.3F, -0.1F);
               m.popPose();
            }
            break;
         }
         case "Strohhut": {
            int straw = v.tint(0xFFE9CD86);
            int weave = Art.bright(straw, 0.88F);
            for (int k = 0; k < 2; k++) {
               Art.ring(m, vc, 0.0F, 0.0F, -8.6F, -8.05F, 3.9F + k * 1.9F, 5.8F + k * 1.9F, 1.0F, 16, k * 11.25F, k == 0 ? straw : weave, b);
            }

            Art.ring(m, vc, 0.0F, 0.0F, -8.4F, -7.85F, 7.4F, 7.95F, 1.0F, 16, 0.0F, straw, b);
            Art.frustum(m, vc, 0.0F, 0.0F, -8.5F, -11.6F, 4.2F, 4.2F, 3.9F, 3.9F, 12, 0.0F, straw, b, false, false);
            Art.sphere(m, vc, 0.0F, -11.55F, 0.0F, 3.9F, 0.8F, 3.9F, 2, 12, weave, b);
            Art.ring(m, vc, 0.0F, 0.0F, -9.9F, -8.6F, 4.05F, 4.4F, 1.0F, 12, 0.0F, 0xFFD9475A, b);
            m.pushPose();
            m.translate(4.35F, -9.6F, 1.2F);
            Art.sprig(m, vc, 0.0F, 0.6F, 0.6F, 0.6F, -1.4F, -1.8F, 1.0F, v.tint(Art.SAKURA), b);
            m.popPose();
            break;
         }
         case "Piratenhut": {
            int felt = v.tint(0xFF2E2A33);
            int gold = 0xFFDCB66E;
            Art.frustum(m, vc, 0.0F, 0.0F, -8.2F, -11.2F, 4.3F, 4.3F, 4.0F, 4.0F, 10, 0.0F, felt, b, false, true);
            for (int i = 0; i < 3; i++) {
               m.pushPose();
               m.rotate(Axis.YP.rotationDegrees(i * 120.0F));
               m.translate(0.0F, -8.2F, -4.6F);
               m.rotate(Axis.XP.rotationDegrees(-62.0F));
               Art.bevel(m, vc, -5.2F, -3.6F, -0.3F, 5.2F, 0.0F, 0.3F, 0.15F, felt, b);
               Art.bevel(m, vc, -5.3F, -3.85F, -0.38F, 5.3F, -3.45F, 0.38F, 0.1F, gold, b);
               m.popPose();
            }

            // Totenkopf-Abzeichen vorne
            Art.sphere(m, vc, 0.0F, -10.2F, -6.55F, 0.75F, 0.7F, 0.4F, 2, 6, Art.IVORY, b);
            for (int k = -1; k <= 1; k += 2) {
               Art.limb(m, vc, -1.1F, -9.6F + k * 0.45F, -6.45F, 1.1F, -9.6F - k * 0.45F, -6.45F, 0.15F, 0.15F, 4, Art.IVORY, b);
            }
            break;
         }
         case "Zaubererhut": {
            int blue = v.tint(0xFF3A4C9E);
            Art.ring(m, vc, 0.0F, 0.0F, -8.6F, -8.05F, 3.9F, 7.2F, 1.0F, 14, 0.0F, blue, b);
            float px = 0.0F, py = -8.5F, pr = 4.1F;
            for (int i = 0; i < 6; i++) {
               float q = (i + 1) / 6.0F;
               float nr = i == 5 ? 0.0F : 4.1F * (1.0F - q * 0.9F);
               float nx = q * q * 1.6F, ny = -8.5F - q * 12.5F;
               Art.taper(m, vc, px, py, 0.0F, pr, nx, ny, 0.0F, nr, 10, i * 7.0F, i % 2 == 0 ? blue : Art.bright(blue, 0.92F), b, false, false);
               px = nx;
               py = ny;
               pr = nr;
            }

            Art.ring(m, vc, 0.0F, 0.0F, -9.6F, -8.6F, 3.95F, 4.3F, 1.0F, 10, 0.0F, Art.GOLD, b);
            float[][] stars = {{-2.9F, -12.0F, -1.6F}, {1.6F, -14.4F, -1.8F}, {-0.9F, -17.0F, 1.2F}, {2.2F, -11.2F, 2.6F}};
            for (float[] s : stars) {
               Art.gem(m, vc, s[0], s[1], s[2], 0.5F, 0.55F, 0.55F, 4, 45.0F, Art.GOLD, b);
            }
            break;
         }
         case "Einhorn": {
            int[] cols = {0xFFFDF6F2, 0xFFF9D9E4, 0xFFEFD8F4, 0xFFFDF6F2, 0xFFF9D9E4, 0xFFEFD8F4};
            for (int i = 0; i < 6; i++) {
               float q0 = i / 6.0F, q1 = (i + 1) / 6.0F;
               float r0 = 1.25F * (1.0F - q0 * 0.85F), r1 = i == 5 ? 0.0F : 1.25F * (1.0F - q1 * 0.85F);
               Art.taper(m, vc, 0.0F, -8.0F - q0 * 7.2F, -2.5F - q0 * 1.5F, r0, 0.0F, -8.0F - q1 * 7.2F, -2.5F - q1 * 1.5F, r1, 5, i * 36.0F, cols[i], Math.max(b, 0.85F), false, false);
            }

            Art.ring(m, vc, 0.0F, 0.0F, -8.4F, -7.8F, 4.0F, 4.5F, 1.0F, 12, 0.0F, v.tint(0xFFF7AECB), b);
            Art.ring(m, vc, 0.0F, -2.5F, -8.6F, -8.0F, 1.0F, 1.6F, 1.0F, 8, 0.0F, Art.GOLD, b);
            for (int k = -1; k <= 1; k += 2) {
               m.pushPose();
               m.translate(k * 2.2F, -8.5F, -3.4F);
               m.rotate(Axis.XP.rotationDegrees(-60.0F));
               Art.sakura(m, vc, 0.0F, 0.0F, 0.0F, 1.1F, k * 20.0F, v.tint(Art.SAKURA), b);
               m.popPose();
            }
            break;
         }
         case "Geweih": {
            int bone = v.tint(0xFF9C7A5C);
            int tip = Art.bright(bone, 1.25F);
            for (int s = -1; s <= 1; s += 2) {
               Art.limb(m, vc, s * 2.6F, -8.0F, 0.0F, s * 4.0F, -12.0F, 0.2F, 0.7F, 0.55F, 5, bone, b);
               Art.limb(m, vc, s * 4.0F, -12.0F, 0.2F, s * 6.6F, -15.0F, 0.6F, 0.55F, 0.2F, 5, tip, b);
               Art.limb(m, vc, s * 4.0F, -12.0F, 0.2F, s * 3.3F, -16.2F, -0.2F, 0.45F, 0.15F, 5, tip, b);
               Art.limb(m, vc, s * 3.3F, -10.3F, 0.1F, s * 6.0F, -11.1F, -0.5F, 0.45F, 0.15F, 5, tip, b);
               Art.limb(m, vc, s * 5.3F, -13.6F, 0.45F, s * 7.5F, -13.1F, 0.2F, 0.35F, 0.12F, 5, tip, b);
               // kleine Blüten an den Geweihenden
               m.pushPose();
               m.translate(s * 4.6F, -13.3F, 0.4F);
               m.rotate(Axis.XP.rotationDegrees(-70.0F));
               Art.sakura(m, vc, 0.0F, 0.0F, 0.0F, 0.95F, s * 30.0F, v.tint(Art.SAKURA), b);
               m.popPose();
               Art.gem(m, vc, s * 6.1F, -11.4F, -0.5F, 0.3F, 0.4F, 0.15F, 5, 0.0F, v.tint(Art.ROSE), b);
            }
            break;
         }
         case "Sakura-Geist": {
            // Waldgeist-Hirsch: kantiges Elfenbein-Geweih, das in Kirschblüten-Wolken ausläuft
            int bone = v.tint(Art.IVORY);
            Art.bevel(m, vc, -3.4F, -8.75F, -3.0F, 3.4F, -7.95F, 3.0F, 0.25F, Art.WHITE, b);
            Art.bevel(m, vc, -2.6F, -8.4F, -4.55F, 2.6F, -6.9F, -3.95F, 0.2F, bone, b);
            Art.bevel(m, vc, -1.6F, -7.1F, -4.7F, 1.6F, -6.4F, -4.1F, 0.15F, Art.WHITE, b);
            Art.diamond(m, vc, 0.0F, -7.6F, -4.62F, 0.42F, 2, v.tint(Art.SAKURA), b);
            for (int k = -1; k <= 1; k += 2) {
               Art.bevel(m, vc, k * 2.2F - 0.9F, -9.6F, -0.3F, k * 2.2F + 0.9F, -8.5F, 1.5F, 0.2F, bone, b);
               Art.diamond(m, vc, k * 3.42F, -8.35F, 0.0F, 0.25F, 0, v.tint(Art.SAKURA), b);
               m.pushPose();
               m.translate(k * 2.2F, -9.4F, 0.6F);
               Art.antler(m, vc, k, 1.15F, bone, b);
               m.popPose();
            }

            Art.drift(m, vc, 0.0F, -17.0F, 0.0F, 7.0F, 17.0F, 9, t, 0.9F, b);
            break;
         }
         case "Kürbiskopf": {
            int orange = v.tint(0xFFE58A3A);
            int groove = Art.bright(orange, 0.8F);
            Art.bevel(m, vc, -4.6F, -8.6F, -4.6F, 4.6F, 0.4F, 4.6F, 0.9F, orange, b);
            for (int k = -1; k <= 1; k++) {
               if (k != 0) {
                  Art.box(m, vc, k * 1.9F - 0.18F, -8.2F, -4.65F, k * 1.9F + 0.18F, 0.0F, 4.65F, groove, b);
                  Art.box(m, vc, -4.65F, -8.2F, k * 1.9F - 0.18F, 4.65F, 0.0F, k * 1.9F + 0.18F, groove, b);
               }
            }

            // geschnitztes Gesicht (dunkel, das Leuchten kommt im Glow-Durchgang)
            int ink = Art.INK;
            Art.face(m, vc, ink, b, 0.0F, -4.0F, 0.0F, -3.2F, -4.0F, -4.66F, -1.0F, -4.0F, -4.66F, -2.1F, -6.0F, -4.66F);
            Art.face(m, vc, ink, b, 0.0F, -4.0F, 0.0F, 1.0F, -4.0F, -4.66F, 3.2F, -4.0F, -4.66F, 2.1F, -6.0F, -4.66F);
            Art.box(m, vc, -3.0F, -2.6F, -4.68F, 3.0F, -1.4F, -4.6F, ink, b);
            Art.limb(m, vc, 0.0F, -8.4F, 0.0F, 0.6F, -10.6F, 0.3F, 0.55F, 0.4F, 5, 0xFF5E6E3A, b);
            Art.petal(m, vc, 0.5F, -9.6F, 0.2F, 2.2F, 20.0F, -15.0F, 0xFF7FA35E, b);
            break;
         }
         default: {
            int felt = v.tint(0xFF2E2A33);
            Art.ring(m, vc, 0.0F, 0.0F, -8.55F, -8.0F, 3.9F, 6.3F, 0.92F, 14, 0.0F, felt, b);
            Art.ring(m, vc, 0.0F, 0.0F, -8.95F, -8.4F, 5.7F, 6.45F, 0.92F, 14, 0.0F, Art.bright(felt, 1.12F), b);
            Art.frustum(m, vc, 0.0F, 0.0F, -8.4F, -16.0F, 4.0F, 4.0F, 4.45F, 4.45F, 12, 15.0F, felt, b, false, true);
            Art.ring(m, vc, 0.0F, 0.0F, -16.25F, -15.75F, 4.15F, 4.6F, 1.0F, 12, 15.0F, Art.bright(felt, 1.15F), b);
            Art.ring(m, vc, 0.0F, 0.0F, -10.6F, -8.6F, 4.0F, 4.35F, 1.0F, 12, 15.0F, v.tint(0xFFE58FAB), b);
            Art.bevel(m, vc, -0.9F, -10.8F, -4.6F, 0.9F, -8.4F, -4.28F, 0.15F, Art.GOLD, b);
            Art.box(m, vc, -0.45F, -10.3F, -4.66F, 0.45F, -8.9F, -4.55F, felt, b);
            m.pushPose();
            m.translate(4.4F, -9.6F, -1.4F);
            m.rotate(Axis.ZP.rotationDegrees(90.0F));
            Art.sakura(m, vc, 0.0F, 0.0F, 0.0F, 1.6F, 0.0F, v.tint(Art.SAKURA), b);
            Art.petal(m, vc, 0.0F, 0.05F, 0.0F, 2.0F, 150.0F, 10.0F, Art.LEAF, b);
            Art.petal(m, vc, 0.0F, 0.05F, 0.0F, 1.7F, 210.0F, 10.0F, Art.LEAF, b);
            m.popPose();
         }
      }
   }

   private void glow(PoseStack m, VertexConsumer vc, String st, CosmeticValue v, float t) {
      switch (st) {
         case "Sakura-Geist":
            for (int k = -1; k <= 1; k += 2) {
               float tw = 0.5F + 0.5F * Mth.sin(t * 2.0F + k);
               Art.glowSpark(m, vc, k * 4.7F, -16.8F, 1.0F, 1.6F, v.tint(Art.SAKURA), 0.25F + 0.2F * tw);
               Art.glowSpark(m, vc, k * 6.2F, -15.4F, 1.9F, 1.2F, v.tint(Art.SAKURA), 0.2F + 0.2F * (1.0F - tw));
            }

            Art.glowSpark(m, vc, 0.0F, -7.6F, -4.75F, 0.5F, v.tint(Art.SAKURA), 0.5F + 0.3F * Mth.sin(t * 3.0F));
            break;
         case "Krone":
            for (int i = 0; i < 8; i++) {
               double c = Math.toRadians(i * 45.0);
               float gx = (float)Math.cos(c) * 5.05F, gz = (float)Math.sin(c) * 5.05F;
               float tw = 0.5F + 0.5F * Mth.sin(t * 3.0F + i * 1.6F);
               Shapes.glowCube(m, vc, gx, -9.1F, gz, 0.6F, i % 2 == 0 ? 0xFFFF7A9A : 0xFF8FB4FF, 0.18F + 0.35F * tw);
            }
            break;
         case "Hexenhut":
            Shapes.glow(m, vc, -1.4F, -10.6F, -4.75F, 1.4F, -8.2F, -4.2F, 0xFFFFE6A0, 0.3F + 0.25F * Mth.sin(t * 2.0F));
            break;
         case "Teufelshörner":
            for (int side = -1; side <= 1; side += 2) {
               Shapes.glowCube(m, vc, side * 2.3F, -14.0F, 0.2F, 0.55F, v.tint(0xFFFF5A3A), 0.5F + 0.3F * Mth.sin(t * 4.0F + side));
            }
            break;
         case "Astronautenhelm":
            Shapes.glow(m, vc, -4.7F, -8.4F, -5.4F, 4.7F, -1.4F, -5.2F, v.tint(0xFFA8D8FF), 0.22F);
            Shapes.glow(m, vc, -3.6F, -7.6F, -5.45F, -1.2F, -5.6F, -5.4F, 0xFFFFFFFF, 0.18F + 0.08F * Mth.sin(t * 1.5F));
            Shapes.glowCube(m, vc, 5.25F, -13.2F, 2.65F, 0.6F, 0xFFFF5A70, Mth.sin(t * 3.0F) > 0.0F ? 0.85F : 0.1F);
            break;
         case "Kopfhörer":
            for (int side = -1; side <= 1; side += 2) {
               float beat = Math.abs(Mth.sin(t * 4.2F)) * 0.5F + 0.2F;
               int col = Shapes.hsv(0.93F + v.hue() / 360.0F + Mth.sin(t * 0.5F) * 0.05F, 0.45F, 1.0F);
               Art.glowRing(m, vc, side * 6.65F, -3.6F, 0.0F, 2.3F, col, beat);
            }
            break;
         case "Kürbiskopf": {
            float fl = 0.6F + 0.4F * Mth.sin(t * 9.0F) * Mth.sin(t * 5.3F);
            int fire = 0xFFFFB040;
            Shapes.glow(m, vc, -3.2F, -6.0F, -4.8F, -1.0F, -4.0F, -4.7F, fire, 0.85F * fl);
            Shapes.glow(m, vc, 1.0F, -6.0F, -4.8F, 3.2F, -4.0F, -4.7F, fire, 0.85F * fl);
            Shapes.glow(m, vc, -3.0F, -2.6F, -4.8F, 3.0F, -1.4F, -4.7F, fire, 0.85F * fl);
            break;
         }
         case "Zaubererhut":
            for (int i = 0; i < 7; i++) {
               float tw = Mth.sin(t * (2.0F + i * 0.4F) + i * 1.9F);
               float q = (i + 0.7F) / 8.0F;
               float y = -8.5F - q * 12.5F;
               float r = 4.1F * (1.0F - q * 0.9F) + 0.3F;
               m.pushPose();
               m.translate(q * q * 1.6F, 0.0F, 0.0F);
               m.rotate(Axis.YP.rotationDegrees(i * 77.0F));
               Shapes.glowCube(m, vc, 0.0F, y, -r, 0.3F, 0xFFFFF0B0, tw > 0.0F ? tw : 0.0F);
               m.popPose();
            }
            break;
         case "Einhorn":
            Shapes.glow(m, vc, -0.7F, -14.8F, -3.9F, 0.7F, -8.2F, -2.4F, Shapes.hsv(0.9F + Mth.sin(t * 0.5F) * 0.08F, 0.35F, 1.0F), 0.14F + 0.08F * Mth.sin(t * 3.0F));
            break;
         case "Partyhut":
            m.pushPose();
            m.rotate(Axis.ZP.rotationDegrees(8.0F));
            Shapes.glowBall(m, vc, 0.0F, -18.3F, 0.0F, 1.6F, 0xFFFFF0B0, 0.25F + 0.15F * Mth.sin(t * 5.0F));
            m.popPose();
            break;
         case "Blumenkranz":
            for (int i = 0; i < 9; i++) {
               double a = Math.PI * 2.0 * i / 9.0 + 0.2;
               float tw = 0.5F + 0.5F * Mth.sin(t * 2.0F + i * 1.3F);
               Shapes.glowCube(m, vc, (float)Math.cos(a) * 4.9F, -9.2F, (float)Math.sin(a) * 4.9F, 0.45F, 0xFFFFD6E2, 0.12F * tw);
            }
            break;
         default:
      }
   }
}
