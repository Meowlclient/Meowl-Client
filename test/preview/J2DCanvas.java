import com.lennart.nebula.afk.space.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;

public class J2DCanvas implements SpaceCanvas {
   final Graphics2D g;
   final Map<String, BufferedImage> imgs = new HashMap<>();
   J2DCanvas(Graphics2D g) { this.g = g; g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR); }
   Color col(int argb) { return new Color(argb, true); }
   public void fill(float x1, float y1, float x2, float y2, int argb) { g.setColor(col(argb)); g.fill(new Rectangle2D.Float(x1, y1, x2 - x1, y2 - y1)); }
   public void gradient(float x1, float y1, float x2, float y2, int t, int b) { g.setPaint(new GradientPaint(0, y1, col(t), 0, y2, col(b))); g.fill(new Rectangle2D.Float(x1, y1, x2 - x1, y2 - y1)); }
   public void line(float x1, float y1, float x2, float y2, float w, int argb) {
      g.setColor(col(argb)); AffineTransform old = g.getTransform();
      g.translate(x1, y1); g.rotate(Math.atan2(y2 - y1, x2 - x1)); float len = (float)Math.hypot(x2 - x1, y2 - y1);
      g.fill(new Rectangle2D.Float(0, -w / 2, len, w)); g.setTransform(old);
   }
   public void upload(String id, Tex t) { BufferedImage i = new BufferedImage(t.w, t.h, BufferedImage.TYPE_INT_ARGB); i.setRGB(0, 0, t.w, t.h, t.px, 0, t.w); imgs.put(id, i); }
   public boolean has(String id) { return imgs.containsKey(id); }
   public void image(String id, float cx, float cy, float w, float h, float u0, float v0, float u1, float v1, float angle, boolean flip, int tint) {
      BufferedImage src = imgs.get(id); if (src == null) return;
      int iw = Math.round(u1 - u0), ih = Math.round(v1 - v0);
      BufferedImage sub = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
      float ta = (tint >>> 24) / 255f, tr = (tint >> 16 & 255) / 255f, tg = (tint >> 8 & 255) / 255f, tb = (tint & 255) / 255f;
      for (int y = 0; y < ih; y++) for (int x = 0; x < iw; x++) {
         int c = src.getRGB(Math.round(u0) + x, Math.round(v0) + y);
         int a = Math.round((c >>> 24) * ta), r = Math.round((c >> 16 & 255) * tr), gg = Math.round((c >> 8 & 255) * tg), b = Math.round((c & 255) * tb);
         sub.setRGB(x, y, a << 24 | r << 16 | gg << 8 | b);
      }
      AffineTransform old = g.getTransform();
      g.translate(cx, cy); g.rotate(angle); g.scale(w / iw * (flip ? -1 : 1), h / ih);
      g.drawImage(sub, -iw / 2, -ih / 2, null); g.setTransform(old);
   }
}
