package com.lennart.nebula.cosmetic.mods;

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
      "Zaubererhut", "Einhorn", "Geweih", "Kürbiskopf"
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
            int gold = v.tint(0xFFE8B830);
            Shapes.box(m, vc, -4.6F, -10.0F, -4.6F, 4.6F, -8.2F, -4.0F, gold, b);
            Shapes.box(m, vc, -4.6F, -10.0F, 4.0F, 4.6F, -8.2F, 4.6F, gold, b);
            Shapes.box(m, vc, -4.6F, -10.0F, -4.0F, -4.0F, -8.2F, 4.0F, gold, b);
            Shapes.box(m, vc, 4.0F, -10.0F, -4.0F, 4.6F, -8.2F, 4.0F, gold, b);

            for (int side = 0; side < 4; side++) {
               m.pushPose();
               m.rotate(Axis.YP.rotationDegrees(side * 90.0F));

               for (int k = -1; k <= 1; k++) {
                  float h = k == 0 ? 3.2F : 2.2F;
                  Shapes.box(m, vc, k * 3.0F - 0.6F, -10.0F - h, -4.6F, k * 3.0F + 0.6F, -10.0F, -4.0F, gold, b);
               }

               Shapes.box(m, vc, -0.7F, -9.6F, -4.9F, 0.7F, -8.6F, -4.4F, side % 2 == 0 ? 0xFFD02040 : 0xFF2060E0, b);
               m.popPose();
            }
            break;
         }
         case "Partyhut": {
            m.pushPose();
            m.rotate(Axis.ZP.rotationDegrees(8.0F));

            for (int i = 0; i < 9; i++) {
               float q = i / 9.0F;
               float r = 3.4F * (1.0F - q * 0.9F);
               int col = v.tint(i % 2 == 0 ? 0xFFFF4FA0 : 0xFF40D0FF);
               Shapes.box(m, vc, -r, -8.0F - (i + 1) * 1.05F, -r, r, -8.0F - i * 1.05F, r, col, b);
            }

            Shapes.ball(m, vc, 0.0F, -18.0F, 0.0F, 1.0F, 0xFFFFF060, b);
            m.popPose();
            break;
         }
         case "Hexenhut": {
            int hat = v.tint(0xFF3A1A5A);
            Shapes.box(m, vc, -7.5F, -8.6F, -7.5F, 7.5F, -8.0F, 7.5F, hat, b);

            for (int i = 0; i < 8; i++) {
               float q = i / 8.0F;
               float r = 4.0F * (1.0F - q * 0.85F);
               float bend = q * q * 3.5F;
               Shapes.box(m, vc, -r, -8.6F - (i + 1) * 1.4F, -r + bend, r, -8.6F - i * 1.4F, r + bend, hat, b);
            }

            Shapes.box(m, vc, -4.1F, -10.0F, -4.1F, 4.1F, -9.0F, 4.1F, v.tint(0xFFFF8A1A), b);
            Shapes.box(m, vc, -1.0F, -10.2F, -4.4F, 1.0F, -8.8F, -4.1F, 0xFFE8C040, b);
            break;
         }
         case "Cowboyhut": {
            int brown = v.tint(0xFF8A5A2A);
            int dark = v.tint(0xFF5A3A1A);
            Shapes.box(m, vc, -5.5F, -8.6F, -7.0F, 5.5F, -8.0F, 7.0F, brown, b);
            Shapes.box(m, vc, -7.5F, -10.0F, -6.0F, -5.5F, -8.0F, 6.0F, brown, b);
            Shapes.box(m, vc, 5.5F, -10.0F, -6.0F, 7.5F, -8.0F, 6.0F, brown, b);
            Shapes.box(m, vc, -3.8F, -13.0F, -4.2F, 3.8F, -8.6F, 4.2F, brown, b);
            Shapes.box(m, vc, -1.0F, -13.2F, -3.6F, 1.0F, -12.4F, 3.6F, dark, b);
            Shapes.box(m, vc, -3.9F, -10.0F, -4.3F, 3.9F, -9.0F, 4.3F, dark, b);
            break;
         }
         case "Wikingerhelm": {
            int steel = v.tint(0xFF9AA0AA);
            Shapes.box(m, vc, -4.6F, -10.5F, -4.6F, 4.6F, -7.0F, 4.6F, steel, b);
            Shapes.box(m, vc, -3.6F, -11.6F, -3.6F, 3.6F, -10.5F, 3.6F, steel, b);
            Shapes.box(m, vc, -4.7F, -8.0F, -4.7F, 4.7F, -7.0F, 4.7F, 0xFF7A5A30, b);
            Shapes.box(m, vc, -0.6F, -7.0F, -4.8F, 0.6F, -3.5F, -4.4F, steel, b);

            for (int side = -1; side <= 1; side += 2) {
               Shapes.bar(m, vc, side * 4.4F, -9.0F, side * 7.5F, -11.0F, 0.0F, 1.6F, 1.6F, 0xFFEDE4CC, b, false, 1.0F);
               Shapes.bar(m, vc, side * 7.3F, -10.8F, side * 8.2F, -14.5F, 0.0F, 1.1F, 1.1F, 0xFFEDE4CC, b, false, 1.0F);
               Shapes.bar(m, vc, side * 8.1F, -14.0F, side * 8.3F, -15.6F, 0.0F, 0.7F, 0.7F, 0xFF4A4038, b, false, 1.0F);
            }
            break;
         }
         case "Propellermütze": {
            int[] q = {0xFFE83030, 0xFF30A0E8, 0xFFF0D020, 0xFF30C050};

            for (int i = 0; i < 4; i++) {
               float x0 = i % 2 == 0 ? -4.3F : 0.0F;
               float z0 = i < 2 ? -4.3F : 0.0F;
               Shapes.box(m, vc, x0, -10.2F, z0, x0 + 4.3F, -7.8F, z0 + 4.3F, v.tint(q[i]), b);
            }

            Shapes.box(m, vc, -4.3F, -8.4F, -7.0F, 4.3F, -7.9F, -4.3F, v.tint(q[0]), b);
            Shapes.box(m, vc, -0.3F, -12.2F, -0.3F, 0.3F, -10.2F, 0.3F, 0xFF404040, b);
            m.pushPose();
            m.translate(0.0F, -12.4F, 0.0F);
            m.rotate(Axis.YP.rotationDegrees(t * 900.0F));
            Shapes.box(m, vc, -5.0F, -0.25F, -0.8F, 5.0F, 0.25F, 0.8F, 0xFFE8E8E8, b);
            Shapes.box(m, vc, -0.6F, -0.5F, -0.6F, 0.6F, 0.3F, 0.6F, 0xFFE83030, b);
            m.popPose();
            break;
         }
         case "Weihnachtsmütze": {
            int red = v.tint(0xFFD02020);
            Shapes.box(m, vc, -4.6F, -9.6F, -4.6F, 4.6F, -7.6F, 4.6F, 0xFFF4F4F4, b);

            for (int i = 0; i < 7; i++) {
               float q = i / 7.0F;
               float r = 4.0F * (1.0F - q * 0.8F);
               float droop = q * q * 6.0F;
               float y0 = -9.6F - (i + 1) * 1.3F + droop * 0.9F;
               Shapes.box(m, vc, -r + droop, y0, -r, r + droop, y0 + 1.4F, r, red, b);
            }

            Shapes.ball(m, vc, 6.0F, -12.5F, 0.0F, 1.4F, 0xFFFFFFFF, b);
            break;
         }
         case "Kochmütze": {
            Shapes.box(m, vc, -4.4F, -11.0F, -4.4F, 4.4F, -7.8F, 4.4F, 0xFFF8F8F8, b);
            Shapes.ball(m, vc, 0.0F, -14.0F, 0.0F, 5.0F, 0xFFFFFFFF, b);
            Shapes.ball(m, vc, -2.6F, -13.0F, 1.5F, 3.0F, 0xFFF4F4F4, b);
            Shapes.ball(m, vc, 2.6F, -13.0F, -1.5F, 3.0F, 0xFFF4F4F4, b);
            break;
         }
         case "Katzenohren":
         case "Hasenohren": {
            boolean bunny = st.equals("Hasenohren");
            int fur = v.tint(bunny ? 0xFFF0ECE8 : 0xFF2A2A30);
            int inner = v.tint(0xFFF4A0B8);

            for (int side = -1; side <= 1; side += 2) {
               float tw = Mth.sin(t * 1.3F + side * 1.7F);
               float twitch = tw > 0.0F ? (float)Math.pow(tw, 20.0) * 22.0F : 0.0F;
               m.pushPose();
               m.translate(side * 2.6F, -8.0F, 0.0F);
               m.rotate(Axis.ZP.rotationDegrees(side * (bunny ? 8.0F + Mth.sin(t * 1.7F + side) * 5.0F : 10.0F) + side * twitch * 0.4F));
               m.rotate(Axis.XP.rotationDegrees(-twitch));
               if (bunny) {
                  Shapes.box(m, vc, -1.2F, -9.0F, -0.6F, 1.2F, 0.0F, 0.6F, fur, b);
                  Shapes.box(m, vc, -0.7F, -8.2F, -0.75F, 0.7F, -0.6F, -0.55F, inner, b);
               } else {
                  for (int i = 0; i < 4; i++) {
                     float r = 1.8F * (1.0F - i * 0.22F);
                     Shapes.box(m, vc, -r, -(i + 1) * 1.1F, -0.5F, r, -i * 1.1F, 0.5F, fur, b);
                  }

                  Shapes.box(m, vc, -0.9F, -2.6F, -0.65F, 0.9F, -0.2F, -0.45F, inner, b);
               }

               m.popPose();
            }
            break;
         }
         case "Teufelshörner":
            for (int side = -1; side <= 1; side += 2) {
               int red = v.tint(0xFFB01818);
               Shapes.bar(m, vc, side * 2.8F, -7.8F, side * 3.6F, -10.6F, -1.0F, 1.5F, 1.5F, red, b, false, 1.0F);
               Shapes.bar(m, vc, side * 3.5F, -10.4F, side * 3.0F, -12.6F, -1.0F, 1.0F, 1.0F, red, b, false, 1.0F);
               Shapes.bar(m, vc, side * 3.05F, -12.4F, side * 2.4F, -13.6F, -1.0F, 0.6F, 0.6F, 0xFF2A0808, b, false, 1.0F);
            }
            break;
         case "Blumenkranz":
            for (int i = 0; i < 14; i++) {
               m.pushPose();
               m.rotate(Axis.YP.rotationDegrees(i * 360.0F / 14.0F));
               Shapes.box(m, vc, 4.2F, -8.8F, -0.9F, 5.0F, -7.8F, 0.9F, 0xFF3A8A2A, b);
               int[] petals = {0xFFFF7AB0, 0xFFFFE060, 0xFFB080FF, 0xFFFFFFFF};
               Shapes.cube(m, vc, 4.8F, -9.0F, 0.0F, 0.75F, v.tint(petals[i % 4]), b);
               Shapes.cube(m, vc, 5.2F, -9.1F, 0.0F, 0.35F, 0xFFFFC020, b);
               m.popPose();
            }
            break;
         case "Pilzhut": {
            int cap = v.tint(0xFFD02828);
            Shapes.box(m, vc, -6.5F, -10.0F, -6.5F, 6.5F, -8.0F, 6.5F, cap, b);
            Shapes.box(m, vc, -5.0F, -12.0F, -5.0F, 5.0F, -10.0F, 5.0F, cap, b);
            Shapes.box(m, vc, -3.0F, -13.0F, -3.0F, 3.0F, -12.0F, 3.0F, cap, b);
            float[][] dots = {{-4.0F, -10.2F, -4.0F}, {3.5F, -10.6F, -2.0F}, {-1.0F, -12.4F, 2.0F}, {2.0F, -12.2F, 2.6F}, {-5.2F, -9.2F, 2.5F}, {5.0F, -9.4F, 4.0F}};

            for (float[] d : dots) {
               Shapes.cube(m, vc, d[0], d[1], d[2], 0.8F, 0xFFF8F8F0, b);
            }
            break;
         }
         case "Astronautenhelm":
            Shapes.box(m, vc, -5.2F, 0.0F, -5.2F, 5.2F, 1.4F, 5.2F, 0xFFE8E8EC, b);
            Shapes.box(m, vc, -5.4F, -0.6F, -5.4F, -4.6F, 1.2F, 5.4F, 0xFFC8C8D0, b);
            Shapes.box(m, vc, 4.6F, -0.6F, -5.4F, 5.4F, 1.2F, 5.4F, 0xFFC8C8D0, b);
            Shapes.box(m, vc, 5.4F, -5.0F, -1.0F, 6.2F, -2.0F, 1.0F, v.tint(0xFFE04040), b);
            break;
         case "Kopfhörer": {
            int band = v.tint(0xFF2A2A34);
            Shapes.box(m, vc, -5.0F, -9.4F, -1.0F, 5.0F, -8.6F, 1.0F, band, b);
            Shapes.box(m, vc, -5.0F, -8.6F, -1.0F, -4.2F, -4.5F, 1.0F, band, b);
            Shapes.box(m, vc, 4.2F, -8.6F, -1.0F, 5.0F, -4.5F, 1.0F, band, b);
            Shapes.box(m, vc, -6.0F, -5.5F, -2.0F, -4.0F, -1.5F, 2.0F, band, b);
            Shapes.box(m, vc, 4.0F, -5.5F, -2.0F, 6.0F, -1.5F, 2.0F, band, b);
            break;
         }
         case "Konoha-Stirnband": {
            int cloth = v.tint(0xFF1A2A5A);
            Shapes.box(m, vc, -4.35F, -6.6F, -4.35F, 4.35F, -5.0F, 4.35F, cloth, b);
            Shapes.box(m, vc, -3.0F, -6.8F, -4.6F, 3.0F, -4.8F, -4.35F, 0xFFC8CCD4, b);
            // Blatt-Symbol (Spirale) eingraviert
            Shapes.box(m, vc, -1.2F, -6.4F, -4.65F, 1.2F, -6.1F, -4.6F, 0xFF4A4E58, b);
            Shapes.box(m, vc, 0.9F, -6.4F, -4.65F, 1.2F, -5.2F, -4.6F, 0xFF4A4E58, b);
            Shapes.box(m, vc, -0.6F, -5.5F, -4.65F, 1.2F, -5.2F, -4.6F, 0xFF4A4E58, b);
            Shapes.box(m, vc, -0.6F, -6.0F, -4.65F, -0.3F, -5.2F, -4.6F, 0xFF4A4E58, b);
            float flow = Mth.sin(t * 3.0F) * 10.0F;

            for (int k = -1; k <= 1; k += 2) {
               m.pushPose();
               m.translate(k * 0.8F, -5.8F, 4.35F);
               m.rotate(Axis.XP.rotationDegrees(35.0F + flow + k * 6.0F));
               Shapes.box(m, vc, -0.5F, 0.0F, -0.15F, 0.5F, 5.0F, 0.15F, cloth, b);
               m.popPose();
            }
            break;
         }
         case "Strohhut": {
            int straw = v.tint(0xFFE8C870);
            Shapes.box(m, vc, -7.5F, -8.6F, -7.5F, 7.5F, -8.0F, 7.5F, straw, b);
            Shapes.box(m, vc, -4.2F, -12.0F, -4.2F, 4.2F, -8.6F, 4.2F, straw, b);
            Shapes.box(m, vc, -4.3F, -9.8F, -4.3F, 4.3F, -8.6F, 4.3F, 0xFFC81818, b);
            break;
         }
         case "Piratenhut": {
            int felt = v.tint(0xFF1A1A1E);
            Shapes.box(m, vc, -4.4F, -11.0F, -4.4F, 4.4F, -8.0F, 4.4F, felt, b);
            Shapes.box(m, vc, -7.0F, -10.5F, -1.0F, 7.0F, -7.8F, 1.0F, felt, b);
            Shapes.box(m, vc, -1.0F, -11.5F, -5.5F, 1.0F, -8.0F, -4.4F, felt, b);
            Shapes.box(m, vc, -7.05F, -10.6F, -1.05F, 7.05F, -10.2F, 1.05F, 0xFFE8C040, b);
            Shapes.box(m, vc, -1.0F, -10.4F, -5.6F, 1.0F, -9.0F, -5.5F, 0xFFF4F4F4, b);
            break;
         }
         case "Zaubererhut": {
            int blue = v.tint(0xFF2A3A9A);
            Shapes.box(m, vc, -7.0F, -8.6F, -7.0F, 7.0F, -8.0F, 7.0F, blue, b);
            Shapes.cone(m, vc, 0.0F, -8.6F, 0.0F, 4.0F, 12.0F, 10, blue, Shapes.mix(blue, 0xFF000000, 0.3F), b);
            break;
         }
         case "Einhorn":
            for (int i = 0; i < 6; i++) {
               float r = 1.1F * (1.0F - i * 0.15F);
               m.pushPose();
               m.translate(0.0F, -8.0F - i * 1.2F, -2.5F - i * 0.25F);
               m.rotate(Axis.YP.rotationDegrees(i * 30.0F));
               Shapes.box(m, vc, -r, -1.2F, -r, r, 0.0F, r, i % 2 == 0 ? 0xFFFFF0F8 : 0xFFF0D8F0, Math.max(b, 0.85F));
               m.popPose();
            }

            Shapes.box(m, vc, -4.4F, -8.4F, -4.4F, 4.4F, -7.8F, 4.4F, v.tint(0xFFFF9AD0), b);
            break;
         case "Geweih": {
            int bone = v.tint(0xFF8A6A40);

            for (int s = -1; s <= 1; s += 2) {
               Shapes.bar(m, vc, s * 2.6F, -8.0F, s * 4.0F, -12.0F, 0.0F, 0.8F, 0.8F, bone, b, false, 1.0F);
               Shapes.bar(m, vc, s * 4.0F, -12.0F, s * 6.5F, -15.0F, 0.0F, 0.7F, 0.7F, bone, b, false, 1.0F);
               Shapes.bar(m, vc, s * 4.0F, -12.0F, s * 3.4F, -16.0F, 0.0F, 0.6F, 0.6F, bone, b, false, 1.0F);
               Shapes.bar(m, vc, s * 3.4F, -10.4F, s * 6.0F, -11.0F, 0.0F, 0.6F, 0.6F, bone, b, false, 1.0F);
               Shapes.bar(m, vc, s * 5.4F, -13.6F, s * 7.4F, -13.0F, 0.0F, 0.5F, 0.5F, bone, b, false, 1.0F);
            }
            break;
         }
         case "Kürbiskopf": {
            int orange = v.tint(0xFFE07818);
            Shapes.box(m, vc, -4.6F, -8.6F, -4.6F, 4.6F, 0.4F, 4.6F, orange, b);

            for (int k = -2; k <= 2; k++) {
               Shapes.box(m, vc, k * 1.8F - 0.1F, -8.65F, -4.65F, k * 1.8F + 0.1F, 0.45F, 4.65F, Shapes.mix(orange, 0xFF000000, 0.25F), b);
            }

            Shapes.box(m, vc, -0.4F, -10.4F, -0.4F, 0.4F, -8.6F, 0.4F, 0xFF3A6A20, b);
            break;
         }
         default: {
            int black = v.tint(0xFF18181C);
            Shapes.box(m, vc, -6.0F, -8.6F, -6.0F, 6.0F, -8.0F, 6.0F, black, b);
            Shapes.box(m, vc, -4.2F, -16.0F, -4.2F, 4.2F, -8.6F, 4.2F, black, b);
            Shapes.box(m, vc, -4.3F, -10.0F, -4.3F, 4.3F, -9.0F, 4.3F, v.tint(0xFFB01828), b);
         }
      }
   }

   private void glow(PoseStack m, VertexConsumer vc, String st, CosmeticValue v, float t) {
      switch (st) {
         case "Krone":
            for (int side = 0; side < 4; side++) {
               m.pushPose();
               m.rotate(Axis.YP.rotationDegrees(side * 90.0F));
               float tw = 0.5F + 0.5F * Mth.sin(t * 3.0F + side * 1.6F);
               Shapes.glow(m, vc, -1.0F, -9.9F, -5.1F, 1.0F, -8.3F, -4.3F, side % 2 == 0 ? 0xFFFF4060 : 0xFF60A0FF, 0.25F + 0.4F * tw);
               m.popPose();
            }
            break;
         case "Hexenhut":
            Shapes.glow(m, vc, -1.3F, -10.4F, -4.6F, 1.3F, -8.6F, -4.0F, 0xFFFFE080, 0.4F + 0.3F * Mth.sin(t * 2.0F));
            break;
         case "Teufelshörner":
            for (int side = -1; side <= 1; side += 2) {
               Shapes.glowCube(m, vc, side * 2.4F, -13.6F, -1.0F, 0.6F, v.tint(0xFFFF4010), 0.5F + 0.3F * Mth.sin(t * 4.0F + side));
            }
            break;
         case "Astronautenhelm":
            Shapes.glow(m, vc, -5.3F, -9.3F, -5.3F, 5.3F, 0.0F, 5.3F, v.tint(0xFF9AD8FF), 0.14F);
            Shapes.glow(m, vc, -3.5F, -7.5F, -5.4F, 3.5F, -2.0F, -5.25F, 0xFFFFFFFF, 0.12F + 0.06F * Mth.sin(t * 1.5F));
            Shapes.glowCube(m, vc, 5.8F, -3.5F, 0.0F, 0.5F, 0xFFFF4040, Mth.sin(t * 3.0F) > 0.0F ? 0.8F : 0.1F);
            break;
         case "Kopfhörer":
            for (int side = -1; side <= 1; side += 2) {
               float beat = Math.abs(Mth.sin(t * 4.2F)) * 0.6F + 0.2F;
               int col = Shapes.hsv(v.hue() / 360.0F + t * 0.1F + side * 0.15F, 0.8F, 1.0F);
               Shapes.glow(m, vc, side * 6.1F - 0.3F, -5.2F, -1.6F, side * 6.1F + 0.3F, -1.8F, 1.6F, col, beat);
            }
            break;
         case "Kürbiskopf": {
            float fl = 0.6F + 0.4F * Mth.sin(t * 9.0F) * Mth.sin(t * 5.3F);
            int fire = 0xFFFFA020;
            Shapes.glow(m, vc, -3.2F, -6.0F, -4.75F, -1.0F, -4.0F, -4.6F, fire, 0.8F * fl);
            Shapes.glow(m, vc, 1.0F, -6.0F, -4.75F, 3.2F, -4.0F, -4.6F, fire, 0.8F * fl);
            Shapes.glow(m, vc, -3.0F, -2.6F, -4.75F, 3.0F, -1.4F, -4.6F, fire, 0.8F * fl);
            Shapes.glow(m, vc, -1.6F, -3.0F, -4.75F, -0.6F, -2.6F, -4.6F, fire, 0.8F * fl);
            Shapes.glow(m, vc, 0.6F, -3.0F, -4.75F, 1.6F, -2.6F, -4.6F, fire, 0.8F * fl);
            break;
         }
         case "Zaubererhut":
            for (int i = 0; i < 7; i++) {
               float tw = Mth.sin(t * (2.0F + i * 0.4F) + i * 1.9F);
               float y = -9.5F - i * 1.5F;
               float r = 3.8F * (1.0F - (i * 1.5F + 1.0F) / 12.0F) + 0.15F;
               m.pushPose();
               m.rotate(Axis.YP.rotationDegrees(i * 77.0F));
               Shapes.glowCube(m, vc, 0.0F, y, -r, 0.35F, 0xFFFFF0A0, tw > 0.0F ? tw : 0.0F);
               m.popPose();
            }
            break;
         case "Einhorn":
            Shapes.glow(m, vc, -0.9F, -15.2F, -4.3F, 0.9F, -8.0F, -2.2F, Shapes.hsv(t * 0.3F, 0.6F, 1.0F), 0.16F + 0.08F * Mth.sin(t * 3.0F));
            break;
         case "Partyhut":
            m.pushPose();
            m.rotate(Axis.ZP.rotationDegrees(8.0F));
            Shapes.glowBall(m, vc, 0.0F, -18.0F, 0.0F, 1.6F, 0xFFFFF060, 0.3F + 0.2F * Mth.sin(t * 5.0F));
            m.popPose();
            break;
         default:
      }
   }
}
