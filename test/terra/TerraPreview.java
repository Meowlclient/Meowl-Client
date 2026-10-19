import com.lennart.nebula.afk.space.Tex;
import com.lennart.nebula.afk.terra.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.zip.*;
import javax.imageio.ImageIO;

/** Offline-Vorschau der Terraria-Szene: java TerraPreview.java <client.jar> <out-dir> */
public class TerraPreview {
   static Map<String, Tex> load(ZipFile z, Collection<String> paths) throws Exception {
      Map<String, Tex> m = new HashMap<>();
      for (String p : paths) {
         ZipEntry e = z.getEntry("assets/minecraft/textures/" + p + ".png");
         if (e == null) { System.out.println("fehlt: " + p); continue; }
         BufferedImage img = ImageIO.read(z.getInputStream(e));
         Tex t = new Tex(img.getWidth(), img.getHeight());
         img.getRGB(0, 0, t.w, t.h, t.px, 0, t.w);
         m.put(p, t);
      }
      return m;
   }

   static BufferedImage img(Tex t) {
      BufferedImage b = new BufferedImage(t.w, t.h, BufferedImage.TYPE_INT_ARGB);
      b.setRGB(0, 0, t.w, t.h, t.px, 0, t.w);
      return b;
   }

   static class J2D implements TerraCanvas {
      final Graphics2D g;
      final Map<Integer, BufferedImage> tex = new HashMap<>();
      final Font font = new Font(Font.MONOSPACED, Font.BOLD, 8);
      int quads;
      float[] probe;

      J2D(Graphics2D g) { this.g = g; }

      public void fill(float x1, float y1, float x2, float y2, int argb) {
         if (x2 <= x1 || y2 <= y1 || argb >>> 24 == 0) return;
         g.setColor(new Color(argb, true));
         g.fill(new java.awt.geom.Rectangle2D.Float(x1, y1, x2 - x1, y2 - y1));
      }

      public void quad(int t, float p0x, float p0y, float p1x, float p1y, float p3x, float p3y, int u, int v, int uw, int vh, int tint) {
         BufferedImage src = tex.get(t);
         if (src == null || tint >>> 24 == 0) return;
         quads++;
         if (probe != null) {
            try {
               AffineTransform inv = new AffineTransform((p1x - p0x) / uw, (p1y - p0y) / uw, (p3x - p0x) / vh, (p3y - p0y) / vh, p0x, p0y).createInverse();
               java.awt.geom.Point2D q = inv.transform(new java.awt.geom.Point2D.Float(probe[0], probe[1]), null);
               if (q.getX() >= 0 && q.getY() >= 0 && q.getX() < uw && q.getY() < vh) System.out.printf("  probe tex=%d uv=%d,%d %dx%d tint=%08x p0=%.1f,%.1f p1=%.1f,%.1f p3=%.1f,%.1f%n", t, u, v, uw, vh, tint, p0x, p0y, p1x, p1y, p3x, p3y);
            } catch (Exception e) {}
         }
         int aw = Math.abs(uw), ah = Math.abs(vh);
         BufferedImage sub = new BufferedImage(aw, ah, BufferedImage.TYPE_INT_ARGB);
         float ta = (tint >>> 24) / 255f, tr = (tint >> 16 & 255) / 255f, tg = (tint >> 8 & 255) / 255f, tb = (tint & 255) / 255f;
         for (int y = 0; y < ah; y++) for (int x = 0; x < aw; x++) {
            int sx = uw < 0 ? u - 1 - x : u + x, sy = vh < 0 ? v - 1 - y : v + y;
            if (sx < 0 || sy < 0 || sx >= src.getWidth() || sy >= src.getHeight()) continue;
            int c = src.getRGB(sx, sy);
            int a = Math.round((c >>> 24) * ta), r = Math.round((c >> 16 & 255) * tr), gg = Math.round((c >> 8 & 255) * tg), b = Math.round((c & 255) * tb);
            sub.setRGB(x, y, a << 24 | r << 16 | gg << 8 | b);
         }
         AffineTransform at = new AffineTransform((p1x - p0x) / uw, (p1y - p0y) / uw, (p3x - p0x) / vh, (p3y - p0y) / vh, p0x, p0y);
         g.drawImage(sub, at, null);
      }

      public void text(String s, float x, float y, float scale, int argb, boolean shadow) {
         AffineTransform old = g.getTransform();
         g.translate(x, y);
         g.scale(scale, scale);
         g.setFont(font);
         if (shadow) { g.setColor(new Color(0x3F3F3F | (argb & 0xFF000000), true)); g.drawString(s, 1, 8); }
         g.setColor(new Color(argb, true));
         g.drawString(s, 0, 7);
         g.setTransform(old);
      }

