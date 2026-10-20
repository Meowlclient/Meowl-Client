package com.lennart.nebula.cosmetic.mods;

import com.lennart.nebula.cosmetic.Art;
import com.lennart.nebula.cosmetic.Cosmetic;
import com.lennart.nebula.cosmetic.CosmeticContext;
import com.lennart.nebula.cosmetic.CosmeticValue;
import com.lennart.nebula.cosmetic.Cosmetics;
import com.lennart.nebula.cosmetic.Shapes;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.util.WorldRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Random;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/** Effekte an den Händen (Armraum: Hand bei y = 8..10). */
public class Hands extends Cosmetic {
   public static final String[] STYLES = {"Feuerfäuste", "Eishände", "Blitzhände", "Rasengan", "Chidori", "Boxhandschuhe", "Krallen", "Zauberlicht", "Leuchtringe"};

   private static Hands instance;
   private final BooleanSetting firstPerson = this.add(
      new BooleanSetting("In Ego-Sicht", "Effekt auch in der Ich-Perspektive an der Haupthand zeigen", true)
   );

   public Hands() {
      super("Hände", "Effekte an den Händen: Feuerfäuste, Eishände, Blitze, Rasengan, Chidori, Boxhandschuhe, Krallen, Zauberlicht oder Leuchtringe.", "hands", Items.BLAZE_ROD, STYLES);
      instance = this;
      this.groups(this.firstPerson);
   }

   /**
    * Ego-Sicht: Effekt an der Haupthand. {@code arm} = Armteil, wenn die leere Hand gezeichnet wird (Matrix steht im
    * Modellraum des Arms); null = Hand hält etwas (Matrix steht am Gegenstand).
    */
   public static void renderFirstPerson(PoseStack matrices, SubmitNodeCollector queue, ModelPart arm, boolean right) {
      Hands h = instance;
      Minecraft mc = Minecraft.getInstance();
      if (h == null || mc.player == null || !h.firstPerson.get()) {
         return;
      }

      CosmeticValue v = Cosmetics.valueFor(mc.player, h);
      if (v == null) {
         return;
      }

      float t = Cosmetics.time();
      int side = right ? 0 : 1;
      String st = v.style();
      float hx = right ? -1.0F : 1.0F;
      matrices.pushPose();
      if (arm != null) {
         arm.translateAndRotate(matrices);
      }

      float s = v.size() * (arm != null ? 1.0F : 0.85F);
      queue.submitCustomGeometry(matrices, WorldRender.solidColor(), (entry, vc) -> {
         PoseStack m = local(entry);
         if (arm != null) {
            m.translate(hx, 9.0F, 0.0F);
         }

         m.scale(s, s, s);
         h.solid(m, vc, st, v, t, 1.0F, side);
      });
      queue.submitCustomGeometry(matrices, WorldRender.glow(), (entry, vc) -> {
         PoseStack m = local(entry);
         if (arm != null) {
            m.translate(hx, 9.0F, 0.0F);
         }

         m.scale(s, s, s);
         h.glow(m, vc, st, v, t, side);
      });
      matrices.popPose();
   }

   private static PoseStack local(PoseStack.Pose entry) {
      PoseStack local = new PoseStack();
      local.last().pose().set(entry.pose());
      local.last().normal().set(entry.normal());
      local.scale(0.0625F, 0.0625F, 0.0625F);
      return local;
   }

   private static boolean oneHand(String st) {
      return st.equals("Rasengan") || st.equals("Chidori") || st.equals("Zauberlicht");
   }

   @Override
   public void render(CosmeticContext c, CosmeticValue v) {
      float t = c.time;
      float b = c.bright;
      String st = v.style();

      for (int i = 0; i < 2; i++) {
         if (i == 1 && oneHand(st)) {
            break;
         }

         float hx = i == 0 ? -1.0F : 1.0F;
         int side = i;
         // über den Ärmeln: etwas dicker
         float wide = c.outer ? 1.14F : 1.0F;
         c.onPart(i == 0 ? c.model.rightArm : c.model.leftArm, () -> {
            c.solid((m, vc) -> {
               m.translate(hx, 9.0F, 0.0F);
               m.scale(v.size() * wide, v.size(), v.size() * wide);
               this.solid(m, vc, st, v, t, b, side);
            });
            c.glow((m, vc) -> {
               m.translate(hx, 9.0F, 0.0F);
               m.scale(v.size() * wide, v.size(), v.size() * wide);
               this.glow(m, vc, st, v, t, side);
            });
         });
      }
   }

