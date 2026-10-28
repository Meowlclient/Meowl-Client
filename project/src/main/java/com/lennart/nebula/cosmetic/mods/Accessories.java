package com.lennart.nebula.cosmetic.mods;

import com.lennart.nebula.cosmetic.Cosmetic;
import com.lennart.nebula.cosmetic.CosmeticContext;
import com.lennart.nebula.cosmetic.CosmeticValue;
import com.lennart.nebula.cosmetic.Shapes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;

/** Accessoires am Oberkörper (Körperraum: Hals bei y = 0, Brust bei z = -2, Rücken bei z = 2). */
public class Accessories extends Cosmetic {
   public static final String[] STYLES = {"Fliege", "Krawatte", "Goldkette", "Medaille", "Schal", "Kristallanhänger", "Neon-Linien", "Hosenträger", "Herz-Anstecker"};

   private final com.lennart.nebula.setting.BooleanSetting overArmor = this.add(
      new com.lennart.nebula.setting.BooleanSetting("Über Rüstung", "Trägst du einen Brustpanzer, sitzen die Accessoires darauf statt darunter", true)
   );

   public Accessories() {
      super("Accessoires", "Am Oberkörper: Fliege, Krawatte, Goldkette, Medaille, wehender Schal, Kristallanhänger, Neon-Linien und mehr.", "acc", Items.GOLD_INGOT, STYLES);
      this.groups(this.overArmor);
   }

   @Override
   protected String extra() {
      return this.overArmor.get() ? "a" : "";
   }

   @Override
   public void render(CosmeticContext c, CosmeticValue v) {
      float t = c.time;
      float b = c.bright;
      float walk = c.walk;
      String st = v.style();
      boolean armored = v.extra().contains("a") && !c.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty();
      c.onBody(() -> {
         if (armored) {
            // Brustpanzer steht 1 px über dem Körper (8x12x4 -> 10x14x6)
            c.matrices.translate(0.0F, -0.06F, 0.0F);
            c.matrices.scale(1.25F, 1.1F, 1.5F);
         }

         c.solid((m, vc) -> this.solid(m, vc, st, v, t, walk, b));
         c.glow((m, vc) -> this.glow(m, vc, st, v, t));
      });
   }