      public int textWidth(String s) { return s.length() * 5; }
   }

   public static void main(String[] a) throws Exception {
      ZipFile z = new ZipFile(a[0]);
      File out = new File(a[1]);
      out.mkdirs();
      Map<String, Tex> tex = load(z, TerraTiles.textures());
      long t0 = System.currentTimeMillis();
      TerraAtlas atlas = new TerraAtlas(tex);
      TerraBackdrop bd = new TerraBackdrop(tex, 42L);
      System.out.println("Atlas+Hintergrund: " + (System.currentTimeMillis() - t0) + " ms, fehlt: " + atlas.missing());
      ImageIO.write(img(atlas.image), "png", new File(out, "atlas.png"));
      BufferedImage skin = ImageIO.read(z.getInputStream(z.getEntry("assets/minecraft/textures/entity/player/wide/steve.png")));
      long seed = a.length > 2 ? Long.parseLong(a[2]) : 7L;
      // Szenen: {Startzeit, Sekunden vorlaufen, Name}
      Object[][] shots = {
         {0, 3f, "morgen"}, {0, 28f, "laufen"}, {0, 75f, "graben"}, {0, 95f, "tunnel"}, {1, 30f, "abend"}, {2, 15f, "nacht"}, {2, 32f, "boss"}, {2, 52f, "boss2"}
      };
      for (Object[] s : shots) {
         TerraSim sim = new TerraSim(seed);
         sim.startAt((Integer)s[0]);
         if (s[2].equals("graben") || s[2].equals("tunnel")) forceDig(sim);
         TerraView view = new TerraView(atlas);
         int W = 1920, H = 1080;
         BufferedImage frame = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
         Graphics2D g = frame.createGraphics();
         g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
         g.scale(2, 2);
         J2D c = new J2D(g);
         c.tex.put(TerraCanvas.ATLAS, img(atlas.image));
         c.tex.put(TerraCanvas.SKIN, skin);
         c.tex.put(TerraCanvas.BG_FAR, img(bd.far));
         for (int b = 0; b < TerraWorld.BIOMES; b++) { c.tex.put(16 + b * 2, img(bd.mid[b])); c.tex.put(17 + b * 2, img(bd.near[b])); }
         float secs = (Float)s[1];
         long t1 = System.currentTimeMillis();
         for (float t = 0; t < secs; t += 1 / 30f) {
            sim.update(1 / 30f);
            if (t > secs - 3f) {
               // Kamera einschwingen lassen (ohne Bild)
               view.render(new J2D(g) { public void quad(int t2, float a1, float b1, float c1, float d1, float e1, float f1, int u, int v, int uw, int vh, int tint) {} public void fill(float x1, float y1, float x2, float y2, int argb) {} public void text(String s2, float x, float y, float sc, int argb, boolean sh) {} }, 960, 540, 2f, sim, 1 / 30f, true, 0f, true);
            }
         }
         long t2 = System.currentTimeMillis();
         view.render(c, 960, 540, 2f, sim, 1 / 30f, true, 0f, true);
         c.tex.put(TerraCanvas.MAP, img(view.map));
         g.setTransform(new AffineTransform());
         g.scale(2, 2);
         if (System.getenv("PROBE") != null && s[2].equals("morgen")) {
            String[] pp = System.getenv("PROBE").split(",");
            c.probe = new float[]{Float.parseFloat(pp[0]), Float.parseFloat(pp[1])};
         }
         view.render(c, 960, 540, 2f, sim, 0f, true, 0f, true);
         c.probe = null;
         g.dispose();
         ImageIO.write(frame, "png", new File(out, s[2] + ".png"));
         System.out.printf("%s: sim %d ms, frame %d ms, quads %d, held x=%.1f y=%.1f mode=%d mobs=%d boss=%s%n", s[2], t2 - t1, System.currentTimeMillis() - t2, c.quads / 2, sim.hero.x, sim.hero.y, sim.hero.mode, sim.mobs.size(), sim.boss != null);
      }
   }

   /** Graben sofort starten (für die Vorschau). */
   static void forceDig(TerraSim sim) throws Exception {
      java.lang.reflect.Field f = TerraSim.Hero.class.getDeclaredField("nextDig");
      f.setAccessible(true);
      f.setFloat(sim.hero, 40f);
   }
}
