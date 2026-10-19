package com.lennart.nebula.cosmetic.mods;

import com.lennart.nebula.cosmetic.Cosmetic;
import com.lennart.nebula.cosmetic.CosmeticContext;
import com.lennart.nebula.cosmetic.CosmeticValue;
import com.lennart.nebula.cosmetic.Shapes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/** Auren: leuchtender Kreis am Boden, aufsteigende Lichter und passende Effekte rund um den Spieler. */
public class Aura extends Cosmetic {
   public static final String[] STYLES = {"Feuer", "Frost", "Blitz", "Sakura", "Galaxie", "Herzen", "Musik", "Schatten", "Gift", "Gold", "Wind", "Regenbogen"};

   public Aura() {
      super("Auren", "Aura um deinen Charakter: Feuer, Frost, Blitz, Sakura, Galaxie, Herzen, Musik, Schatten und mehr – mit Licht und Partikeln.", "aura", Items.BLAZE_POWDER, STYLES);
      this.groups();
   }

   private static int base(String st) {
      return switch (st) {
         case "Frost" -> 0xFF8FE0FF;
         case "Blitz" -> 0xFF9AD0FF;
         case "Sakura" -> 0xFFFF9AC8;
         case "Galaxie" -> 0xFF9A60FF;
         case "Herzen" -> 0xFFFF4A7A;
         case "Musik" -> 0xFF60F0C0;
         case "Schatten" -> 0xFF8A40D0;
         case "Gift" -> 0xFF70F040;
         case "Gold" -> 0xFFFFD040;
         case "Wind" -> 0xFFE8F4FF;
         case "Regenbogen" -> 0xFFFF6060;
         default -> 0xFFFF7A20;
      };
   }

   private static int col(String st, CosmeticValue v, float t, float i) {
      return st.equals("Regenbogen") ? Shapes.hsv(t * 0.15F + i + v.hue() / 360.0F, 0.85F, 1.0F) : v.tint(base(st));
   }

   @Override
   public void render(CosmeticContext c, CosmeticValue v) {
      float t = c.time;
      float b = c.bright;
      String st = v.style();
      float s = v.size();
      c.glow((m, vc) -> {
         m.translate(0.0F, 24.0F, 0.0F);
         m.scale(s, s, s);
         this.glow(m, vc, st, v, t);
      });
      c.solid((m, vc) -> {
         m.translate(0.0F, 24.0F, 0.0F);
         m.scale(s, s, s);
         this.solid(m, vc, st, v, t, b);
      });
   }

