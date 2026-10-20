package com.lennart.nebula.cosmetic.mods;

import com.lennart.nebula.cosmetic.Art;
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

   /** Flache Form (Umriss in x/y um (cx, cy)) zwischen z0 und z1 hochgezogen. */
   private static void extrude(PoseStack m, VertexConsumer vc, float cx, float cy, float[][] p, float z0, float z1, int col, float b) {
      int n = p.length;
      float iz = (z0 + z1) / 2.0F;
      for (int i = 0; i < n; i++) {
         int j = (i + 1) % n;
         Art.face(m, vc, col, b, cx, cy, iz, cx, cy, z0, cx + p[i][0], cy + p[i][1], z0, cx + p[j][0], cy + p[j][1], z0);
         Art.face(m, vc, col, b, cx, cy, iz, cx, cy, z1, cx + p[i][0], cy + p[i][1], z1, cx + p[j][0], cy + p[j][1], z1);
         Art.face(
            m, vc, Art.bright(col, 0.88F), b, cx, cy, iz, cx + p[i][0], cy + p[i][1], z0, cx + p[j][0], cy + p[j][1], z0, cx + p[j][0], cy + p[j][1], z1,
            cx + p[i][0], cy + p[i][1], z1
         );
      }
   }

   /** Herzumriss mit n Punkten, Größe s (Spitze nach unten, +y). */
   private static float[][] heart(float s, int n) {
      float[][] p = new float[n][];
      for (int i = 0; i < n; i++) {
         double a = Math.PI * 2.0 * i / n;
         float x = (float)(16.0 * Math.pow(Math.sin(a), 3.0));
         float y = (float)(13.0 * Math.cos(a) - 5.0 * Math.cos(2.0 * a) - 2.0 * Math.cos(3.0 * a) - Math.cos(4.0 * a));
         p[i] = new float[]{x / 16.0F * s, -y / 16.0F * s};
      }

      return p;
   }

   /** Kettenglied (Ring, Achse nach vorn), um {@code angle} in der Bildebene gedreht; jedes zweite steht quer. */
   private static void link(PoseStack m, VertexConsumer vc, float x, float y, float z, float angle, boolean flat, float r, int col, float b) {
      m.pushPose();
      m.translate(x, y, z);
      m.mulPose(Axis.ZP.rotationDegrees(angle));
      m.mulPose(Axis.XP.rotationDegrees(flat ? 90.0F : 20.0F));
      Art.ring(m, vc, 0.0F, 0.0F, -0.09F, 0.09F, r * 0.45F, r, 1.45F, 8, 0.0F, col, b);
      m.popPose();
   }

   /** Kette als Bogen um den Hals bis zur Brustmitte (unterster Punkt bei y = depth). */
   private static void chain(PoseStack m, VertexConsumer vc, float z, float width, float depth, int links, float r, int col, float b) {
      for (int i = 0; i <= links; i++) {
         float a = (float)(Math.PI * i / links);
         float x = Mth.cos(a) * width;
         float y = Mth.sin(a) * depth;
         float tang = (float)Math.toDegrees(Math.atan2(Mth.cos(a) * depth, -Mth.sin(a) * width));
         link(m, vc, x, y, z - 0.25F, tang, i % 2 == 0, r, col, b);
      }
   }

   private void solid(PoseStack m, VertexConsumer vc, String st, CosmeticValue v, float t, float walk, float b) {
      float z = -2.05F;
      float sw = Mth.sin(t * 2.0F) * 4.0F + walk * Mth.sin(t * 9.0F) * 8.0F;
      switch (st) {
         case "Fliege": {
            // zwei gefaltete Schleifenflügel, gerafft zum Knoten, mit Falten
            int c = v.tint(0xFFD0465E);
            int fold = Art.bright(c, 0.82F);
            for (int s = -1; s <= 1; s += 2) {
               float[][] wing = {{s * 0.3F, -0.55F}, {s * 2.5F, -1.05F}, {s * 2.75F, 0.0F}, {s * 2.5F, 1.05F}, {s * 0.3F, 0.55F}};
               extrude(m, vc, 0.0F, 1.3F, wing, z - 0.75F, z, c, b);
               Art.box(m, vc, s * 1.1F - 0.09F, 0.6F, z - 0.8F, s * 1.1F + 0.09F, 2.0F, z - 0.7F, fold, b);
               Art.box(m, vc, s * 1.8F - 0.09F, 0.45F, z - 0.8F, s * 1.8F + 0.09F, 2.15F, z - 0.7F, fold, b);
            }

            Art.bevel(m, vc, -0.6F, 0.6F, z - 0.95F, 0.6F, 2.0F, z, 0.18F, fold, b);
            Art.box(m, vc, -0.35F, 0.85F, z - 1.0F, 0.35F, 1.05F, z - 0.94F, Art.bright(c, 1.15F), b);
            break;
         }
         case "Krawatte": {
            // Knoten als Trapez, Blatt verbreitert sich zur Spitze, diagonale Streifen und silberne Klammer
            int c = v.tint(0xFF3E5BA8);
            int stripe = Art.mix(c, Art.WHITE, 0.45F);
            float[][] knot = {{-0.85F, -0.55F}, {0.85F, -0.55F}, {0.5F, 0.6F}, {-0.5F, 0.6F}};
            extrude(m, vc, 0.0F, 0.75F, knot, z - 0.6F, z, Art.bright(c, 0.9F), b);
            m.pushPose();
            m.translate(0.0F, 1.35F, z - 0.25F);
            m.mulPose(Axis.XP.rotationDegrees(-Math.abs(sw) * 0.6F));
            float[][] blade = {{-0.5F, 0.0F}, {0.5F, 0.0F}, {1.05F, 5.6F}, {0.0F, 6.7F}, {-1.05F, 5.6F}};
            extrude(m, vc, 0.0F, 0.0F, blade, -0.22F, 0.22F, c, b);
            for (int i = 0; i < 3; i++) {
               float y = 1.6F + i * 1.5F;
               float w = 0.55F + y * 0.085F;
               Art.face(m, vc, stripe, b, 0.0F, y, 0.0F, -w, y + 0.35F, -0.24F, w, y - 0.15F, -0.24F, w, y + 0.2F, -0.24F, -w, y + 0.7F, -0.24F);
            }

            Art.bevel(m, vc, -1.0F, 2.75F, -0.4F, 1.0F, 3.05F, -0.2F, 0.06F, Art.SILVER, b);
            m.popPose();
            break;
         }
         case "Goldkette": {
            // Panzerkette aus abwechselnd liegenden und stehenden Gliedern, Anhänger: Goldmünze mit Edelstein
            int gold = v.tint(Art.GOLD);
            chain(m, vc, z, 3.3F, 3.1F, 14, 0.42F, gold, b);
            m.pushPose();
            m.translate(0.0F, 3.4F, z - 0.35F);
            m.mulPose(Axis.XP.rotationDegrees(-Math.abs(sw)));
            m.mulPose(Axis.ZP.rotationDegrees(sw * 0.5F));
            m.translate(0.0F, 1.35F, 0.0F);
            m.mulPose(Axis.XP.rotationDegrees(90.0F));
            Art.frustum(m, vc, 0.0F, 0.0F, -0.22F, 0.22F, 1.1F, 1.1F, 1.1F, 1.1F, 12, 0.0F, gold, b, true, true);
            Art.ring(m, vc, 0.0F, 0.0F, -0.3F, 0.3F, 0.85F, 1.2F, 1.0F, 12, 0.0F, Art.bright(gold, 1.12F), b);
            Art.gem(m, vc, 0.0F, -0.3F, 0.0F, 0.45F, 0.35F, 0.05F, 6, 0.0F, v.tint(Art.SAKURA), Math.max(b, 0.85F));
            m.popPose();
            break;
         }
         case "Medaille": {
            // Band als V in Pastellstreifen, runde Medaille mit Rand und Stern-Edelstein
            int gold = v.tint(Art.GOLD);
            for (int s = -1; s <= 1; s += 2) {
               Art.face(m, vc, Art.SAKURA, b, 0.0F, 1.5F, z, s * 3.0F, -0.05F, z - 0.2F, s * 1.6F, -0.05F, z - 0.2F, s * 0.2F, 3.6F, z - 0.2F, s * 1.4F, 3.6F, z - 0.2F);
               Art.face(m, vc, Art.WHITE, b, 0.0F, 1.5F, z, s * 2.55F, -0.05F, z - 0.25F, s * 2.05F, -0.05F, z - 0.25F, s * 0.6F, 3.6F, z - 0.25F, s * 1.0F, 3.6F, z - 0.25F);
            }

            m.pushPose();
            m.translate(0.0F, 3.6F, z - 0.35F);
            m.mulPose(Axis.XP.rotationDegrees(-Math.abs(sw)));
            m.mulPose(Axis.ZP.rotationDegrees(sw * 0.5F));
            Art.bevel(m, vc, -0.9F, -0.2F, -0.15F, 0.9F, 0.45F, 0.15F, 0.08F, gold, b);
            m.translate(0.0F, 1.85F, 0.0F);
            m.mulPose(Axis.XP.rotationDegrees(90.0F));
            Art.frustum(m, vc, 0.0F, 0.0F, -0.22F, 0.22F, 1.45F, 1.45F, 1.45F, 1.45F, 14, 0.0F, gold, b, true, true);
            Art.ring(m, vc, 0.0F, 0.0F, -0.32F, 0.32F, 1.15F, 1.55F, 1.0F, 14, 0.0F, Art.bright(gold, 1.12F), b);
            Art.gem(m, vc, 0.0F, -0.3F, 0.0F, 0.75F, 0.4F, 0.05F, 5, -90.0F, v.tint(Art.ROSE), Math.max(b, 0.85F));
            m.popPose();
            break;
         }
         case "Kristallanhänger": {
            // feine Kette, goldene Fassung, langer facettierter Kristall
            int crystal = v.tint(0xFFB592F0);
            chain(m, vc, z, 3.1F, 2.9F, 18, 0.24F, Art.GOLD, b);
            m.pushPose();
            m.translate(0.0F, 3.1F, z - 0.4F);
            m.mulPose(Axis.XP.rotationDegrees(-Math.abs(sw)));
            m.mulPose(Axis.ZP.rotationDegrees(sw * 0.5F));
            Art.frustum(m, vc, 0.0F, 0.0F, 0.0F, 0.7F, 0.25F, 0.25F, 0.55F, 0.55F, 6, 0.0F, Art.GOLD, b, true, true);
            Art.gem(m, vc, 0.0F, 1.3F, 0.0F, 0.75F, 0.6F, 1.9F, 6, 0.0F, crystal, Math.max(b, 0.9F));
            m.popPose();
            break;
         }
         case "Schal": {
            // Strickschal: dicke Wicklung mit Rippen, vorn geknotet, ein Ende hängt vorn, eines weht hinten
            int c = v.tint(0xFFD8607A);
            int c2 = Art.IVORY;
            Art.bevel(m, vc, -4.5F, -0.8F, -2.7F, 4.5F, 1.9F, 2.7F, 0.55F, c, b);
            Art.bevel(m, vc, -4.3F, -1.3F, -2.5F, 4.3F, -0.5F, 2.5F, 0.3F, Art.bright(c, 1.08F), b);
            for (int i = -3; i <= 3; i++) {
               Art.box(m, vc, i * 1.2F - 0.12F, -0.6F, -2.78F, i * 1.2F + 0.12F, 1.7F, -2.7F, Art.bright(c, 0.85F), b);
            }

            Art.bevel(m, vc, 0.8F, 0.9F, -3.3F, 2.6F, 2.6F, -2.4F, 0.4F, c, b);
            m.pushPose();
            m.translate(1.7F, 2.5F, -2.9F);
            m.mulPose(Axis.XP.rotationDegrees(-Math.abs(sw) * 0.4F - 4.0F));
            for (int i = 0; i < 3; i++) {
               Art.bevel(m, vc, -0.85F, i * 1.6F, -0.3F, 0.85F, i * 1.6F + 1.7F, 0.3F, 0.15F, i == 1 ? c2 : c, b);
            }

            for (int i = -2; i <= 2; i++) {
               Art.box(m, vc, i * 0.36F - 0.09F, 4.8F, -0.12F, i * 0.36F + 0.09F, 5.5F, 0.12F, c2, b);
            }

            m.popPose();
            m.pushPose();
            m.translate(2.0F, 1.0F, 2.6F);
            m.mulPose(Axis.XP.rotationDegrees(20.0F + walk * 50.0F));
            for (int i = 0; i < 4; i++) {
               float wave = Mth.sin(t * (3.0F + walk * 6.0F) - i * 0.9F) * (6.0F + walk * 12.0F);
               m.mulPose(Axis.XP.rotationDegrees(wave * 0.5F));
               m.mulPose(Axis.YP.rotationDegrees(wave * 0.3F));
               Art.bevel(m, vc, -1.2F, 0.0F, -0.28F, 1.2F, 2.05F, 0.28F, 0.12F, i % 2 == 0 ? c : c2, b);
               m.translate(0.0F, 2.0F, 0.0F);
            }

            for (int i = -2; i <= 2; i++) {
               Art.box(m, vc, i * 0.48F - 0.1F, 0.0F, -0.12F, i * 0.48F + 0.1F, 0.7F, 0.12F, c2, b);
            }

            m.popPose();
            break;
         }
         case "Hosenträger": {
            // Lederbänder mit Nähten, vorn silberne Klemmen, hinten gekreuzt mit Lederpatch
            int c = v.tint(0xFFB64A4A);
            int seam = Art.bright(c, 1.25F);
            for (int s = -1; s <= 1; s += 2) {
               Art.bevel(m, vc, s * 2.4F - 0.6F, -0.05F, z - 0.2F, s * 2.4F + 0.6F, 9.2F, z, 0.08F, c, b);
               Art.box(m, vc, s * 2.4F - 0.04F, 0.3F, z - 0.24F, s * 2.4F + 0.04F, 8.8F, z - 0.2F, seam, b);
               Art.bevel(m, vc, s * 2.4F - 0.7F, 8.9F, z - 0.45F, s * 2.4F + 0.7F, 10.3F, z - 0.05F, 0.12F, Art.SILVER, b);
               for (int k = -1; k <= 1; k++) {
                  Art.box(m, vc, s * 2.4F + k * 0.38F - 0.08F, 10.2F, z - 0.4F, s * 2.4F + k * 0.38F + 0.08F, 10.6F, z - 0.1F, Art.STONE, b);
               }

               // hinten: diagonal zur Mitte
               Art.face(m, vc, c, b, 0.0F, 6.0F, 1.0F, s * 3.2F, 0.0F, 2.05F, s * 2.0F, 0.0F, 2.05F, s * -1.0F, 9.5F, 2.05F, s * 0.2F, 9.5F, 2.05F);
               Art.face(m, vc, c, b, 0.0F, 6.0F, 1.0F, s * 0.2F, 9.5F, 2.05F, s * -1.0F, 9.5F, 2.05F, s * -1.1F, 12.0F, 2.05F, s * 0.1F, 12.0F, 2.05F);
            }

            Art.bevel(m, vc, -1.0F, 7.6F, 2.0F, 1.0F, 9.4F, 2.3F, 0.15F, Art.bright(c, 0.85F), b);
            break;
         }
         case "Herz-Anstecker": {
            // emailliertes Herz mit Goldrand und Glanz, schlägt sanft
            int c = v.tint(0xFFF0607E);
            float beat = 1.0F + Math.max(0.0F, Mth.sin(t * 5.0F)) * Math.max(0.0F, Mth.sin(t * 5.0F)) * 0.12F;
            m.pushPose();
            m.translate(1.9F, 3.0F, z);
            m.scale(beat, beat, 1.0F);
            extrude(m, vc, 0.0F, 0.0F, heart(1.45F, 20), -0.3F, 0.0F, Art.GOLD, b);
            extrude(m, vc, 0.0F, 0.08F, heart(1.15F, 20), -0.55F, -0.3F, c, Math.max(b, 0.85F));
            Art.box(m, vc, -0.75F, -0.75F, -0.6F, -0.35F, -0.45F, -0.54F, 0xFFFFFFFF, 1.0F);
            m.popPose();
            break;
         }
         case "Neon-Linien":
            // dünne dunkle Leitungen, in denen das Licht läuft (Leuchten im Glow-Durchgang)
            for (int s = -1; s <= 1; s += 2) {
               Art.box(m, vc, s * 4.05F - 0.12F, 0.0F, -2.06F, s * 4.05F + 0.12F, 12.0F, -1.96F, Art.CHARCOAL, b);
               Art.box(m, vc, s * 4.05F - 0.12F, 0.0F, 1.96F, s * 4.05F + 0.12F, 12.0F, 2.06F, Art.CHARCOAL, b);
            }
            break;
         default:
      }
   }

   private void glow(PoseStack m, VertexConsumer vc, String st, CosmeticValue v, float t) {
      switch (st) {
         case "Neon-Linien": {
            int col = Shapes.hsv(t * 0.15F + v.hue() / 360.0F, 0.6F, 1.0F);
            float run = (t * 0.8F) % 1.0F;

            for (int s = -1; s <= 1; s += 2) {
               Shapes.glow(m, vc, s * 4.05F - 0.2F, 0.0F, -2.1F, s * 4.05F + 0.2F, 12.0F, -1.9F, col, 0.45F);
               Shapes.glow(m, vc, s * 4.05F - 0.2F, 0.0F, 1.9F, s * 4.05F + 0.2F, 12.0F, 2.1F, col, 0.45F);
               Shapes.glow(m, vc, s * 4.05F - 0.25F, run * 12.0F - 1.0F, -2.15F, s * 4.05F + 0.25F, run * 12.0F + 1.0F, -1.85F, 0xFFFFFFFF, 0.7F);
            }

            Shapes.glow(m, vc, -4.0F, 11.8F, -2.1F, 4.0F, 12.1F, -1.9F, col, 0.4F);
            Shapes.glow(m, vc, -0.2F, 2.0F, -2.15F, 0.2F, 10.0F, -2.05F, col, 0.35F);
            Shapes.glow(m, vc, -3.0F, 5.8F, -2.15F, 3.0F, 6.2F, -2.05F, col, 0.35F);
            break;
         }
         case "Kristallanhänger": {
            float sw = Mth.sin(t * 2.0F) * 4.0F;
            m.translate(0.0F, 3.1F, -2.45F);
            m.mulPose(Axis.XP.rotationDegrees(-Math.abs(sw)));
            Art.glowSpark(m, vc, 0.0F, 1.4F, 0.0F, 1.2F, v.tint(0xFFB592F0), 0.3F + 0.15F * Mth.sin(t * 2.0F));
            break;
         }
         case "Goldkette":
         case "Medaille": {
            // kurzer Glanz, der ab und zu über das Metall läuft
            float g = (t * 0.35F) % 1.0F;
            if (g < 0.2F) {
               float k = Mth.sin(g / 0.2F * (float)Math.PI);
               Art.glowSpark(m, vc, -1.0F + g * 10.0F, 4.6F, -2.7F, 0.45F, 0xFFFFF4D8, 0.6F * k);
            }
            break;
         }
         default:
      }
   }

   @Override
   protected boolean hasLayerOption() {
      return true;
   }
}
