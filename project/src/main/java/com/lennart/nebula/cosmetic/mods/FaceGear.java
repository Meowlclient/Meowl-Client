package com.lennart.nebula.cosmetic.mods;

import com.lennart.nebula.cosmetic.Cosmetic;
import com.lennart.nebula.cosmetic.CosmeticContext;
import com.lennart.nebula.cosmetic.CosmeticValue;
import com.lennart.nebula.cosmetic.Shapes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;

/** Brillen und Masken im Gesicht. Gesicht im Kopfraum bei z = -4, Augen auf Höhe y = -4..-3. */
public class FaceGear extends Cosmetic {
   public static final String[] STYLES = {
      "Sonnenbrille", "Pixelbrille", "3D-Brille", "Herzbrille", "Monokel", "VR-Brille", "Cyber-Visier", "Augenklappe", "Ninja-Maske", "Schnurrbart",
      "Bart", "Clownsnase", "Skimaske", "Taucherbrille"
   };

   public FaceGear() {
      super("Brillen & Masken", "Für dein Gesicht: Sonnenbrille, Pixelbrille, VR-Brille, Cyber-Visier, Monokel, Bart, Clownsnase und mehr.", "face", Items.GLASS_PANE, STYLES);
      this.groups();
   }

   @Override
   public void render(CosmeticContext c, CosmeticValue v) {
      float t = c.time;
      float b = c.bright;
      String st = v.style();
      c.onHead(() -> {
         c.solid((m, vc) -> {
            scale(m, v.size());
            this.solid(m, vc, st, v, t, b);
         });
         c.glow((m, vc) -> {
            scale(m, v.size());
            this.glow(m, vc, st, v, t);
         });
      });
   }

   private static void scale(PoseStack m, float s) {
      m.translate(0.0F, -4.0F, -4.0F);
      m.scale(s, s, s);
      m.translate(0.0F, 4.0F, 4.0F);
   }

   /** Bügel an beiden Seiten. */
   private static void arms(PoseStack m, VertexConsumer vc, int c, float b) {
      Shapes.box(m, vc, -4.35F, -4.6F, -4.3F, -4.05F, -4.1F, 1.0F, c, b);
      Shapes.box(m, vc, 4.05F, -4.6F, -4.3F, 4.35F, -4.1F, 1.0F, c, b);
   }

