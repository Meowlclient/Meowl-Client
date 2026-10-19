import com.lennart.nebula.util.WorldRender;
import com.lennart.nebula.wings.WingsModel;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;

/** Software-Vorschau der Flügel: Quads aufzeichnen und mit z-Puffer rastern (Leuchtteile additiv). */
public class WingsPreview {
   static final class Rec implements VertexConsumer {
      final List<float[]> v = new ArrayList<>();
      float[] cur;

      public VertexConsumer addVertex(float x, float y, float z) {
         this.cur = new float[]{x, y, z, 0};
         this.v.add(this.cur);
         return this;
      }

      public VertexConsumer setColor(int r, int g, int b, int a) {
         this.cur[3] = Float.intBitsToFloat(a << 24 | r << 16 | g << 8 | b);
         return this;
      }

      public VertexConsumer setColor(int argb) {
         this.cur[3] = Float.intBitsToFloat(argb);
         return this;
      }

      public VertexConsumer setUv(float u, float v) {
         return this;
      }

      public VertexConsumer setUv1(int u, int v) {
         return this;
      }

      public VertexConsumer setUv2(int u, int v) {
         return this;
      }

      public VertexConsumer setNormal(float x, float y, float z) {
         return this;
      }

      public VertexConsumer setLineWidth(float w) {
         return this;
      }
   }

   static float ZOOM = 1.0F;
   static float OX = 0.0F;
   static float OY = 0.0F;
   static int W = 360;
   static int H = 300;