   private void solid(PoseStack m, VertexConsumer vc, String st, CosmeticValue v, float t, float walk, float b) {
      float z = -2.05F;
      float sw = Mth.sin(t * 2.0F) * 4.0F + walk * Mth.sin(t * 9.0F) * 8.0F;
      switch (st) {
         case "Fliege": {
            int c = v.tint(0xFFC01828);
            Shapes.box(m, vc, -2.4F, 0.4F, z - 0.6F, -0.5F, 2.2F, z, c, b);
            Shapes.box(m, vc, 0.5F, 0.4F, z - 0.6F, 2.4F, 2.2F, z, c, b);
            Shapes.box(m, vc, -0.6F, 0.8F, z - 0.8F, 0.6F, 1.8F, z, Shapes.mix(c, 0xFF000000, 0.3F), b);
            break;
         }
         case "Krawatte": {
            int c = v.tint(0xFF2040A0);
            Shapes.box(m, vc, -0.8F, 0.2F, z - 0.4F, 0.8F, 1.4F, z, c, b);
            m.pushPose();
            m.translate(0.0F, 1.4F, z - 0.2F);
            m.mulPose(Axis.XP.rotationDegrees(-Math.abs(sw) * 0.6F));
            Shapes.box(m, vc, -0.7F, 0.0F, -0.2F, 0.7F, 6.0F, 0.2F, c, b);
            Shapes.box(m, vc, -0.4F, 6.0F, -0.2F, 0.4F, 6.8F, 0.2F, c, b);
            Shapes.box(m, vc, -0.72F, 2.0F, -0.25F, 0.72F, 2.4F, -0.2F, 0xFFE8E8E8, b);
            m.popPose();
            break;
         }
         case "Goldkette":
         case "Medaille":
         case "Kristallanhänger": {
            int gold = 0xFFE8B830;

            for (int i = 0; i <= 8; i++) {
               float a = (float)(Math.PI * i / 8.0);
               float x = Mth.cos(a) * 3.2F;
               float y = Mth.sin(a) * 3.0F;
               Shapes.box(m, vc, x - 0.35F, y - 0.35F, z - 0.4F, x + 0.35F, y + 0.35F, z, gold, b);
            }

            m.pushPose();
            m.translate(0.0F, 3.2F, z - 0.3F);
            m.mulPose(Axis.XP.rotationDegrees(-Math.abs(sw)));
            m.mulPose(Axis.ZP.rotationDegrees(sw * 0.5F));
            if (st.equals("Goldkette")) {
               Shapes.box(m, vc, -1.0F, 0.0F, -0.3F, 1.0F, 2.2F, 0.3F, gold, b);
               Shapes.box(m, vc, -0.5F, 0.4F, -0.4F, 0.5F, 1.8F, -0.3F, 0xFFFFF0A0, b);
            } else if (st.equals("Medaille")) {
               Shapes.box(m, vc, -1.4F, 0.6F, -0.3F, 1.4F, 3.4F, 0.3F, v.tint(0xFFE8B830), b);
               Shapes.box(m, vc, -0.7F, 1.3F, -0.4F, 0.7F, 2.7F, -0.3F, 0xFFC03030, b);
            } else {
               Shapes.box(m, vc, -0.8F, 0.4F, -0.5F, 0.8F, 2.6F, 0.5F, v.tint(0xFFA060FF), Math.max(b, 0.85F));
               Shapes.box(m, vc, -0.4F, 2.6F, -0.3F, 0.4F, 3.4F, 0.3F, v.tint(0xFFA060FF), Math.max(b, 0.85F));
            }

            m.popPose();
            break;
         }
         case "Schal": {
            int c = v.tint(0xFFD03030);
            int c2 = v.tint(0xFFF4F4F4);
            Shapes.box(m, vc, -4.4F, -0.6F, -2.6F, 4.4F, 1.6F, 2.6F, c, b);
            Shapes.box(m, vc, -4.45F, 0.1F, -2.65F, 4.45F, 0.7F, 2.65F, c2, b);
            // Enden wehen nach hinten
            m.pushPose();
            m.translate(2.0F, 1.0F, 2.6F);
            m.mulPose(Axis.XP.rotationDegrees(20.0F + walk * 50.0F));

            for (int i = 0; i < 4; i++) {
               float wave = Mth.sin(t * (3.0F + walk * 6.0F) - i * 0.9F) * (6.0F + walk * 12.0F);
               m.mulPose(Axis.XP.rotationDegrees(wave * 0.5F));
               m.mulPose(Axis.YP.rotationDegrees(wave * 0.3F));
               Shapes.box(m, vc, -1.2F, 0.0F, -0.25F, 1.2F, 2.0F, 0.25F, i % 2 == 0 ? c : c2, b);
               m.translate(0.0F, 2.0F, 0.0F);
            }

            m.popPose();
            break;
         }
         case "Hosenträger": {
            int c = v.tint(0xFFC02828);

            for (int s = -1; s <= 1; s += 2) {
               Shapes.box(m, vc, s * 2.4F - 0.6F, 0.0F, z - 0.15F, s * 2.4F + 0.6F, 12.0F, z, c, b);
               Shapes.box(m, vc, s * 2.4F - 0.6F, 0.0F, 2.0F, s * 2.4F + 0.6F, 12.0F, 2.15F, c, b);
               Shapes.box(m, vc, s * 2.4F - 0.5F, 9.0F, z - 0.3F, s * 2.4F + 0.5F, 9.8F, z - 0.15F, 0xFFD8D8D8, b);
            }
            break;
         }
         case "Herz-Anstecker": {
            int c = v.tint(0xFFFF3A6A);
            float beat = 1.0F + Math.max(0.0F, Mth.sin(t * 5.0F)) * 0.15F;
            m.pushPose();
            m.translate(1.8F, 3.0F, z - 0.2F);
            m.scale(beat * 0.6F, beat * 0.6F, 0.6F);
            Shapes.box(m, vc, -1.6F, -1.8F, -0.4F, -0.1F, -0.4F, 0.4F, c, b);
            Shapes.box(m, vc, 0.1F, -1.8F, -0.4F, 1.6F, -0.4F, 0.4F, c, b);
            Shapes.box(m, vc, -1.1F, -0.5F, -0.4F, 1.1F, 0.6F, 0.4F, c, b);
            Shapes.box(m, vc, -0.5F, 0.5F, -0.4F, 0.5F, 1.4F, 0.4F, c, b);
            m.popPose();
            break;
         }
         default:
      }
   }

   private void glow(PoseStack m, VertexConsumer vc, String st, CosmeticValue v, float t) {
      switch (st) {
         case "Neon-Linien": {
            int col = Shapes.hsv(t * 0.15F + v.hue() / 360.0F, 0.85F, 1.0F);
            float run = (t * 0.8F) % 1.0F;

            for (int s = -1; s <= 1; s += 2) {
               Shapes.glow(m, vc, s * 4.05F - 0.2F, 0.0F, -2.1F, s * 4.05F + 0.2F, 12.0F, -1.9F, col, 0.55F);
               Shapes.glow(m, vc, s * 4.05F - 0.2F, 0.0F, 1.9F, s * 4.05F + 0.2F, 12.0F, 2.1F, col, 0.55F);
               Shapes.glow(m, vc, s * 4.05F - 0.25F, run * 12.0F - 1.0F, -2.15F, s * 4.05F + 0.25F, run * 12.0F + 1.0F, -1.85F, 0xFFFFFFFF, 0.8F);
            }

            Shapes.glow(m, vc, -4.0F, 11.8F, -2.1F, 4.0F, 12.1F, -1.9F, col, 0.5F);
            Shapes.glow(m, vc, -0.2F, 2.0F, -2.15F, 0.2F, 10.0F, -2.05F, col, 0.4F);
            Shapes.glow(m, vc, -3.0F, 5.8F, -2.15F, 3.0F, 6.2F, -2.05F, col, 0.4F);
            break;
         }
         case "Kristallanhänger": {
            m.translate(0.0F, 3.2F, -2.35F);
            Shapes.glowBall(m, vc, 0.0F, 1.6F, 0.0F, 2.2F, v.tint(0xFFA060FF), 0.25F + 0.15F * Mth.sin(t * 2.0F));
            break;
         }
         case "Goldkette":
            Shapes.glow(m, vc, -0.6F, 3.6F, -2.5F, 0.6F, 4.8F, -2.4F, 0xFFFFFFFF, Mth.sin(t * 1.3F) > 0.9F ? 0.8F : 0.0F);
            break;
         default:
      }
   }

   @Override
   protected boolean hasLayerOption() {
      return true;
   }
}
