import com.lennart.nebula.afk.space.*;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.io.File;
import java.util.*;
import java.util.function.Function;
import javax.imageio.ImageIO;

public class Preview {
   static final Map<String, Tex> cache = new HashMap<>();
   static Function<String, Tex> TEX = name -> cache.computeIfAbsent(name, n -> {
      try {
         BufferedImage img = ImageIO.read(new File("/tmp/mctex/assets/minecraft/textures/block/" + n + ".png"));
         Tex t = new Tex(img.getWidth(), img.getHeight());
         for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) t.px[y * t.w + x] = img.getRGB(x, y);
         return t;
      } catch (Exception e) { System.out.println("fehlt: " + n); return null; }
   });

   static void save(Tex t, String file) throws Exception {
      BufferedImage img = new BufferedImage(t.w, t.h, BufferedImage.TYPE_INT_ARGB);
      img.setRGB(0, 0, t.w, t.h, t.px, 0, t.w);
      ImageIO.write(img, "png", new File(file));
   }

   public static void main(String[] a) throws Exception {
      long t0 = System.currentTimeMillis();
      BridgeTracer b = new BridgeTracer(TEX, new float[]{1.0F, 0.72F, 0.62F});
      b.render();
      System.out.println("Brücke: " + (System.currentTimeMillis() - t0) + " ms, Lampen " + b.blinkers.size());
      PlanetPainter.Planet type = PlanetPainter.Planet.valueOf(a.length > 1 ? a[1] : "LAVA");
      t0 = System.currentTimeMillis();
      PlanetPainter pp = new PlanetPainter(type, 5L);
      pp.render();
      System.out.println("Planet: " + (System.currentTimeMillis() - t0) + " ms");
      Tex flat = new Tex(b.image.w, b.image.h);
      for (int y = 0; y < flat.h; y++) for (int x = 0; x < flat.w; x++) {
         int i = y * flat.w + x;
         int bg = pp.image.px[(y / 2) * pp.image.w + x / 2];
         flat.px[i] = Tex.mix(bg, b.image.px[i] | 0xFF000000, (b.image.px[i] >>> 24) / 255f);
      }
      save(pp.image, a[0] + "/planet.png");
      t0 = System.currentTimeMillis();
      ShipSprites ss = new ShipSprites(TEX);
      ss.bakeAll();
      System.out.println("Schiffe: " + (System.currentTimeMillis() - t0) + " ms");
      for (int k = 0; k < ShipSprites.COUNT; k++) save(ss.sprites[k].image, a[0] + "/ship" + k + ".png");
      SpaceBattle battle = new SpaceBattle(ss, 1, 3L);
      battle.prewarm(10f);
      SpaceView view = new SpaceView(ss, b.blinkers, type.light);
      int W = 960, H = 540;
      for (int frame = 0; frame < 4; frame++) {
         BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
         Graphics2D g = img.createGraphics();
         J2DCanvas c = new J2DCanvas(g);
         c.upload(SpaceView.BRIDGE, b.image); c.upload(SpaceView.WINDOW_LIGHT, b.windowLight); c.upload(SpaceView.ALARM_LIGHT, b.alarmLight);
         c.upload(SpaceView.PLANET, pp.image); c.upload(SpaceView.PLANET_GLOW, pp.glow);
         for (int k = 0; k < ShipSprites.COUNT; k++) c.upload(SpaceView.shipId(k), ss.sprites[k].image);
         for (int st = 0; st < 60; st++) battle.update(1 / 30f);
         if (frame == 2) { battle.alarm = 4f; battle.shake = 1f; battle.flash = 1f; }
         view.render(c, W, H, battle, battle.time, true, 1f);
         // Platzhalter Spieler
         float pb = view.playerBlock; if (false)
         g.fillRect(Math.round(view.playerX - pb * 0.3f), Math.round(view.playerFeetY - pb * 1.8f), Math.round(pb * 0.6f), Math.round(pb * 1.8f));
         javax.imageio.ImageIO.write(img, "png", new File(a[0] + "/frame" + frame + ".png"));
      }
      save(flat, a[0] + "/bridge.png");
      save(b.windowLight, a[0] + "/winlight.png");
   }
}
