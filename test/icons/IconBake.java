import com.lennart.nebula.plasma.PlasmaSword;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Backt die Hotbar-Symbole der Schwerter aus den echten 3D-Modellen (diagonal wie Minecraft-Schwerter, 32x32 mit
 * Transparenz, 4-fach geglättet). Aufruf: IconBake <ausgabeordner>
 */
public class IconBake {
   static int OUT = 32;
   static final int SS = 4;

   public static void main(String[] a) throws Exception {
      File dir = new File(a[0]);
      ONLY = a.length > 2 ? a[2] : null;
      for (String sz : (a.length > 1 ? a[1] : "16,32,48,64").split(",")) {
      OUT = Integer.parseInt(sz);
      String suf = "_" + OUT;
      dir.mkdirs();
      Method kar = m("karambit", Matrix4f.class, VertexConsumer.class, PlasmaSword.Look.class, float.class, boolean.class);
      Method kat = m("katana", Matrix4f.class, VertexConsumer.class, PlasmaSword.Look.class, float.class, boolean.class);
      Method bf = m("butterfly", Matrix4f.class, VertexConsumer.class, PlasmaSword.Look.class, float.class, float.class, float.class, boolean.class);
      Method hilt = m("hilt", Matrix4f.class, VertexConsumer.class, PlasmaSword.Look.class);
      for (int f = 0; f < PlasmaSword.FINISHES.length; f++) {
         PlasmaSword.Look look = new PlasmaSword.Look(0xB040FF, 1.0F, 0.95F, 1.0F, 0.0F, false, false, PlasmaSword.KARAMBIT, f);
         int ff = f;
         bake(new File(dir, "karambit_" + f + suf + ".png"), (mat, vc) -> kar.invoke(null, mat, vc, look, 1.0F, false), null);
         PlasmaSword.Look lb = new PlasmaSword.Look(0xB040FF, 1.0F, 0.95F, 1.0F, 0.0F, false, false, PlasmaSword.BUTTERFLY, ff);
         bake(new File(dir, "butterfly_" + f + suf + ".png"), (mat, vc) -> bf.invoke(null, mat, vc, lb, 1.0F, 180.0F, 180.0F, false), null);
      }

      PlasmaSword.Look lk = new PlasmaSword.Look(0xB040FF, 1.0F, 0.95F, 1.0F, 0.0F, false, false, PlasmaSword.KATANA, 0);
      bake(new File(dir, "katana" + suf + ".png"), (mat, vc) -> kat.invoke(null, mat, vc, lk, 1.0F, false), null);
      // Lichtschwert: Griff und (weiße, später eingefärbte) Klinge getrennt
      PlasmaSword.Look ls = new PlasmaSword.Look(0xFFFFFF, 1.0F, 0.95F, 0.0F, 0.0F, false, false, PlasmaSword.SABER, 0);
      Draw whole = (mat, vc) -> { hilt.invoke(null, mat, vc, ls); box(mat, vc, 0.022F, 0.2F, 1.15F, 0xFFFFFFFF); };
      bake(new File(dir, "saber_hilt" + suf + ".png"), (mat, vc) -> hilt.invoke(null, mat, vc, ls), whole);
      bake(new File(dir, "saber_blade" + suf + ".png"), (mat, vc) -> box(mat, vc, 0.022F, 0.2F, 1.15F, 0xFFFFFFFF), whole);
      }
   }

   interface Draw {
      void draw(Matrix4f mat, VertexConsumer vc) throws Exception;
   }

   static Method m(String name, Class<?>... types) throws Exception {
      Method x = PlasmaSword.class.getDeclaredMethod(name, types);
      x.setAccessible(true);
      return x;
   }

   static void box(Matrix4f m, VertexConsumer vc, float hw, float y1, float y2, int c) {
      float[][] f = {
         {-hw, y1, -hw, hw, y1, -hw, hw, y2, -hw, -hw, y2, -hw}, {-hw, y1, hw, -hw, y2, hw, hw, y2, hw, hw, y1, hw},
         {-hw, y1, -hw, -hw, y2, -hw, -hw, y2, hw, -hw, y1, hw}, {hw, y1, -hw, hw, y1, hw, hw, y2, hw, hw, y2, -hw}
      };
      for (float[] q : f) {
         for (int i = 0; i < 4; i++) {
            vc.addVertex(m, q[i * 3], q[i * 3 + 1], q[i * 3 + 2]).setColor(c);
         }
      }
   }

   /** Messer-Raum: y = Klinge, z = Breite, x = Dicke → im Bild diagonal (unten links nach oben rechts), Fläche zur Kamera. */
   static String ONLY = null;