   /** Im Raum mit Ursprung am Boden (y nach unten, also Höhe = negatives y). */
   private void glow(PoseStack m, VertexConsumer vc, String st, CosmeticValue v, float t) {
      float pulse = 0.75F + 0.25F * Mth.sin(t * 2.2F);
      // Bodenkreis mit Runen
      Shapes.ring(m, vc, -0.15F, 9.0F, 0.7F, 0.25F, 32, t * 18.0F, col(st, v, t, 0.0F), 1.0F, true, 0.5F * pulse);
      Shapes.ring(m, vc, -0.15F, 6.5F, 0.4F, 0.25F, 24, -t * 26.0F, col(st, v, t, 0.3F), 1.0F, true, 0.35F * pulse);

      for (int i = 0; i < 8; i++) {
         m.pushPose();
         m.mulPose(Axis.YP.rotationDegrees(t * 18.0F + i * 45.0F));
         Shapes.glow(m, vc, 7.2F, -0.3F, -0.5F, 8.3F, -0.05F, 0.5F, col(st, v, t, i / 8.0F), 0.6F * pulse);
         m.popPose();
      }

      // aufsteigende Lichter
      int n = 14;

      for (int i = 0; i < n; i++) {
         float ph = frac(t * 0.32F + i / (float)n + (i * 0.37F) % 0.2F);
         float ang = i * 2.4F + t * 0.9F;
         float r = 8.0F - ph * 2.5F;
         float y = -ph * 32.0F;
         float a = Mth.sin(ph * (float)Math.PI);
         float sz = st.equals("Blitz") ? 0.3F : 0.45F;
         Shapes.glowCube(m, vc, Mth.cos(ang) * r, y, Mth.sin(ang) * r, sz + a * 0.25F, col(st, v, t, ph), 0.75F * a);
      }

      switch (st) {
         case "Feuer":
            for (int i = 0; i < 16; i++) {
               m.pushPose();
               m.mulPose(Axis.YP.rotationDegrees(i * 22.5F));
               float fl = 0.5F + 0.5F * Mth.sin(t * 11.0F + i * 2.3F) * Mth.sin(t * 7.0F + i);
               float h = 2.0F + fl * 5.0F;
               Shapes.glow(m, vc, 7.6F, -h, -0.6F, 8.6F, 0.0F, 0.6F, v.tint(0xFFFF6A10), 0.45F);
               Shapes.glow(m, vc, 7.85F, -h * 0.6F, -0.35F, 8.35F, 0.0F, 0.35F, v.tint(0xFFFFD040), 0.6F);
               m.popPose();
            }
            break;
         case "Blitz": {
            Random r = new Random((long)(t * 12.0F));

            for (int k = 0; k < 3; k++) {
               float a0 = r.nextFloat() * 6.283F;
               float px = Mth.cos(a0) * 8.0F;
               float pz = Mth.sin(a0) * 8.0F;
               float py = 0.0F;

               for (int j = 0; j < 6; j++) {
                  float nx = px * 0.85F + (r.nextFloat() - 0.5F) * 3.0F;
                  float nz = pz * 0.85F + (r.nextFloat() - 0.5F) * 3.0F;
                  float ny = py - 4.0F - r.nextFloat() * 2.0F;
                  m.pushPose();
                  m.translate(0.0F, 0.0F, (pz + nz) / 2.0F);
                  Shapes.bar(m, vc, px, py, nx, ny, 0.0F, 0.35F, 0.35F, 0xFFE8F6FF, 1.0F, true, 0.9F);
                  Shapes.bar(m, vc, px, py, nx, ny, 0.0F, 1.2F, 1.2F, v.tint(0xFF6AB8FF), 1.0F, true, 0.25F);
                  m.popPose();
                  px = nx;
                  py = ny;
                  pz = nz;
               }
            }
            break;
         }
         case "Galaxie":
            for (int i = 0; i < 20; i++) {
               float ang = i * 1.7F + t * (0.3F + i % 3 * 0.15F);
               float r = 6.0F + i % 5 * 1.4F;
               float y = -4.0F - i % 7 * 3.5F;
               float tw = Mth.sin(t * (2.0F + i % 4) + i);
               Shapes.glowCube(m, vc, Mth.cos(ang) * r, y, Mth.sin(ang) * r, 0.3F, 0xFFFFFFFF, tw > 0.0F ? tw : 0.0F);
            }
            break;
         case "Wind":
            for (int i = 0; i < 4; i++) {
               float y0 = -4.0F - i * 6.0F;

               for (int j = 0; j < 10; j++) {
                  float ang = t * (2.5F + i * 0.4F) + i * 1.6F + j * 0.18F;
                  float r = 9.0F - i;
                  Shapes.glowCube(m, vc, Mth.cos(ang) * r, y0 - j * 0.15F, Mth.sin(ang) * r, 0.35F, v.tint(0xFFE8F4FF), 0.08F * j);
               }
            }
            break;
         case "Schatten":
            Shapes.ring(m, vc, -0.4F, 9.5F, 3.0F, 0.6F, 24, t * 9.0F, v.tint(0xFF6020A0), 1.0F, true, 0.25F);
            break;
         default:
      }
   }

