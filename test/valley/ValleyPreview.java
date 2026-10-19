import com.lennart.nebula.afk.space.Tex;
import com.lennart.nebula.afk.valley.ValleyTracer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** Rendert das Tal des Endes ohne Spiel (Texturen direkt aus der Minecraft-Jar). Argumente: client.jar, Ausgabeordner. */
public class ValleyPreview {
   static ZipFile zip;
   static final Map<String, Tex> cache = new HashMap<>();
   static final Function<String, Tex> TEX = n -> cache.computeIfAbsent(n, k -> {
      try {
         ZipEntry e = zip.getEntry("assets/minecraft/textures/block/" + k + ".png");
         if (e == null) {
            System.out.println("fehlt: " + k);
            return null;
         }
         try (InputStream in = zip.getInputStream(e)) {
            BufferedImage img = ImageIO.read(in);
            Tex t = new Tex(img.getWidth(), img.getHeight());
            for (int y = 0; y < t.h; y++) for (int x = 0; x < t.w; x++) t.px[y * t.w + x] = img.getRGB(x, y);
            return t;
         }
      } catch (Exception ex) {
         throw new RuntimeException(ex);
      }
   });

   static void save(Tex t, File f) throws Exception {
      BufferedImage img = new BufferedImage(t.w, t.h, BufferedImage.TYPE_INT_ARGB);
      img.setRGB(0, 0, t.w, t.h, t.px, 0, t.w);
      ImageIO.write(img, "png", f);
   }

   public static void main(String[] a) throws Exception {
      zip = new ZipFile(a[0]);
      File out = new File(a[1]);
      out.mkdirs();
      long t0 = System.currentTimeMillis();
      ValleyTracer v = new ValleyTracer(TEX);
      System.out.println("Welt: " + (System.currentTimeMillis() - t0) + " ms");
      t0 = System.currentTimeMillis();
      v.render();
      System.out.println("Render: " + (System.currentTimeMillis() - t0) + " ms, Fall " + java.util.Arrays.toString(v.fallBox) + " Fluss " + java.util.Arrays.toString(v.riverBox));
      save(v.image, new File(out, "valley.png"));
      save(v.fallAtlas, new File(out, "fall_atlas.png"));
      if (a.length > 2) {
         ValleyFrames.render(v, out, Float.parseFloat(a[2]), Integer.parseInt(a[3]), Float.parseFloat(a[4]), Integer.parseInt(a[5]), a[6], Integer.parseInt(a[7]), Integer.parseInt(a[8]));
      }
   }
}