   private void solid(PoseStack m, VertexConsumer vc, String st, CosmeticValue v, float t, float b) {
      float z0 = -4.6F;
      float z1 = -4.05F;
      switch (st) {
         case "Pixelbrille": {
            int black = 0xFF101010;
            Shapes.box(m, vc, -4.3F, -5.0F, z0, 4.3F, -4.4F, z1, black, b);
            Shapes.box(m, vc, -3.6F, -4.4F, z0, -0.6F, -3.0F, z1, black, b);
            Shapes.box(m, vc, 0.6F, -4.4F, z0, 3.6F, -3.0F, z1, black, b);
            Shapes.box(m, vc, -3.0F, -3.0F, z0, -1.2F, -2.4F, z1, black, b);
            Shapes.box(m, vc, 1.2F, -3.0F, z0, 3.0F, -2.4F, z1, black, b);
            Shapes.box(m, vc, -3.2F, -4.2F, z0 - 0.05F, -2.6F, -3.6F, z0, 0xFFFFFFFF, 1.0F);
            Shapes.box(m, vc, 1.0F, -4.2F, z0 - 0.05F, 1.6F, -3.6F, z0, 0xFFFFFFFF, 1.0F);
            arms(m, vc, black, b);
            break;
         }
         case "3D-Brille":
            Shapes.box(m, vc, -4.2F, -5.0F, z0, 4.2F, -2.6F, z1, 0xFFF4F4F4, b);
            Shapes.box(m, vc, -3.5F, -4.5F, z0 - 0.05F, -0.6F, -3.0F, z0, 0xFFE02020, Math.max(b, 0.8F));
            Shapes.box(m, vc, 0.6F, -4.5F, z0 - 0.05F, 3.5F, -3.0F, z0, 0xFF20D0E0, Math.max(b, 0.8F));
            arms(m, vc, 0xFFF4F4F4, b);
            break;
         case "Herzbrille":
            for (int side = -1; side <= 1; side += 2) {
               float cx = side * 2.1F;
               int pink = v.tint(0xFFFF4A8A);
               Shapes.box(m, vc, cx - 1.8F, -5.0F, z0, cx - 0.1F, -3.8F, z1, pink, b);
               Shapes.box(m, vc, cx + 0.1F, -5.0F, z0, cx + 1.8F, -3.8F, z1, pink, b);
               Shapes.box(m, vc, cx - 1.3F, -3.8F, z0, cx + 1.3F, -2.9F, z1, pink, b);
               Shapes.box(m, vc, cx - 0.6F, -2.9F, z0, cx + 0.6F, -2.2F, z1, pink, b);
            }

            arms(m, vc, v.tint(0xFFD02060), b);
            break;
         case "Monokel": {
            int gold = 0xFFE8B830;
            Shapes.box(m, vc, 0.6F, -5.2F, z0, 3.6F, -4.8F, z1, gold, b);
            Shapes.box(m, vc, 0.6F, -2.6F, z0, 3.6F, -2.2F, z1, gold, b);
            Shapes.box(m, vc, 0.6F, -4.8F, z0, 1.0F, -2.6F, z1, gold, b);
            Shapes.box(m, vc, 3.2F, -4.8F, z0, 3.6F, -2.6F, z1, gold, b);

            for (int i = 0; i < 6; i++) {
               float sw = Mth.sin(t * 1.5F) * 0.3F;
               Shapes.cube(m, vc, 3.6F + i * 0.15F + sw * i * 0.1F, -2.0F + i * 0.9F, -4.3F, 0.15F, gold, b);
            }
            break;
         }
         case "VR-Brille": {
            int body = v.tint(0xFF2A2A34);
            Shapes.box(m, vc, -4.4F, -5.6F, -6.0F, 4.4F, -2.2F, -4.0F, body, b);
            Shapes.box(m, vc, -4.5F, -5.0F, -4.0F, 4.5F, -4.2F, 4.5F, 0xFF18181C, b);
            break;
         }
         case "Cyber-Visier":
            Shapes.box(m, vc, -4.5F, -5.2F, -4.7F, 4.5F, -4.9F, -4.0F, 0xFF303040, b);
            Shapes.box(m, vc, -4.5F, -2.8F, -4.7F, 4.5F, -2.5F, -4.0F, 0xFF303040, b);
            Shapes.box(m, vc, -4.6F, -5.2F, -4.0F, -4.1F, -2.5F, 0.0F, 0xFF303040, b);
            Shapes.box(m, vc, 4.1F, -5.2F, -4.0F, 4.6F, -2.5F, 0.0F, 0xFF303040, b);
            break;
         case "Augenklappe":
            Shapes.box(m, vc, -3.6F, -4.8F, z0, -0.6F, -2.6F, z1, 0xFF141414, b);
            Shapes.box(m, vc, -4.3F, -6.0F, -4.2F, 4.3F, -5.6F, 4.3F, 0xFF141414, b);
            break;
         case "Ninja-Maske": {
            int cloth = v.tint(0xFF1A1A24);
            Shapes.box(m, vc, -4.3F, -2.6F, -4.35F, 4.3F, 0.1F, 4.35F, cloth, b);
            Shapes.box(m, vc, -4.3F, -6.4F, -4.35F, 4.3F, -5.2F, 4.35F, cloth, b);
            Shapes.box(m, vc, -1.0F, -6.2F, 4.3F, 1.0F, -4.0F, 6.5F, cloth, b);
            break;
         }
         case "Schnurrbart": {
            int hair = v.tint(0xFF3A2414);
            Shapes.box(m, vc, -2.6F, -2.6F, -4.5F, 2.6F, -1.9F, -4.0F, hair, b);
            Shapes.box(m, vc, -3.6F, -2.2F, -4.5F, -2.4F, -1.4F, -4.0F, hair, b);
            Shapes.box(m, vc, 2.4F, -2.2F, -4.5F, 3.6F, -1.4F, -4.0F, hair, b);
            Shapes.box(m, vc, -4.0F, -1.7F, -4.5F, -3.4F, -0.9F, -4.0F, hair, b);
            Shapes.box(m, vc, 3.4F, -1.7F, -4.5F, 4.0F, -0.9F, -4.0F, hair, b);
            break;
         }
         case "Bart": {
            int hair = v.tint(0xFF5A3A20);
            Shapes.box(m, vc, -4.2F, -2.6F, -4.6F, 4.2F, 1.2F, -4.0F, hair, b);
            Shapes.box(m, vc, -3.2F, 1.2F, -4.6F, 3.2F, 3.0F, -3.6F, hair, b);
            Shapes.box(m, vc, -2.0F, 3.0F, -4.4F, 2.0F, 4.4F, -3.6F, hair, b);
            Shapes.box(m, vc, -4.2F, -4.0F, -4.3F, -3.8F, -2.6F, -2.0F, hair, b);
            Shapes.box(m, vc, 3.8F, -4.0F, -4.3F, 4.2F, -2.6F, -2.0F, hair, b);
            Shapes.box(m, vc, -1.6F, -1.7F, -4.7F, 1.6F, -1.2F, -4.5F, 0xFF8A3A3A, b);
            break;
         }
         case "Clownsnase":
            Shapes.ball(m, vc, 0.0F, -1.9F, -4.9F, 0.95F, v.tint(0xFFE02020), Math.max(b, 0.8F));
            break;
         case "Skimaske": {
            int knit = v.tint(0xFFC03030);
            // Strickmaske mit Augenschlitz
            Shapes.box(m, vc, -4.35F, -8.4F, -4.35F, 4.35F, -4.6F, 4.35F, knit, b);
            Shapes.box(m, vc, -4.35F, -2.8F, -4.35F, 4.35F, 0.2F, 4.35F, knit, b);
            Shapes.box(m, vc, -4.35F, -4.6F, -2.0F, 4.35F, -2.8F, 4.35F, knit, b);
            Shapes.box(m, vc, -4.35F, -4.6F, -4.35F, -3.6F, -2.8F, -2.0F, knit, b);
            Shapes.box(m, vc, 3.6F, -4.6F, -4.35F, 4.35F, -2.8F, -2.0F, knit, b);
            Shapes.box(m, vc, -0.6F, -4.6F, -4.35F, 0.6F, -2.8F, -2.0F, knit, b);
            Shapes.box(m, vc, -1.4F, -9.6F, -1.4F, 1.4F, -8.4F, 1.4F, 0xFFF0F0F0, b);
            break;
         }
         case "Taucherbrille": {
            Shapes.box(m, vc, -4.0F, -5.6F, -5.0F, 4.0F, -5.0F, -4.0F, 0xFF20A0D0, b);
            Shapes.box(m, vc, -4.0F, -2.4F, -5.0F, 4.0F, -1.8F, -4.0F, 0xFF20A0D0, b);
            Shapes.box(m, vc, -4.0F, -5.0F, -5.0F, -3.4F, -2.4F, -4.0F, 0xFF20A0D0, b);
            Shapes.box(m, vc, 3.4F, -5.0F, -5.0F, 4.0F, -2.4F, -4.0F, 0xFF20A0D0, b);
            Shapes.bar(m, vc, 4.4F, -3.0F, 5.4F, -10.0F, 0.0F, 0.8F, 0.8F, 0xFFE8D020, b, false, 1.0F);
            break;
         }
         default: {
            // Sonnenbrille
            int frame = v.tint(0xFF141418);
            Shapes.box(m, vc, -4.3F, -4.9F, z0, 4.3F, -4.5F, z1, frame, b);
            Shapes.box(m, vc, -3.8F, -4.5F, z0, -0.5F, -2.7F, z1, 0xFF0A0A12, b);
            Shapes.box(m, vc, 0.5F, -4.5F, z0, 3.8F, -2.7F, z1, 0xFF0A0A12, b);
            arms(m, vc, frame, b);
         }
      }
   }