   /** Ursprung = Mitte der Faust (Arm 4x4, nach unten = +y, vorn = -z). */
   private void solid(PoseStack m, VertexConsumer vc, String st, CosmeticValue v, float t, float b, int side) {
      switch (st) {
         case "Eishände": {
            // Eiskristalle wachsen schräg aus der Faust, dazu eine dünne Frostschicht
            int ice = v.tint(0xFFC4ECFA);
            Art.bevel(m, vc, -2.15F, -1.2F, -2.15F, 2.15F, 1.4F, 2.15F, 0.3F, Art.mix(ice, Art.WHITE, 0.4F), Math.max(b, 0.8F));
            for (int k = 0; k < 6; k++) {
               m.pushPose();
               m.mulPose(Axis.YP.rotationDegrees(k * 60.0F + side * 30.0F));
               m.mulPose(Axis.ZP.rotationDegrees(-35.0F - (k % 3) * 12.0F));
               float len = 2.4F + (k * 7 % 4) * 0.5F;
               Art.gem(m, vc, 0.0F, -2.5F, 0.0F, 0.5F, len, 0.6F, 6, k * 20.0F, k % 2 == 0 ? ice : Art.mix(ice, Art.WHITE, 0.3F), Math.max(b, 0.85F));
               m.popPose();
            }
            break;
         }
         case "Boxhandschuhe": {
            // runder Handschuh mit abgesetztem Daumen, weißer Stulpe und Schnürung
            int glove = v.tint(0xFFD8485A);
            int dark = Art.bright(glove, 0.85F);
            Art.sphere(m, vc, 0.0F, 0.9F, -0.3F, 2.75F, 2.5F, 2.9F, 4, 10, glove, b);
            Art.sphere(m, vc, side == 0 ? 2.2F : -2.2F, 0.6F, -1.4F, 0.95F, 1.4F, 1.0F, 3, 7, dark, b);
            Art.frustum(m, vc, 0.0F, 0.0F, -3.4F, -1.3F, 2.5F, 2.5F, 2.65F, 2.65F, 10, 0.0F, Art.WHITE, b, true, false);
            Art.ring(m, vc, 0.0F, 0.0F, -1.6F, -1.2F, 2.4F, 2.85F, 1.0F, 10, 0.0F, Art.IVORY, b);
            for (int i = 0; i < 3; i++) {
               float y = -3.0F + i * 0.6F;
               Art.box(m, vc, -0.7F, y, -2.65F, 0.7F, y + 0.16F, -2.5F, Art.CHARCOAL, b);
            }

            Art.diamond(m, vc, 0.0F, 0.9F, -3.22F, 0.5F, 2, Art.WHITE, b);
            break;
         }
         case "Krallen":
            // Knöchelplatte und drei gebogene, spitz zulaufende Klingen
            Art.bevel(m, vc, -2.1F, -0.6F, -2.45F, 2.1F, 0.9F, -1.95F, 0.15F, Art.CHARCOAL, b);
            for (int k = -1; k <= 1; k++) {
               float x = k * 1.15F;
               Art.gem(m, vc, x, 0.15F, -2.5F, 0.25F, 0.0F, 0.0F, 4, 45.0F, Art.STONE, b);
               int steel = Art.mix(Art.SILVER, v.tint(Art.SILVER), 0.5F);
               Art.taper(m, vc, x, 0.4F, -2.3F, 0.32F, x, 3.6F, -2.65F, 0.24F, 4, 45.0F, steel, Math.max(b, 0.8F), true, false);
               Art.taper(m, vc, x, 3.6F, -2.65F, 0.24F, x, 6.4F, -3.6F, 0.0F, 4, 45.0F, Art.bright(steel, 1.1F), Math.max(b, 0.8F), false, false);
            }
            break;
         case "Leuchtringe":
            // goldene Armreifen mit eingesetzten Steinen, drehen sich langsam
            for (int i = 0; i < 2; i++) {
               float y = -3.0F + i * 1.3F;
               Art.ring(m, vc, 0.0F, 0.0F, y, y + 0.55F, 2.2F, 2.65F, 1.0F, 12, t * 40.0F * (i == 0 ? 1 : -1), Art.GOLD, b);
               for (int k = 0; k < 4; k++) {
                  double a = Math.toRadians(t * 40.0F * (i == 0 ? 1 : -1) + k * 90.0F + 15.0F);
                  float gx = (float)Math.cos(a) * 2.7F, gz = (float)Math.sin(a) * 2.7F;
                  Art.gem(m, vc, gx, y + 0.28F, gz, 0.22F, 0.2F, 0.2F, 4, 0.0F, v.tint(k % 2 == 0 ? Art.SAKURA : 0xFFA8D8FF), Math.max(b, 0.85F));
               }
            }
            break;
         case "Zauberlicht": {
            // schwebender Kristall im Licht
            float bob = Mth.sin(t * 2.0F) * 0.8F;
            m.pushPose();
            m.translate(0.0F, 4.0F + bob, -1.0F);
            m.mulPose(Axis.YP.rotationDegrees(t * 60.0F));
            Art.gem(m, vc, 0.0F, 0.0F, 0.0F, 0.6F, 0.9F, 0.9F, 5, 0.0F, v.tint(0xFFD8B8FF), Math.max(b, 0.95F));
            m.popPose();
            break;
         }
         default:
      }
   }

