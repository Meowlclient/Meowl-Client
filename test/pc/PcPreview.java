import com.lennart.nebula.afk.pc.*;
import com.lennart.nebula.afk.space.Tex;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/** Offline-Vorschau des PC-Zimmers und des Spiels: java PcPreview.java <client.jar> <out-dir> [Stil-Anfang] */
public class PcPreview {
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

   static void save(Tex t, File f) throws Exception {
      BufferedImage img = new BufferedImage(t.w, t.h, BufferedImage.TYPE_INT_ARGB);
      img.setRGB(0, 0, t.w, t.h, t.px, 0, t.w);
      ImageIO.write(img, "png", f);
   }

   public static void main(String[] a) throws Exception {
      ZipFile z = new ZipFile(a[0]);
      File out = new File(a[1]);
      String only = a.length > 2 ? a[2] : null;
      Map<String, Tex> base = load(z, List.of("font/ascii"));
      PcFont font = new PcFont(base.get("font/ascii"));
      Map<String, Tex> gt = load(z, PcGame.textures());
      PcHud hud = new PcHud(font, gt);
      PcGame game = new PcGame(gt);
      Tex gameTex = null;
      for (int k = 0; k < 4; k++) {
         int n = 0; do { game.update(0.05F); n++; } while (n < 40 || (!game.hasTarget() && n < 2000));
         PcGame.Snapshot snap = game.snapshot();
         gameTex = new Tex(PcGame.W, PcGame.H, game.render(snap));
         hud.draw(gameTex, game, snap);
         save(gameTex, new File(out, "game" + k + ".png"));
      }
      game.debugEnemyAhead(3.5F, true, false);
      save(new Tex(PcGame.W, PcGame.H, game.render(game.snapshot())), new File(out, "close1.png"));
      game.debugEnemyAhead(3.0F, false, true);
      save(new Tex(PcGame.W, PcGame.H, game.render(game.snapshot())), new File(out, "close2.png"));
      for (String th : PcScene.THEMES) {
         if (only != null && !th.startsWith(only)) continue;
         PcTheme theme = PcTheme.of(th);
         Map<String, Tex> tex = load(z, theme.textures());
         long t0 = System.currentTimeMillis();
         PcRoom room = new PcRoom(tex, theme);
         room.render();
         System.out.println(th + ": " + (System.currentTimeMillis() - t0) + " ms");
         Tex img = new Tex(PcRoom.VW, PcRoom.VH, room.image.px.clone());
         room.paintScreen(img, PcRoom.SCREEN_LEFT, PcScreens.discord(font), 1.0F);
         room.paintScreen(img, PcRoom.SCREEN_RIGHT, PcScreens.spotify(font, "Blinding Lights", "The Weeknd", true, null), 1.0F);
         room.paintScreen(img, PcRoom.SCREEN_MID, gameTex, 1.0F);
         BufferedImage bi = new BufferedImage(img.w, img.h, BufferedImage.TYPE_INT_ARGB);
         bi.setRGB(0, 0, img.w, img.h, img.px, 0, img.w);
         java.awt.Graphics2D g2 = bi.createGraphics();
         BufferedImage skin = ImageIO.read(z.getInputStream(z.getEntry("assets/minecraft/textures/entity/player/wide/steve.png")));
         for (float[] q : PcScene.armFaces(false, 1.0F)) {
            int w = (int)q[8], h = (int)q[9];
            java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform((q[2] - q[0]) / w, (q[3] - q[1]) / w, (q[4] - q[0]) / h, (q[5] - q[1]) / h, q[0], q[1]);
            BufferedImage part = skin.getSubimage((int)q[6], (int)q[7], w, h);
            BufferedImage shaded = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int yy = 0; yy < h; yy++) for (int xx = 0; xx < w; xx++) { int c = part.getRGB(xx, yy); int a2 = c >>> 24; int r = Math.round((c >> 16 & 255) * q[10]), gg = Math.round((c >> 8 & 255) * q[10]), bb = Math.round((c & 255) * q[10]); shaded.setRGB(xx, yy, a2 << 24 | r << 16 | gg << 8 | bb); }
            g2.drawImage(shaded, at, null);
         }
         g2.dispose();
         ImageIO.write(bi, "png", new File(out, "room_" + th.split(" ")[0] + ".png"));
      }
   }
}