   public static void main(String[] args) throws Exception {
      if (args.length > 1 && args[1].equals("pos")) {
         // Positions-Regler: normal, Winkel +30, Winkel -30, Neigung +25, Öffnung +25, Abstand +5
         String[] names = {"normal", "Winkel +30", "Winkel -30", "Neigung +25", "Oeffnung +25", "Abstand +5"};
         float[][] v = {{0, 0, 0, 0}, {0, 0, 0, 30}, {0, 0, 0, -30}, {0, 25, 0, 0}, {0, 0, 25, 0}, {5, 0, 0, 0}};
         BufferedImage sheet = new BufferedImage(W * names.length, H * 2, BufferedImage.TYPE_INT_RGB);
         Graphics2D g = sheet.createGraphics();
         for (int i = 0; i < names.length; i++) {
            for (int r = 0; r < 2; r++) {
               WingsModel.Spec spec = new WingsModel.Spec(WingsModel.SERAPH, 0.8F, 0.0F, 1.0F, true, false, 0.0F, v[i][0], v[i][1], v[i][2], v[i][3]);
               g.drawImage(render(spec, new WingsModel.Anim(1.3F, 0.0F, 0.0F, 0.15F, 0.0F), r == 0 ? 205.0F : 270.0F), i * W, r * H, null);
               g.setColor(Color.WHITE);
               g.drawString(names[i] + (r == 0 ? "" : " (seitlich)"), i * W + 4, r * H + 14);
            }
         }
         ImageIO.write(sheet, "png", new File(args[0]));
         return;
      }

      if (args.length > 1 && args[1].equals("elytra")) {
         elytra(args[0]);
         return;
      }

      String[] styles = WingsModel.STYLES;
      float[][] moments = {{0.0F, 0.15F, 0.0F}, {0.35F, 0.2F, 1.0F}, {0.9F, 0.6F, 1.6F}, {0.9F, 0.6F, 4.7F}, {0.2F, 1.0F, 0.6F}};
      BufferedImage sheet = new BufferedImage(W * moments.length, H * styles.length, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = sheet.createGraphics();

      for (int s = 0; s < styles.length; s++) {
         for (int m = 0; m < moments.length; m++) {
            float[] mo = moments[m];
            WingsModel.Spec spec = new WingsModel.Spec(styles[s], 0.8F, 0.0F, 1.0F);
            WingsModel.Anim anim = new WingsModel.Anim(1.3F + m * 0.37F, mo[2], mo[0], mo[1], 0.0F);
            BufferedImage img = render(spec, anim, m == 4 ? 160.0F : 205.0F);
            g.drawImage(img, m * W, s * H, null);
            g.setColor(Color.WHITE);
            g.drawString(styles[s] + (m == 0 ? " (ruhig)" : m == 1 ? " (laufen)" : m == 4 ? " (gleiten)" : m == 2 ? " (Abschlag)" : " (Aufschlag)"), m * W + 4, s * H + 14);
         }
      }

      ImageIO.write(sheet, "png", new File(args[0]));
   }

   /** Elytra-Modus und Höhe: normal, als Elytra angelegt, halb, gleitend, Höhe +4. */
   static void elytra(String out) throws Exception {
      String[] styles = {WingsModel.SERAPH, WingsModel.BUTTERFLY, WingsModel.DEMON, WingsModel.CRYSTAL, WingsModel.WEB};
      String[] names = {"normal", "Elytra angelegt", "Elytra halb", "Elytra gleiten", "Hoehe +4", "angelegt hinten", "angelegt seitlich", "gleiten hinten"};
      float[] yaws = {205, 205, 205, 205, 205, 180, 270, 180};
      BufferedImage sheet = new BufferedImage(W * names.length, H * styles.length, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = sheet.createGraphics();

      for (int s = 0; s < styles.length; s++) {
         for (int m = 0; m < names.length; m++) {
            float height = m == 4 ? 4.0F : 0.0F;
            WingsModel.Spec spec = new WingsModel.Spec(styles[s], 0.8F, 0.0F, 1.0F, true, false, height);
            int k = m == 5 || m == 6 ? 1 : (m == 7 ? 3 : m);
            float el = k >= 1 && k <= 3 ? 1.0F : 0.0F;
            float gl = k == 3 ? 1.0F : (k == 2 ? 0.5F : 0.0F);
            float open = k == 3 ? 1.0F : (k == 2 ? 0.55F : 0.15F);
            WingsModel.Anim anim = new WingsModel.Anim(1.3F, 0.4F, k == 3 ? 0.18F : 0.0F, open, 0.0F, el, gl);
            g.drawImage(render(spec, anim, yaws[m]), m * W, s * H, null);
            g.setColor(Color.WHITE);
            g.drawString(styles[s] + " (" + names[m] + ")", m * W + 4, s * H + 14);
         }
      }

      ImageIO.write(sheet, "png", new File(out));
   }

   static PoseStack base() {
      PoseStack m = new PoseStack();
      m.scale(0.0625F, 0.0625F, 0.0625F);
      return m;
   }

   static BufferedImage render(WingsModel.Spec spec, WingsModel.Anim anim, float yawDeg) {
      Rec solid = new Rec();
      Rec glow = new Rec();
      Rec body = new Rec();
      // Spieler-Körper als Bezug (Körperraum: y nach unten, Rücken +z)
      PoseStack bm = base();
      WorldRender.box(bm, body, -4, 0, -2, 4, 12, 2, 0xFF6A7A9A, 0.9F);
      WorldRender.box(bm, body, -4, -8, -4, 4, 0, 4, 0xFFD8B090, 0.9F);
      WorldRender.box(bm, body, -4, 12, -2, 0, 24, 2, 0xFF3A4A6A, 0.9F);
      WorldRender.box(bm, body, 0, 12, -2, 4, 24, 2, 0xFF3A4A6A, 0.9F);
      WorldRender.box(bm, body, -8, 0, -2, -4, 12, 2, 0xFF6A7A9A, 0.9F);
      WorldRender.box(bm, body, 4, 0, -2, 8, 12, 2, 0xFF6A7A9A, 0.9F);
      WingsModel.draw(base(), solid, false, spec, anim, 0.95F);
      WingsModel.draw(base(), glow, true, spec, anim, 1.0F);
      return renderRecs(body, solid, glow, yawDeg);
   }

   static BufferedImage renderRecs(Rec body, Rec solid, Rec glow, float yawDeg) {
      float[] zb = new float[W * H];
      java.util.Arrays.fill(zb, Float.MAX_VALUE);
      float[] rgb = new float[W * H * 3];

      for (int i = 0; i < W * H; i++) {
         rgb[i * 3] = 0.16F;
         rgb[i * 3 + 1] = 0.2F;
         rgb[i * 3 + 2] = 0.26F;
      }

      double yaw = Math.toRadians(yawDeg);
      double pitch = Math.toRadians(-14.0);
      raster(body, zb, rgb, yaw, pitch, false);
      raster(solid, zb, rgb, yaw, pitch, false);
      raster(glow, zb, rgb, yaw, pitch, true);
      BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);

      for (int i = 0; i < W * H; i++) {
         int r = Math.min(255, Math.round(rgb[i * 3] * 255.0F));
         int gg = Math.min(255, Math.round(rgb[i * 3 + 1] * 255.0F));
         int b = Math.min(255, Math.round(rgb[i * 3 + 2] * 255.0F));
         img.setRGB(i % W, i / W, r << 16 | gg << 8 | b);
      }

      return img;
   }

   static float[] view(float[] p, double yaw, double pitch) {
      double x = p[0] - OX;
      double y = p[1] - 0.2 - OY;
      double z = p[2];
      double cx = x * Math.cos(yaw) + z * Math.sin(yaw);
      double cz = -x * Math.sin(yaw) + z * Math.cos(yaw);
      double cy = y * Math.cos(pitch) - cz * Math.sin(pitch);
      cz = y * Math.sin(pitch) + cz * Math.cos(pitch);
      double d = cz + 6.0;
      double f = 520.0 * ZOOM;
      return new float[]{(float)(W / 2.0 + f * cx / d), (float)(H / 2.0 + f * cy / d), (float)d};
   }

   static void raster(Rec rec, float[] zb, float[] rgb, double yaw, double pitch, boolean add) {
      for (int q = 0; q + 3 < rec.v.size(); q += 4) {
         float[][] p = new float[4][];

         for (int k = 0; k < 4; k++) {
            p[k] = view(rec.v.get(q + k), yaw, pitch);
         }

         int col = Float.floatToRawIntBits(rec.v.get(q)[3]);
         tri(p[0], p[1], p[2], col, zb, rgb, add);
         tri(p[0], p[2], p[3], col, zb, rgb, add);
      }
   }

   static void tri(float[] a, float[] b, float[] c, int col, float[] zb, float[] rgb, boolean add) {
      int minX = (int)Math.max(0, Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
      int maxX = (int)Math.min(W - 1, Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
      int minY = (int)Math.max(0, Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
      int maxY = (int)Math.min(H - 1, Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
      float area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
      if (Math.abs(area) < 1.0E-6F) {
         return;
      }

      float al = (col >>> 24) / 255.0F;
      float cr = (col >> 16 & 255) / 255.0F;
      float cg = (col >> 8 & 255) / 255.0F;
      float cb = (col & 255) / 255.0F;

      for (int y = minY; y <= maxY; y++) {
         for (int x = minX; x <= maxX; x++) {
            float px = x + 0.5F;
            float py = y + 0.5F;
            float w0 = ((b[0] - px) * (c[1] - py) - (b[1] - py) * (c[0] - px)) / area;
            float w1 = ((c[0] - px) * (a[1] - py) - (c[1] - py) * (a[0] - px)) / area;
            float w2 = 1.0F - w0 - w1;
            if (w0 >= 0.0F && w1 >= 0.0F && w2 >= 0.0F) {
               float z = w0 * a[2] + w1 * b[2] + w2 * c[2];
               int i = y * W + x;
               if (z < zb[i]) {
                  if (add) {
                     rgb[i * 3] += cr * al;
                     rgb[i * 3 + 1] += cg * al;
                     rgb[i * 3 + 2] += cb * al;
                  } else {
                     zb[i] = z;
                     rgb[i * 3] = cr;
                     rgb[i * 3 + 1] = cg;
                     rgb[i * 3 + 2] = cb;
                  }
               }
            }
         }
      }
   }
}