   private void glow(PoseStack m, VertexConsumer vc, String st, CosmeticValue v, float t, int side) {
      float pulse = 0.75F + 0.25F * Mth.sin(t * 3.0F + side);
      switch (st) {
         case "Feuerfäuste":
            for (int k = 0; k < 8; k++) {
               m.pushPose();
               m.mulPose(Axis.YP.rotationDegrees(k * 45.0F));
               float fl = 0.5F + 0.5F * Mth.sin(t * 13.0F + k * 2.2F + side);
               Shapes.glow(m, vc, 1.2F, -1.0F - fl * 3.0F, -0.6F, 2.4F, 1.6F, 0.6F, v.tint(0xFFFF6010), 0.45F);
               Shapes.glow(m, vc, 1.0F, -0.2F - fl * 1.6F, -0.35F, 1.8F, 1.2F, 0.35F, v.tint(0xFFFFD040), 0.6F);
               m.popPose();
            }

            Shapes.glowBall(m, vc, 0.0F, 0.0F, 0.0F, 3.2F, v.tint(0xFFFF6010), 0.18F * pulse);
            break;
         case "Eishände":
            Shapes.glowBall(m, vc, 0.0F, 0.0F, 0.0F, 3.2F, v.tint(0xFF9AE0FF), 0.18F * pulse);
            break;
         case "Blitzhände": {
            Random r = new Random((long)(t * 14.0F) + side * 77L);

            for (int k = 0; k < 3; k++) {
               float a = r.nextFloat() * 6.283F;
               float px = 0.0F;
               float py = 0.0F;

               for (int j = 0; j < 4; j++) {
                  float nx = px + Mth.cos(a) * 1.3F + (r.nextFloat() - 0.5F) * 1.2F;
                  float ny = py + Mth.sin(a) * 1.3F + (r.nextFloat() - 0.5F) * 1.2F;
                  Shapes.bar(m, vc, px, py, nx, ny, (r.nextFloat() - 0.5F) * 2.0F, 0.25F, 0.25F, 0xFFF0FAFF, 1.0F, true, 0.95F);
                  Shapes.bar(m, vc, px, py, nx, ny, 0.0F, 0.9F, 0.9F, v.tint(0xFF60B0FF), 1.0F, true, 0.3F);
                  px = nx;
                  py = ny;
               }
            }
            break;
         }
         case "Rasengan": {
            m.translate(0.0F, 2.5F, -1.5F);
            Shapes.glowBall(m, vc, 0.0F, 0.0F, 0.0F, 3.4F, v.tint(0xFF4AB8FF), 0.35F);
            Shapes.glowBall(m, vc, 0.0F, 0.0F, 0.0F, 2.2F, v.tint(0xFF8AD8FF), 0.6F);
            Shapes.glowBall(m, vc, 0.0F, 0.0F, 0.0F, 1.0F, 0xFFFFFFFF, 0.9F);

            for (int k = 0; k < 6; k++) {
               m.pushPose();
               m.mulPose(Axis.YP.rotationDegrees(t * 720.0F + k * 60.0F));
               m.mulPose(Axis.XP.rotationDegrees(k * 30.0F));
               Shapes.glow(m, vc, 1.0F, -0.15F, -0.15F, 3.2F, 0.15F, 0.15F, 0xFFE8F8FF, 0.7F);
               m.popPose();
            }
            break;
         }
         case "Chidori": {
            m.translate(0.0F, 1.5F, -1.0F);
            float fl = 0.7F + 0.3F * Mth.sin(t * 47.0F);
            Shapes.glowBall(m, vc, 0.0F, 0.0F, 0.0F, 2.6F, v.tint(0xFF9AC8FF), 0.4F * fl);
            Shapes.glowBall(m, vc, 0.0F, 0.0F, 0.0F, 1.2F, 0xFFFFFFFF, 0.9F);
            Random r = new Random((long)(t * 20.0F));

            for (int k = 0; k < 6; k++) {
               float a = r.nextFloat() * 6.283F;
               float bz = (r.nextFloat() - 0.5F) * 3.0F;
               float px = 0.0F;
               float py = 0.0F;

               for (int j = 0; j < 3; j++) {
                  float nx = px + Mth.cos(a) * 1.6F + (r.nextFloat() - 0.5F) * 1.4F;
                  float ny = py + Mth.sin(a) * 1.6F + (r.nextFloat() - 0.5F) * 1.4F;
                  Shapes.bar(m, vc, px, py, nx, ny, bz, 0.22F, 0.22F, 0xFFFFFFFF, 1.0F, true, 0.95F);
                  px = nx;
                  py = ny;
               }
            }
            break;
         }
         case "Zauberlicht": {
            float bob = Mth.sin(t * 2.0F) * 0.8F;
            m.translate(0.0F, 4.0F + bob, -1.0F);
            int col = v.tint(0xFFC080FF);
            Shapes.glowBall(m, vc, 0.0F, 0.0F, 0.0F, 2.4F, col, 0.4F * pulse);
            Shapes.glowBall(m, vc, 0.0F, 0.0F, 0.0F, 1.0F, 0xFFFFFFFF, 0.85F);

            for (int k = 0; k < 4; k++) {
               float a = t * 3.0F + k * 1.57F;
               Shapes.glowCube(m, vc, Mth.cos(a) * 3.0F, Mth.sin(a * 1.3F) * 1.0F, Mth.sin(a) * 3.0F, 0.3F, col, 0.8F);
            }
            break;
         }
         case "Leuchtringe":
            Shapes.ring(m, vc, -3.0F, 2.5F, 1.0F, 1.0F, 14, t * 40.0F, Shapes.hsv(t * 0.2F + side * 0.5F + v.hue() / 360.0F, 0.7F, 1.0F), 1.0F, true, 0.4F);
            break;
         default:
      }
   }

   @Override
   public void particles(ClientLevel world, Player p, CosmeticValue v, Random r) {
      String st = v.style();
      if ((st.equals("Feuerfäuste") || st.equals("Blitzhände") || st.equals("Eishände")) && r.nextFloat() < 0.25F) {
         double yaw = Math.toRadians(p.yBodyRot);
         double side = r.nextBoolean() ? 0.38 : -0.38;
         double x = p.getX() + Math.cos(yaw) * side;
         double z = p.getZ() + Math.sin(yaw) * side;
         world.addParticle(
            st.equals("Feuerfäuste") ? ParticleTypes.SMALL_FLAME : (st.equals("Eishände") ? ParticleTypes.SNOWFLAKE : ParticleTypes.ELECTRIC_SPARK),
            x,
            p.getY() + 0.8,
            z,
            0.0,
            0.01,
            0.0
         );
      }
   }

   @Override
   protected boolean hasLayerOption() {
      return true;
   }
}
