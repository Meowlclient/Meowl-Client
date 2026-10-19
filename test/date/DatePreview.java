import com.lennart.nebula.afk.date.DateTracer;
import com.lennart.nebula.afk.space.Tex;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/** Offline-Vorschau der E-Date-Szene: java -cp build\classes\java\main test\date\DatePreview.java <client.jar> <out> */
public class DatePreview {
   public static void main(String[] a) throws Exception {
      ZipFile z = new ZipFile(a[0]);
      Map<String, Tex> m = new HashMap<>();
      for (String p : DateTracer.TEXTURES) {
         ZipEntry e = z.getEntry("assets/minecraft/textures/" + p + ".png");
         if (e == null) { System.out.println("fehlt: " + p); continue; }
         BufferedImage img = ImageIO.read(z.getInputStream(e));
         Tex t = new Tex(img.getWidth(), img.getHeight());
         img.getRGB(0, 0, t.w, t.h, t.px, 0, t.w);
         m.put(p, t);
      }
      long t0 = System.currentTimeMillis();
      DateTracer tr = new DateTracer(m);
      tr.render();
      System.out.println("Render: " + (System.currentTimeMillis() - t0) + " ms, Sitz " + Arrays.toString(tr.seat) + " Horizont " + DateTracer.horizonY());
      File out = new File(a[1]);
      out.mkdirs();
      for (int k = 0; k < 2; k++) {
         Tex t = k == 0 ? tr.dawn : tr.golden;
         int zen = k == 0 ? 0x2A2C5C : 0x4C76C8, hor = k == 0 ? 0xF88C6A : 0xFFC88C;
         BufferedImage img = new BufferedImage(DateTracer.VW, DateTracer.VH, BufferedImage.TYPE_INT_ARGB);
         Graphics2D g = img.createGraphics();
         float hy = DateTracer.horizonY();
         for (int y = 0; y < DateTracer.VH; y++) {
            float f = Math.min(1, Math.max(0, y / hy));
            f = f * f;
            int r = (int)((zen >> 16 & 255) + ((hor >> 16 & 255) - (zen >> 16 & 255)) * f);
            int gg = (int)((zen >> 8 & 255) + ((hor >> 8 & 255) - (zen >> 8 & 255)) * f);
            int b = (int)((zen & 255) + ((hor & 255) - (zen & 255)) * f);
            g.setColor(new Color(r, gg, b));
            g.fillRect(0, y, DateTracer.VW, 1);
         }
         int sunY = (int)(hy - (k == 0 ? 6 : 60));
         g.setColor(new Color(255, 236, 170));
         g.fillRect(560 - 22, sunY - 22, 44, 44);
         BufferedImage terr = new BufferedImage(t.w, t.h, BufferedImage.TYPE_INT_ARGB);
         terr.setRGB(0, 0, t.w, t.h, t.px, 0, t.w);
         g.drawImage(terr, 0, 0, null);
         g.setColor(Color.MAGENTA);
         g.fillRect((int)tr.seat[0] - 2, (int)tr.seat[2] - 2, 4, 4);
         g.fillRect((int)tr.seat[1] - 2, (int)tr.seat[2] - 2, 4, 4);
         g.dispose();
         ImageIO.write(img, "png", new File(out, k == 0 ? "dawn.png" : "golden.png"));
      }
   }
}
