import com.lennart.nebula.afk.valley.ValleyFight;
import com.lennart.nebula.afk.valley.ValleyTracer;
import com.lennart.nebula.afk.valley.ValleyView;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Simuliert den Kampf und speichert Einzelbilder als Kontaktabzug (Bildausschnitt 480x270 um x0,y0 oder ganzes Bild halbiert). */
public class ValleyFrames {
   public static void render(ValleyTracer v, File out, float start, int count, float step, int cols, String name, int x0, int y0) throws Exception {
      ValleyFight f = new ValleyFight(v, 7L);
      ValleyView view = new ValleyView(v);
      int W = 960;
      int H = 540;
      int rows = (count + cols - 1) / cols;
      BufferedImage sheet = new BufferedImage(480 * cols, 270 * rows, BufferedImage.TYPE_INT_RGB);
      Graphics2D sg = sheet.createGraphics();
      float time = 0.0F;
      String last = "";

      while (time < start) {
         f.update(1.0F / 30.0F);
         time += 1.0F / 30.0F;
         if (!f.moveName().equals(last)) {
            last = f.moveName();
            System.out.printf("%.1f %s%n", time, last);
         }
      }

      for (int i = 0; i < count; i++) {
         float target = start + (i + 1) * step;

         while (time < target) {
            f.update(1.0F / 30.0F);
            time += 1.0F / 30.0F;
            if (!f.moveName().equals(last)) {
               last = f.moveName();
               System.out.printf("%.1f %s%n", time, last);
            }
         }

         BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
         Graphics2D g = img.createGraphics();
         J2DCanvas c = new J2DCanvas(g);
         c.upload(ValleyView.BG, v.image);
         c.upload(ValleyView.FALL, v.fallAtlas);
         c.upload(ValleyView.RIVER, v.riverAtlas);
         view.render(c, W, H, f, time, 1.0F / 30.0F, false, 1.0F);
         g.dispose();
         int x = i % cols * 480;
         int y = i / cols * 270;
         if (x0 >= 0) {
            sg.drawImage(img, x, y, x + 480, y + 270, x0, y0, x0 + 480, y0 + 270, null);
         } else {
            sg.drawImage(img, x, y, 480, 270, null);
         }

         sg.drawString(String.format("%.1fs %s", time, f.moveName()), x + 4, y + 14);
      }

      ImageIO.write(sheet, "png", new File(out, name));
   }
}