   private void glow(PoseStack m, VertexConsumer vc, String st, CosmeticValue v, float t) {
      switch (st) {
         case "Sonnenbrille": {
            float g = frac(t * 0.25F);
            float x = -3.8F + g * 7.6F;
            Shapes.glow(m, vc, x - 0.4F, -4.5F, -4.7F, x + 0.4F, -2.7F, -4.62F, 0xFFFFFFFF, 0.35F * Mth.sin(g * (float)Math.PI));
            break;
         }
         case "Monokel":
            Shapes.glow(m, vc, 1.0F, -4.8F, -4.62F, 3.2F, -2.6F, -4.55F, 0xFFB8E8FF, 0.18F);
            break;
         case "VR-Brille": {
            float scan = frac(t * 0.6F);
            int col = Shapes.hsv(t * 0.1F + v.hue() / 360.0F, 0.7F, 1.0F);
            Shapes.glow(m, vc, -4.0F, -5.2F, -6.1F, 4.0F, -2.6F, -6.0F, col, 0.25F);
            Shapes.glow(m, vc, -4.0F, -5.2F + scan * 2.4F, -6.15F, 4.0F, -4.9F + scan * 2.4F, -6.05F, 0xFFFFFFFF, 0.6F);
            break;
         }
         case "Cyber-Visier": {
            int col = v.tint(0xFFFF2A6A);
            Shapes.glow(m, vc, -4.4F, -4.9F, -4.6F, 4.4F, -2.8F, -4.3F, col, 0.4F);
            float x = Mth.sin(t * 2.6F) * 3.6F;
            Shapes.glow(m, vc, x - 0.6F, -4.6F, -4.75F, x + 0.6F, -3.1F, -4.55F, 0xFFFFFFFF, 0.8F);
            break;
         }
         case "Taucherbrille":
            Shapes.glow(m, vc, -3.4F, -5.0F, -4.95F, 3.4F, -2.4F, -4.9F, 0xFF9AE0FF, 0.15F);
            break;
         default:
      }
   }

   private static float frac(float x) {
      return x - (float)Math.floor(x);
   }

   @Override
   protected boolean hasLayerOption() {
      return true;
   }
}
