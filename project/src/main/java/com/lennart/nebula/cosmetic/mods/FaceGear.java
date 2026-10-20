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

   /** Bügel an beiden Seiten mit Scharnier. */
   private static void arms(PoseStack m, VertexConsumer vc, int c, float b) {
      for (int s = -1; s <= 1; s += 2) {
         Art.bevel(m, vc, s * 4.05F, -4.65F, -4.55F, s * 4.45F, -4.05F, -3.7F, 0.08F, c, b);
         Art.bevel(m, vc, s * 4.12F, -4.55F, -3.7F, s * 4.38F, -4.15F, 0.6F, 0.06F, c, b);
         Art.bevel(m, vc, s * 4.12F, -4.55F, 0.4F, s * 4.38F, -3.3F, 1.1F, 0.06F, c, b);
      }
   }

   /** Glas mit Fassung vor dem Gesicht: Mitte (cx, cy), Halbachsen rx/ry, n Ecken. */
   private static void lens(PoseStack m, VertexConsumer vc, float cx, float cy, float rx, float ry, int n, float rot, int glass, int frame, float fw, float b) {
      m.pushPose();
      m.translate(cx, cy, -4.42F);
      m.mulPose(Axis.XP.rotationDegrees(90.0F));
      Art.frustum(m, vc, 0.0F, 0.0F, -0.12F, 0.12F, rx, ry, rx, ry, n, rot, glass, b, true, true);
      Art.ring(m, vc, 0.0F, 0.0F, -0.22F, 0.24F, rx - 0.05F, rx + fw, ry / rx, n, rot, frame, b);
      m.popPose();
   }

   /** Herzform (Umriss, Mitte bei 0): n Punkte. */
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

   /** Flache Form (Umriss in x/y) zwischen z0 und z1 hochgezogen. */
   private static void extrude(PoseStack m, VertexConsumer vc, float cx, float cy, float[][] p, float z0, float z1, int col, float b) {
      int n = p.length;
      float iz = (z0 + z1) / 2.0F;
      for (int i = 0; i < n; i++) {
         int j = (i + 1) % n;
         Art.face(m, vc, col, b, cx, cy, iz, cx, cy, z0, cx + p[i][0], cy + p[i][1], z0, cx + p[j][0], cy + p[j][1], z0);
         Art.face(m, vc, col, b, cx, cy, iz, cx, cy, z1, cx + p[i][0], cy + p[i][1], z1, cx + p[j][0], cy + p[j][1], z1);
         Art.face(
            m, vc, Art.bright(col, 0.9F), b, cx, cy, iz, cx + p[i][0], cy + p[i][1], z0, cx + p[j][0], cy + p[j][1], z0, cx + p[j][0], cy + p[j][1], z1,
            cx + p[i][0], cy + p[i][1], z1
         );
      }
   }

   /** Band einmal um den Kopf (eckig, folgt der Kopfform) zwischen y1 und y2. */
   private static void strap(PoseStack m, VertexConsumer vc, float y1, float y2, float r, int col, float b) {
      Art.bevel(m, vc, -r, y1, -r, r, y2, -r + 0.35F, 0.08F, col, b);
      Art.bevel(m, vc, -r, y1, r - 0.35F, r, y2, r, 0.08F, col, b);
      Art.bevel(m, vc, -r, y1, -r, -r + 0.35F, y2, r, 0.08F, col, b);
      Art.bevel(m, vc, r - 0.35F, y1, -r, r, y2, r, 0.08F, col, b);
   }

   private void solid(PoseStack m, VertexConsumer vc, String st, CosmeticValue v, float t, float b) {
      switch (st) {
         case "Pixelbrille": {
            int black = 0xFF16151A;
            float z0 = -4.6F, z1 = -4.05F;
            Art.bevel(m, vc, -4.3F, -5.0F, z0, 4.3F, -4.4F, z1, 0.06F, black, b);
            Art.bevel(m, vc, -3.6F, -4.4F, z0, -0.6F, -3.0F, z1, 0.06F, black, b);
            Art.bevel(m, vc, 0.6F, -4.4F, z0, 3.6F, -3.0F, z1, 0.06F, black, b);
            Art.bevel(m, vc, -3.0F, -3.0F, z0, -1.2F, -2.4F, z1, 0.06F, black, b);
            Art.bevel(m, vc, 1.2F, -3.0F, z0, 3.0F, -2.4F, z1, 0.06F, black, b);
            Art.box(m, vc, -3.2F, -4.25F, z0 - 0.06F, -2.6F, -3.65F, z0, 0xFFFFFFFF, 1.0F);
            Art.box(m, vc, -2.55F, -4.25F, z0 - 0.06F, -2.25F, -3.95F, z0, 0xFFE8E8EC, 1.0F);
            Art.box(m, vc, 1.0F, -4.25F, z0 - 0.06F, 1.6F, -3.65F, z0, 0xFFFFFFFF, 1.0F);
            Art.box(m, vc, 1.65F, -4.25F, z0 - 0.06F, 1.95F, -3.95F, z0, 0xFFE8E8EC, 1.0F);
            arms(m, vc, black, b);
            break;
         }
         case "3D-Brille": {
            int paper = 0xFFF7F3EE;
            Art.bevel(m, vc, -4.3F, -5.1F, -4.55F, 4.3F, -2.5F, -4.1F, 0.12F, paper, b);
            Art.box(m, vc, -3.55F, -4.6F, -4.62F, -0.65F, -2.95F, -4.5F, 0xFFE55A6E, Math.max(b, 0.85F));
            Art.box(m, vc, 0.65F, -4.6F, -4.62F, 3.55F, -2.95F, -4.5F, 0xFF62CFE0, Math.max(b, 0.85F));
            // Nasenkerbe
            Art.face(m, vc, Art.INK, b, 0.0F, -3.0F, -4.0F, -0.55F, -2.5F, -4.58F, 0.55F, -2.5F, -4.58F, 0.0F, -3.15F, -4.58F);
            arms(m, vc, paper, b);
            break;
         }
         case "Herzbrille": {
            int pink = v.tint(0xFFF07AA2);
            int glass = v.tint(0xFFFFB9CF);
            for (int side = -1; side <= 1; side += 2) {
               float cx = side * 2.1F;
               extrude(m, vc, cx, -3.75F, heart(2.05F, 18), -4.62F, -4.1F, pink, b);
               extrude(m, vc, cx, -3.75F, heart(1.55F, 18), -4.72F, -4.6F, glass, Math.max(b, 0.85F));
            }

            Art.limb(m, vc, -0.6F, -4.5F, -4.4F, 0.6F, -4.5F, -4.4F, 0.18F, 0.18F, 5, pink, b);
            arms(m, vc, Art.bright(pink, 0.85F), b);
            break;
         }
         case "Monokel": {
            int gold = 0xFFE3BF73;
            lens(m, vc, 2.1F, -3.7F, 1.45F, 1.45F, 12, 0.0F, 0xFFD4ECF7, gold, 0.32F, b);
            // Kette aus Gliedern, schwingt leicht
            float sw = Mth.sin(t * 1.5F) * 0.3F;
            float px = 3.4F, py = -2.6F, pz = -4.35F;
            for (int i = 0; i < 7; i++) {
               float nx = 3.6F + i * 0.18F + sw * i * 0.12F, ny = -2.2F + i * 0.85F, nz = -4.3F + i * 0.1F;
               m.pushPose();
               m.translate(nx, ny, nz);
               m.mulPose((i % 2 == 0 ? Axis.XP : Axis.ZP).rotationDegrees(90.0F));
               Art.ring(m, vc, 0.0F, 0.0F, -0.07F, 0.07F, 0.18F, 0.36F, 1.4F, 6, 0.0F, gold, b);
               m.popPose();
               px = nx;
               py = ny;
               pz = nz;
            }

            Art.gem(m, vc, px, py + 0.5F, pz, 0.35F, 0.3F, 0.5F, 6, 0.0F, v.tint(0xFFE87A96), b);
            break;
         }
         case "VR-Brille": {
            int body = v.tint(0xFFF2EEF2);
            int face = 0xFF2A2730;
            Art.bevel(m, vc, -4.5F, -5.8F, -6.2F, 4.5F, -2.0F, -4.0F, 0.55F, body, b);
            Art.bevel(m, vc, -4.0F, -5.35F, -6.35F, 4.0F, -2.45F, -6.0F, 0.3F, face, b);
            // Lüftungsschlitze oben, Schaumpolster, Kopfband
            for (int i = -2; i <= 2; i++) {
               Art.box(m, vc, i * 1.3F - 0.3F, -5.85F, -5.6F, i * 1.3F + 0.3F, -5.75F, -4.6F, Art.bright(body, 0.8F), b);
            }

            Art.bevel(m, vc, -4.3F, -5.6F, -4.2F, 4.3F, -2.2F, -3.9F, 0.15F, 0xFFBFB6C4, b);
            strap(m, vc, -5.0F, -3.9F, 4.4F, v.tint(0xFF3A3540), b);
            Art.bevel(m, vc, -1.0F, -8.45F, -4.2F, 1.0F, -8.0F, 4.4F, 0.12F, v.tint(0xFF3A3540), b);
            Art.sakura(m, vc, 3.0F, -5.95F, -5.2F, 0.7F, 10.0F, v.tint(Art.SAKURA), b);
            break;
         }
         case "Cyber-Visier": {
            int frame = v.tint(0xFF34313E);
            // schräges Visier, oben breiter
            Art.face(m, vc, frame, b, 0.0F, -3.8F, -4.0F, -4.6F, -5.3F, -4.95F, 4.6F, -5.3F, -4.95F, 4.6F, -4.9F, -4.2F, -4.6F, -4.9F, -4.2F);
            Art.face(m, vc, frame, b, 0.0F, -3.8F, -4.0F, -4.6F, -2.4F, -4.75F, 4.6F, -2.4F, -4.75F, 4.6F, -2.8F, -4.1F, -4.6F, -2.8F, -4.1F);
            Art.bevel(m, vc, -4.6F, -5.3F, -4.95F, 4.6F, -4.95F, -4.6F, 0.08F, frame, b);
            Art.bevel(m, vc, -4.6F, -2.75F, -4.8F, 4.6F, -2.4F, -4.45F, 0.08F, frame, b);
            for (int s = -1; s <= 1; s += 2) {
               Art.bevel(m, vc, s * 4.15F, -5.4F, -4.95F, s * 4.75F, -2.3F, 0.3F, 0.15F, frame, b);
               Art.frustum(m, vc, s * 4.85F, -1.6F, -4.6F, -3.0F, 0.9F, 0.9F, 0.7F, 0.7F, 6, 0.0F, Art.SILVER, b, true, true);
            }
            break;
         }
         case "Augenklappe": {
            int leather = v.tint(0xFF231F26);
            lens(m, vc, -2.1F, -3.75F, 1.7F, 1.35F, 8, 22.5F, leather, Art.bright(leather, 1.45F), 0.18F, b);
            strap(m, vc, -6.2F, -5.6F, 4.35F, leather, b);
            Art.limb(m, vc, -3.6F, -4.6F, -4.45F, -4.35F, -5.8F, -3.6F, 0.15F, 0.15F, 4, leather, b);
            Art.limb(m, vc, -0.6F, -4.6F, -4.45F, 1.0F, -5.8F, -4.4F, 0.15F, 0.15F, 4, leather, b);
            Art.gem(m, vc, -2.1F, -3.75F, -4.75F, 0.38F, 0.25F, 0.25F, 4, 45.0F, Art.GOLD, b);
            break;
         }
         case "Ninja-Maske": {
            int cloth = v.tint(0xFF262331);
            int fold = Art.bright(cloth, 0.82F);
            Art.bevel(m, vc, -4.35F, -2.7F, -4.4F, 4.35F, 0.15F, 4.4F, 0.3F, cloth, b);
            for (int i = 0; i < 3; i++) {
               float y = -2.2F + i * 0.85F;
               Art.box(m, vc, -4.4F, y, -4.45F, 4.4F, y + 0.18F, -4.38F, fold, b);
            }

            Art.bevel(m, vc, -4.35F, -6.5F, -4.4F, 4.35F, -5.2F, 4.4F, 0.2F, cloth, b);
            Art.bevel(m, vc, -0.9F, -6.6F, 4.3F, 0.9F, -5.1F, 5.0F, 0.25F, fold, b);
            float flow = Mth.sin(t * 3.0F) * 8.0F;
            for (int k = -1; k <= 1; k += 2) {
               m.pushPose();
               m.translate(k * 0.5F, -5.8F, 4.95F);
               m.mulPose(Axis.XP.rotationDegrees(40.0F + flow + k * 7.0F));
               m.mulPose(Axis.ZP.rotationDegrees(k * 12.0F));
               float w = Mth.sin(t * 5.0F + k) * 0.35F;
               Art.sheet(m, vc, cloth, b, -0.5F, 0.0F, 0.0F, 0.5F, 0.0F, 0.0F, 0.5F + w, 2.5F, 0.1F, -0.5F + w, 2.5F, 0.1F);
               Art.sheet(m, vc, cloth, b, -0.5F + w, 2.5F, 0.1F, 0.5F + w, 2.5F, 0.1F, 0.35F - w, 4.8F, -0.1F, -0.45F - w, 5.0F, -0.1F);
               m.popPose();
            }
            break;
         }
         case "Schnurrbart": {
            int hair = v.tint(0xFF4A3024);
            for (int s = -1; s <= 1; s += 2) {
               float[][] p = {{s * 0.2F, -2.25F, -4.45F, 0.55F}, {s * 1.4F, -2.15F, -4.5F, 0.6F}, {s * 2.6F, -1.8F, -4.45F, 0.45F}, {s * 3.4F, -2.2F, -4.35F, 0.32F}, {s * 3.6F, -2.9F, -4.3F, 0.2F}, {s * 3.25F, -3.2F, -4.3F, 0.0F}};
               for (int i = 0; i < p.length - 1; i++) {
                  Art.limb(m, vc, p[i][0], p[i][1], p[i][2], p[i + 1][0], p[i + 1][1], p[i + 1][2], p[i][3], p[i + 1][3], 6, hair, b);
               }
            }
            break;
         }
         case "Bart": {
            int hair = v.tint(0xFF6A4630);
            int dark = Art.bright(hair, 0.82F);
            Art.bevel(m, vc, -4.25F, -2.7F, -4.55F, 4.25F, 0.6F, -3.9F, 0.35F, hair, b);
            Art.taper(m, vc, 0.0F, 0.3F, -4.15F, 2.2F, 0.0F, 2.6F, -4.35F, 0.25F, 6, 0.0F, dark, b, true, true);
            Art.bevel(m, vc, -4.3F, -4.4F, -4.3F, -3.85F, -2.4F, -1.8F, 0.12F, hair, b);
            Art.bevel(m, vc, 3.85F, -4.4F, -4.3F, 4.3F, -2.4F, -1.8F, 0.12F, hair, b);
            // Schnurrbart und Mund
            for (int s = -1; s <= 1; s += 2) {
               Art.limb(m, vc, s * 0.3F, -2.3F, -4.65F, s * 2.6F, -1.8F, -4.6F, 0.5F, 0.25F, 5, dark, b);
            }

            Art.box(m, vc, -1.3F, -1.45F, -4.62F, 1.3F, -1.0F, -4.54F, 0xFF8A4A4E, b);
            break;
         }
         case "Clownsnase":
            Art.sphere(m, vc, 0.0F, -1.9F, -4.85F, 1.05F, 1.0F, 1.0F, 4, 8, v.tint(0xFFE6475A), Math.max(b, 0.85F));
            Art.sphere(m, vc, -0.35F, -2.35F, -5.55F, 0.28F, 0.22F, 0.15F, 2, 5, 0xFFFFFFFF, 1.0F);
            break;
         case "Skimaske": {
            int knit = v.tint(0xFFD45A6A);
            int rib = Art.bright(knit, 0.85F);
            Art.bevel(m, vc, -4.4F, -8.45F, -4.4F, 4.4F, -4.6F, 4.4F, 0.35F, knit, b);
            Art.bevel(m, vc, -4.4F, -2.8F, -4.4F, 4.4F, 0.25F, 4.4F, 0.3F, knit, b);
            Art.box(m, vc, -4.4F, -4.6F, -2.0F, 4.4F, -2.8F, 4.4F, knit, b);
            Art.box(m, vc, -4.4F, -4.6F, -4.4F, -3.6F, -2.8F, -2.0F, knit, b);
            Art.box(m, vc, 3.6F, -4.6F, -4.4F, 4.4F, -2.8F, -2.0F, knit, b);
            Art.box(m, vc, -0.6F, -4.6F, -4.4F, 0.6F, -2.8F, -2.0F, knit, b);
            for (int i = -3; i <= 3; i++) {
               Art.box(m, vc, i * 1.15F - 0.15F, -8.2F, -4.47F, i * 1.15F + 0.15F, -4.9F, -4.4F, rib, b);
               Art.box(m, vc, i * 1.15F - 0.15F, -2.5F, -4.47F, i * 1.15F + 0.15F, 0.0F, -4.4F, rib, b);
            }

            Art.sphere(m, vc, 0.0F, -9.6F, 0.0F, 1.5F, 1.4F, 1.5F, 3, 7, 0xFFF8F3EE, b);
            break;
         }
         case "Taucherbrille": {
            int rubber = v.tint(0xFF4DA9CF);
            Art.bevel(m, vc, -4.2F, -5.8F, -5.1F, 4.2F, -1.7F, -4.1F, 0.45F, rubber, b);
            Art.bevel(m, vc, -3.5F, -5.1F, -5.25F, 3.5F, -2.4F, -5.0F, 0.2F, 0xFF20323E, b);
            Art.face(m, vc, rubber, b, 0.0F, -2.0F, -4.6F, -0.9F, -1.7F, -5.15F, 0.9F, -1.7F, -5.15F, 0.0F, -2.6F, -5.3F);
            strap(m, vc, -4.4F, -3.4F, 4.35F, rubber, b);
            // Schnorchel an der rechten Seite
            int tube = 0xFFF0D46A;
            Art.limb(m, vc, 4.5F, -0.6F, -3.6F, 4.6F, -3.2F, -3.0F, 0.42F, 0.42F, 6, tube, b);
            Art.limb(m, vc, 4.6F, -3.2F, -3.0F, 4.9F, -9.5F, -2.0F, 0.42F, 0.42F, 6, tube, b);
            Art.limb(m, vc, 4.9F, -9.5F, -2.0F, 4.6F, -10.6F, -2.6F, 0.42F, 0.3F, 6, Art.bright(tube, 0.85F), b);
            Art.limb(m, vc, 4.5F, -0.6F, -3.6F, 1.0F, -0.8F, -4.6F, 0.35F, 0.35F, 6, tube, b);
            break;
         }
         default: {
            // Sonnenbrille: achteckige Gläser, Doppelsteg
            int frame = v.tint(0xFF2A2730);
            for (int s = -1; s <= 1; s += 2) {
               lens(m, vc, s * 2.2F, -3.75F, 1.75F, 1.3F, 8, 22.5F, 0xFF242638, frame, 0.3F, b);
            }

            Art.limb(m, vc, -0.55F, -4.7F, -4.45F, 0.55F, -4.7F, -4.45F, 0.12F, 0.12F, 4, frame, b);
            Art.limb(m, vc, -0.5F, -4.2F, -4.45F, 0.5F, -4.2F, -4.45F, 0.1F, 0.1F, 4, frame, b);
            arms(m, vc, frame, b);
         }
      }
   }

   private void glow(PoseStack m, VertexConsumer vc, String st, CosmeticValue v, float t) {
      switch (st) {
         case "Sonnenbrille": {
            float g = frac(t * 0.25F);
            float x = -3.9F + g * 7.8F;
            Shapes.glow(m, vc, x - 0.3F, -4.95F, -4.68F, x + 0.3F, -2.55F, -4.6F, 0xFFFFE4EE, 0.4F * Mth.sin(g * (float)Math.PI));
            break;
         }
         case "Monokel":
            Shapes.glow(m, vc, 1.0F, -4.8F, -4.62F, 3.2F, -2.6F, -4.55F, 0xFFB8E8FF, 0.15F);
            Art.glowSpark(m, vc, 1.5F, -4.3F, -4.65F, 0.3F, 0xFFFFFFFF, 0.5F);
            break;
         case "VR-Brille": {
            float scan = frac(t * 0.6F);
            int col = Shapes.hsv(0.9F + v.hue() / 360.0F + Mth.sin(t * 0.4F) * 0.06F, 0.45F, 1.0F);
            Shapes.glow(m, vc, -3.8F, -5.2F, -6.42F, 3.8F, -2.6F, -6.36F, col, 0.25F);
            Shapes.glow(m, vc, -3.8F, -5.2F + scan * 2.4F, -6.46F, 3.8F, -4.95F + scan * 2.4F, -6.4F, 0xFFFFFFFF, 0.5F);
            break;
         }
         case "Cyber-Visier": {
            int col = v.tint(0xFFFF4A86);
            Shapes.glow(m, vc, -4.4F, -4.9F, -4.75F, 4.4F, -2.8F, -4.35F, col, 0.4F);
            float x = Mth.sin(t * 2.6F) * 3.6F;
            Shapes.glow(m, vc, x - 0.6F, -4.6F, -4.85F, x + 0.6F, -3.1F, -4.7F, 0xFFFFFFFF, 0.75F);
            for (int s = -1; s <= 1; s += 2) {
               Art.glowSpark(m, vc, s * 4.85F, -2.3F, -1.6F, 0.35F, col, 0.6F + 0.3F * Mth.sin(t * 4.0F + s));
            }
            break;
         }
         case "Taucherbrille":
            Shapes.glow(m, vc, -3.4F, -5.0F, -5.3F, 3.4F, -2.5F, -5.25F, 0xFF9AE0FF, 0.18F);
            break;
         case "3D-Brille":
            Shapes.glow(m, vc, -3.5F, -4.55F, -4.66F, -0.7F, -3.0F, -4.62F, 0xFFFF5A70, 0.12F);
            Shapes.glow(m, vc, 0.7F, -4.55F, -4.66F, 3.5F, -3.0F, -4.62F, 0xFF5AE6FF, 0.12F);
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