   private void solid(PoseStack m, VertexConsumer vc, String st, CosmeticValue v, float t, float b) {
      switch (st) {
         case "Frost":
            for (int i = 0; i < 10; i++) {
               m.pushPose();
               m.mulPose(Axis.YP.rotationDegrees(i * 36.0F + 10.0F));
               float h = 2.5F + (i * 7 % 5) * 0.8F;
               Shapes.box(m, vc, 8.6F, -h, -0.45F, 9.4F, 0.0F, 0.45F, v.tint(0xFFB8ECFF), Math.max(b, 0.8F));
               Shapes.box(m, vc, 8.8F, -h - 1.0F, -0.25F, 9.2F, -h, 0.25F, 0xFFFFFFFF, Math.max(b, 0.8F));
               m.popPose();
            }
            break;
         case "Sakura":
            for (int i = 0; i < 12; i++) {
               float ph = frac(t * 0.18F + i / 12.0F);
               float ang = i * 2.1F + t * 0.6F + ph * 3.0F;
               float r = 6.0F + i % 4;
               m.pushPose();
               m.translate(Mth.cos(ang) * r, -30.0F + ph * 30.0F, Mth.sin(ang) * r);
               m.mulPose(Axis.YP.rotationDegrees(t * 90.0F + i * 40.0F));
               m.mulPose(Axis.XP.rotationDegrees(t * 70.0F + i * 25.0F));
               Shapes.box(m, vc, -0.7F, -0.1F, -0.45F, 0.7F, 0.1F, 0.45F, v.tint(i % 3 == 0 ? 0xFFFFFFFF : 0xFFFFA8CC), Math.max(b, 0.7F));
               m.popPose();
            }
            break;
         case "Herzen":
            for (int i = 0; i < 5; i++) {
               float ph = frac(t * 0.25F + i / 5.0F);
               float ang = i * 1.3F + t * 0.5F;
               float y = -ph * 30.0F;
               float sz = 0.6F + Mth.sin(ph * (float)Math.PI) * 0.5F;
               m.pushPose();
               m.translate(Mth.cos(ang) * 7.0F, y, Mth.sin(ang) * 7.0F);
               m.scale(sz, sz, sz);
               heart(m, vc, v.tint(0xFFFF4A7A), Math.max(b, 0.8F));
               m.popPose();
            }
            break;
         case "Musik":
            for (int i = 0; i < 5; i++) {
               float ph = frac(t * 0.22F + i / 5.0F);
               float ang = i * 1.3F + t * 0.4F;
               m.pushPose();
               m.translate(Mth.cos(ang) * 7.5F, -ph * 30.0F, Mth.sin(ang) * 7.5F);
               m.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 3.0F + i) * 15.0F));
               int nc = Shapes.hsv(i / 5.0F + v.hue() / 360.0F, 0.6F, 1.0F);
               Shapes.box(m, vc, -1.2F, -0.9F, -0.3F, 0.4F, 0.6F, 0.3F, nc, Math.max(b, 0.8F));
               Shapes.box(m, vc, 0.0F, -4.5F, -0.2F, 0.45F, -0.4F, 0.2F, nc, Math.max(b, 0.8F));
               Shapes.box(m, vc, 0.0F, -4.5F, -0.2F, 1.8F, -3.8F, 0.2F, nc, Math.max(b, 0.8F));
               m.popPose();
            }
            break;
         case "Gift":
            for (int i = 0; i < 9; i++) {
               float ph = frac(t * 0.3F + i / 9.0F);
               float ang = i * 2.0F;
               float r = 0.4F + Mth.sin(ph * (float)Math.PI) * 0.7F;
               Shapes.ball(m, vc, Mth.cos(ang) * 7.5F, -ph * 18.0F, Mth.sin(ang) * 7.5F, r, v.tint(0xFF60D030), Math.max(b, 0.7F));
            }
            break;
         case "Gold":
            for (int i = 0; i < 6; i++) {
               float ang = t * 1.2F + i * 1.047F;
               m.pushPose();
               m.translate(Mth.cos(ang) * 9.0F, -2.0F - Mth.sin(t * 2.0F + i) * 1.5F, Mth.sin(ang) * 9.0F);
               m.mulPose(Axis.YP.rotationDegrees(t * 120.0F + i * 30.0F));
               Shapes.box(m, vc, -1.0F, -1.0F, -0.2F, 1.0F, 1.0F, 0.2F, v.tint(0xFFF0C030), Math.max(b, 0.85F));
               m.popPose();
            }
            break;
         default:
      }
   }

   private static void heart(PoseStack m, VertexConsumer vc, int c, float b) {
      Shapes.box(m, vc, -1.6F, -1.8F, -0.4F, -0.1F, -0.4F, 0.4F, c, b);
      Shapes.box(m, vc, 0.1F, -1.8F, -0.4F, 1.6F, -0.4F, 0.4F, c, b);
      Shapes.box(m, vc, -1.1F, -0.5F, -0.4F, 1.1F, 0.6F, 0.4F, c, b);
      Shapes.box(m, vc, -0.5F, 0.5F, -0.4F, 0.5F, 1.4F, 0.4F, c, b);
   }

   private static float frac(float x) {
      return x - (float)Math.floor(x);
   }

   @Override
   public void particles(ClientLevel world, Player p, CosmeticValue v, Random r) {
      if (r.nextFloat() < 0.35F) {
         String st = v.style();
         double a = r.nextDouble() * Math.PI * 2.0;
         double rad = 0.45 + r.nextDouble() * 0.2;
         double x = p.getX() + Math.cos(a) * rad;
         double z = p.getZ() + Math.sin(a) * rad;
         double y = p.getY() + r.nextDouble() * 1.6;
         ParticleOptions e = switch (st) {
            case "Feuer" -> r.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME;
            case "Frost" -> ParticleTypes.SNOWFLAKE;
            case "Blitz" -> ParticleTypes.ELECTRIC_SPARK;
            case "Sakura" -> ParticleTypes.CHERRY_LEAVES;
            case "Galaxie" -> ParticleTypes.END_ROD;
            case "Herzen" -> ParticleTypes.HEART;
            case "Musik" -> ParticleTypes.NOTE;
            case "Schatten" -> ParticleTypes.SQUID_INK;
            case "Gift" -> ParticleTypes.ITEM_SLIME;
            case "Gold" -> ParticleTypes.WAX_ON;
            case "Wind" -> ParticleTypes.CLOUD;
            default -> new DustParticleOptions(Shapes.hsv(r.nextFloat(), 0.8F, 1.0F) & 16777215, 0.9F);
         };
         if (st.equals("Herzen") || st.equals("Musik")) {
            if (r.nextFloat() > 0.15F) {
               return;
            }

            y = p.getY() + 2.0 + r.nextDouble() * 0.3;
         }

         world.addParticle(e, x, y, z, 0.0, st.equals("Feuer") || st.equals("Wind") ? 0.02 : 0.0, 0.0);
      }
   }
}
