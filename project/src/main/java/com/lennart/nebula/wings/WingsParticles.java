package com.lennart.nebula.wings;

import com.lennart.nebula.module.mods.Wings;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;

/** Partikel an den Flügeln (nur clientseitig): je Stil eigene Partikel, mehr beim Schlagen und Fliegen. */
public final class WingsParticles {
   private static final Random R = new Random();
   private static int ticks;

   private WingsParticles() {
   }

   public static void tick(Minecraft mc) {
      ClientLevel world = mc.level;
      if (world != null && mc.player != null && !mc.isPaused()) {
         if (++ticks % 200 == 0) {
            WingsAnim.cleanup();
         }

         for (AbstractClientPlayer p : world.players()) {
            if (!p.isInvisible() && !(p.distanceToSqr(mc.player) > 2304.0)) {
               boolean own = p == mc.player;
               float rate = Wings.particleRate(own);
               if (!(rate <= 0.0F)) {
                  WingsModel.Spec spec = Wings.specFor(p);
                  if (spec != null) {
                     WingsAnim a = WingsAnim.peek(p.getId());
                     spawn(world, p, spec, a, rate * (0.35F + 0.65F * spec.fx()));
                  }
               }
            }
         }
      }
   }

   private static void spawn(ClientLevel world, AbstractClientPlayer p, WingsModel.Spec spec, WingsAnim a, float rate) {
      float flap = a == null ? 0.0F : a.flap;
      float open = a == null ? 0.15F : a.open;
      float phase = a == null ? 0.0F : a.phase;
      float count = rate * (0.22F + 0.6F * flap);
      float yaw = (float)Math.toRadians(p.yBodyRot);
      double fx = -Mth.sin(yaw);
      double fz = Mth.cos(yaw);
      double rx = -Mth.cos(yaw);
      double rz = -Mth.sin(yaw);
      float scale = spec.scale();
      float back = (float)Math.toRadians(35.0F - open * 28.0F);
      double shoulder = p.isCrouching() ? 1.05 : 1.35;

      while (R.nextFloat() < count) {
         count -= 1.0F;
         int side = R.nextBoolean() ? 1 : -1;
         float t = 0.3F + R.nextFloat() * 0.7F;
         float reach = 1.6F * scale * t;
         double lateral = 0.1 + Math.cos(back) * reach;
         double behind = 0.18 + Math.sin(back) * reach;
         double up = shoulder + 0.35 * scale * t + Mth.sin(phase) * 0.25 * flap * t - R.nextFloat() * 0.55 * scale;
         double x = p.getX() + rx * lateral * side - fx * behind;
         double y = p.getY() + up;
         double z = p.getZ() + rz * lateral * side - fz * behind;
         emit(world, spec, x, y, z);
      }
   }

   private static int tint(WingsModel.Spec spec, int rgb) {
      return spec.hue() == 0.0F ? rgb : WingsModel.shiftHue(rgb, spec.hue()) & 16777215;
   }

   private static void emit(ClientLevel world, WingsModel.Spec spec, double x, double y, double z) {
      float r = R.nextFloat();
      double vx = (R.nextDouble() - 0.5) * 0.02;
      double vz = (R.nextDouble() - 0.5) * 0.02;
      ParticleOptions effect;
      double vy;
      switch (spec.style()) {
         case WingsModel.PHOENIX:
            effect = r < 0.2F ? ParticleTypes.SMOKE : ParticleTypes.FLAME;
            vy = 0.02;
            break;
         case WingsModel.FROST:
            effect = r < 0.5F ? ParticleTypes.SNOWFLAKE : dust(spec, 8380415, 0.7F);
            vy = -0.02;
            break;
         case WingsModel.NEON:
            effect = r < 0.15F ? ParticleTypes.ELECTRIC_SPARK : dust(spec, r < 0.55F ? 2158847 : 16724176, 0.6F);
            vy = 0.0;
            break;
         case WingsModel.BLACK:
            effect = r < 0.3F ? ParticleTypes.SMOKE : dust(spec, 1315860, 0.9F);
            vy = -0.01;
            break;
         case WingsModel.GALAXY:
            effect = r < 0.5F ? ParticleTypes.END_ROD : dust(spec, WingsModel.hsv(0.7F + R.nextFloat() * 0.15F, 0.7F, 1.0F) & 16777215, 0.8F);
            vy = r < 0.5F ? -0.005 : 0.0;
            break;
         case WingsModel.CRYSTAL:
            effect = dust(spec, r < 0.5F ? 14708991 : 16756976, 0.7F);
            vy = -0.01;
            break;
         case WingsModel.EARTH:
            effect = dust(spec, r < 0.5F ? 4259696 : 6965806, 0.8F);
            vy = -0.03;
            break;
         case WingsModel.DEMON:
         case WingsModel.DEMON_BLACK:
         case WingsModel.HELL_LORD:
         case WingsModel.DEVIL:
            effect = r < 0.03F ? ParticleTypes.LAVA : (r < 0.45F ? ParticleTypes.SMOKE : ParticleTypes.ASH);
            vy = 0.01;
            break;
         case WingsModel.DRAGON:
         case WingsModel.DRAGON_BLACK:
         case WingsModel.BABY_DRAGON:
            effect = r < 0.15F ? ParticleTypes.FLAME : ParticleTypes.SMOKE;
            vy = 0.01;
            break;
         case WingsModel.WEB:
            effect = dust(spec, 15790324, 0.5F);
            vy = -0.01;
            break;
         case WingsModel.BUTTERFLY:
            effect = dust(spec, r < 0.5F ? 3778815 : 16777215, 0.7F);
            vy = 0.01;
            break;
         case WingsModel.DRAGONFLY:
            effect = r < 0.2F ? ParticleTypes.ELECTRIC_SPARK : dust(spec, r < 0.6F ? 4251903 : 11563263, 0.5F);
            vy = 0.0;
            break;
         default:
            effect = ParticleTypes.END_ROD;
            vy = -0.008;
            vx *= 0.3;
            vz *= 0.3;
      }

      world.addParticle(effect, x, y, z, vx, vy, vz);
   }

   private static ParticleOptions dust(WingsModel.Spec spec, int rgb, float size) {
      return new DustParticleOptions(tint(spec, rgb), size);
   }
}