   static void bake(File out, Draw d, Draw bounds) throws Exception {
      if (ONLY != null && !out.getName().startsWith(ONLY)) {
         return;
      }

      WingsPreview.Rec rec = new WingsPreview.Rec();
      Vector3f y = new Vector3f(1, 1, 0).normalize();
      Vector3f z = new Vector3f(1, -1, 0).normalize();
      Vector3f x = new Vector3f(y).cross(z).normalize();
      Quaternionf q = new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
      Matrix4f mat = new Matrix4f().rotate(q);
      d.draw(mat, rec);
      List<float[]> v = rec.v;
      List<float[]> bv = v;
      if (bounds != null) {
         WingsPreview.Rec br = new WingsPreview.Rec();
         bounds.draw(mat, br);
         bv = br.v;
      }

      float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
      for (float[] p : bv) {
         minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
         minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
      }

      if (System.getenv("DBG") != null) System.out.println(out.getName() + " " + v.size() + " " + minX + " " + maxX + " " + minY + " " + maxY);
      int R = OUT * SS;
      float margin = 1.0F * SS;
      float size = Math.max(maxX - minX, maxY - minY);
      float k = (R - 2 * margin) / size;
      float ox = margin + ((R - 2 * margin) - (maxX - minX) * k) / 2 - minX * k;
      float oy = margin + ((R - 2 * margin) - (maxY - minY) * k) / 2 - minY * k;
      int[] col = new int[R * R];
      float[] zb = new float[R * R];
      java.util.Arrays.fill(zb, -Float.MAX_VALUE);
      for (int i = 0; i + 3 < v.size(); i += 4) {
         float[][] p = new float[4][];
         for (int j = 0; j < 4; j++) {
            float[] s = v.get(i + j);
            p[j] = new float[]{ox + s[0] * k, R - (oy + s[1] * k), s[2], s[3]};
         }

         tri(p[0], p[1], p[2], 0, col, zb, R);
         tri(p[0], p[2], p[3], 0, col, zb, R);
      }

      BufferedImage img = new BufferedImage(OUT, OUT, BufferedImage.TYPE_INT_ARGB);
      for (int py = 0; py < OUT; py++) {
         for (int px = 0; px < OUT; px++) {
            int r = 0, g = 0, b = 0, n = 0;
            for (int sy = 0; sy < SS; sy++) {
               for (int sx = 0; sx < SS; sx++) {
                  int c = col[(py * SS + sy) * R + px * SS + sx];
                  if (c != 0) {
                     r += c >> 16 & 255; g += c >> 8 & 255; b += c & 255; n++;
                  }
               }
            }

            if (n > 0) {
               int alpha = Math.min(255, n * 255 / (SS * SS) + 30);
               img.setRGB(px, py, alpha << 24 | (r / n) << 16 | (g / n) << 8 | b / n);
            }
         }
      }

      ImageIO.write(img, "png", out);
   }

   static void tri(float[] a, float[] b, float[] c, int color, int[] col, float[] zb, int R) {
      int x0 = (int)Math.max(0, Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
      int x1 = (int)Math.min(R - 1, Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
      int y0 = (int)Math.max(0, Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
      int y1 = (int)Math.min(R - 1, Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
      float area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
      if (Math.abs(area) < 1e-6) {
         return;
      }

      for (int y = y0; y <= y1; y++) {
         for (int x = x0; x <= x1; x++) {
            float px = x + 0.5F, py = y + 0.5F;
            float w0 = ((b[0] - px) * (c[1] - py) - (b[1] - py) * (c[0] - px)) / area;
            float w1 = ((c[0] - px) * (a[1] - py) - (c[1] - py) * (a[0] - px)) / area;
            float w2 = 1 - w0 - w1;
            if (w0 >= 0 && w1 >= 0 && w2 >= 0) {
               float z = w0 * a[2] + w1 * b[2] + w2 * c[2];
               int idx = y * R + x;
               if (z > zb[idx]) {
                  zb[idx] = z;
                  int ca = Float.floatToRawIntBits(a[3]), cb = Float.floatToRawIntBits(b[3]), cc = Float.floatToRawIntBits(c[3]);
                  int rr = Math.round(w0 * (ca >> 16 & 255) + w1 * (cb >> 16 & 255) + w2 * (cc >> 16 & 255));
                  int gg = Math.round(w0 * (ca >> 8 & 255) + w1 * (cb >> 8 & 255) + w2 * (cc >> 8 & 255));
                  int bb = Math.round(w0 * (ca & 255) + w1 * (cb & 255) + w2 * (cc & 255));
                  col[idx] = 0xFF000000 | Math.min(255, rr) << 16 | Math.min(255, gg) << 8 | Math.min(255, bb);
               }
            }
         }
      }
   }
}