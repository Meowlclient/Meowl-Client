package com.lennart.nebula.afk.date;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.afk.pc.PcAssets;
import com.lennart.nebula.afk.space.McSpaceCanvas;
import com.lennart.nebula.afk.space.Tex;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.GuiGraphics;

/**
 * AFK-Szene „E-Date“: ihr sitzt zu zweit auf einer Decke an der Klippe, unter euch die Bucht, und die Sonne geht über
 * dem Meer auf. Landschaft einmal per Raytracing aus Minecraft-Blöcken, live dazu Himmel, aufgehende Sonne, Glitzern
 * auf dem Wasser, Wolken, Vögel, fallende Kirschblüten und kleine Herzen.
 */
public final class DateScene {
   private static final String DAWN = "date_dawn";
   private static final String GOLDEN = "date_golden";
   private static final String SUN = "date_sun";
   private static final String GLOW = "date_glow";
   private static final String CLOUDS = "date_clouds";
   private static final String HEART = "date_heart";
   /** Sekunden, bis die Sonne ganz aufgegangen ist. */
   private static final float RISE = 210.0F;
   private final McSpaceCanvas canvas = new McSpaceCanvas();
   private CompletableFuture<Built> building;
   private Built built;
   private boolean uploaded;
   private float time;
   private List<int[]> sparkles = List.of();
   private final List<float[]> hearts = new ArrayList<>();
   private float heartT = 3.0F;
   private final Random rnd = new Random();
   /** Bildschirm-Lage der beiden Sitzplätze (wird beim Zeichnen gesetzt): {x links, x rechts, y Kante, Pixel je Block}. */
   public final float[] seat = new float[4];

   private record Built(DateTracer tracer, Tex sun, Tex glow, Tex clouds, Tex heart) {
   }

   public void prepare() {
      if (this.building == null) {
         List<String> paths = new ArrayList<>(List.of(DateTracer.TEXTURES));
         paths.add("environment/celestial/sun");
         paths.add("environment/clouds");
         paths.add("particle/heart");
         Map<String, Tex> tex = PcAssets.load(paths);
         this.building = CompletableFuture.supplyAsync(() -> {
            DateTracer t = new DateTracer(tex);
            t.render();
            Tex sun = tex.get("environment/celestial/sun");
            Tex clouds = tex.get("environment/clouds");
            Tex heart = tex.get("particle/heart");
            return new Built(t, sun == null ? Tex.solid(0xFFFFF0C0) : celestial(sun), glow(), clouds == null ? new Tex(16, 16) : clouds, heart == null ? Tex.solid(0xFFFF4060) : heart);
         });
      }
   }

   public boolean ready() {
      if (this.built == null && this.building != null && this.building.isDone()) {
         try {
            this.built = this.building.join();
            this.sparkles = this.built.tracer.sparkleSpots(this.sunImageX(), 140, 5L);
         } catch (RuntimeException var2) {
            NebulaClient.LOGGER.warn("AFK-Szene E-Date konnte nicht erzeugt werden", var2);
            this.building = null;
         }
      }

      return this.built != null;
   }

   public void restart() {
      this.time = 0.0F;
      this.hearts.clear();
      this.heartT = 4.0F;
   }

   public void destroy() {
      this.canvas.destroy();
      this.uploaded = false;
   }

   // ================================================================== Bilder

   /** Sonne wird in Minecraft additiv gezeichnet: Schwarz → durchsichtig, Rand abschneiden. */
   private static Tex celestial(Tex t) {
      int x0 = t.w;
      int y0 = t.h;
      int x1 = -1;
      int y1 = -1;
      int[] px = new int[t.px.length];

      for (int y = 0; y < t.h; y++) {
         for (int x = 0; x < t.w; x++) {
            int c = t.px[y * t.w + x];
            int a = Math.max(c >> 16 & 255, Math.max(c >> 8 & 255, c & 255)) * (c >>> 24) / 255;
            px[y * t.w + x] = a << 24 | c & 0xFFFFFF;
            if (a > 24) {
               x0 = Math.min(x0, x);
               y0 = Math.min(y0, y);
               x1 = Math.max(x1, x);
               y1 = Math.max(y1, y);
            }
         }
      }

      if (x1 < x0) {
         return new Tex(t.w, t.h, px);
      }

      int size = Math.max(x1 - x0, y1 - y0) + 1;
      Tex o = new Tex(size, size);

      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            int sx = (x0 + x1) / 2 - size / 2 + x;
            int sy = (y0 + y1) / 2 - size / 2 + y;
            if (sx >= 0 && sy >= 0 && sx < t.w && sy < t.h) {
               o.px[y * size + x] = px[sy * t.w + sx];
            }
         }
      }

      return o;
   }

   private static Tex glow() {
      Tex t = new Tex(64, 64);

      for (int y = 0; y < 64; y++) {
         for (int x = 0; x < 64; x++) {
            float dx = (x + 0.5F - 32.0F) / 32.0F;
            float dy = (y + 0.5F - 32.0F) / 32.0F;
            float d = Math.max(0.0F, 1.0F - (float)Math.sqrt(dx * dx + dy * dy));
            t.set(x, y, Math.round(255 * d * d * d) << 24 | 0xFFFFFF);
         }
      }

      return t;
   }

   // ================================================================== Zeichnen

   private float rise() {
      float p = Math.min(1.0F, this.time / RISE);
      return p * p * (3.0F - 2.0F * p);
   }

   /** Sonnenmitte im Bild (x fest, y steigt). */
   private float sunImageX() {
      float[] p = new float[3];
      DateTracer.CAM.project(0.12F * 5000.0F, DateTracer.WATER_Y, 5000.0F, p);
      return p[0];
   }

   private float sunImageY(float k) {
      float[] p = new float[3];
      float elev = -0.03F + k * 0.17F;
      DateTracer.CAM.project(0.12F * 5000.0F, DateTracer.CAM.y + elev * 5000.0F, 5000.0F, p);
      return p[1];
   }

   private static int lerp(int a, int b, float t) {
      return Tex.mix(a, b, t);
   }

   /** Zeichnet die Szene; false, solange sie noch gebaut wird. */
   public boolean render(GuiGraphics ctx, int w, int h, float dt, float alpha, boolean hearts) {
      if (!this.ready()) {
         ctx.fill(0, 0, w, h, 0xFF000000);
         return false;
      }

      McSpaceCanvas c = this.canvas.bind(ctx);
      Built b = this.built;
      if (!this.uploaded) {
         c.upload(DAWN, b.tracer.dawn);
         c.upload(GOLDEN, b.tracer.golden);
         c.upload(SUN, b.sun);
         c.upload(GLOW, b.glow);
         c.upload(CLOUDS, b.clouds);
         c.upload(HEART, b.heart);
         this.uploaded = true;
      }

      this.time += dt;
      float k = this.rise();
      // Bild füllt den Bildschirm (Ränder notfalls abgeschnitten), ganz leichtes Atmen der Kamera
      float s = Math.max((float)w / DateTracer.VW, (float)h / DateTracer.VH) * (1.02F + 0.01F * (float)Math.sin(this.time * 0.07F));
      float ox = (w - DateTracer.VW * s) / 2.0F;
      float oy = (h - DateTracer.VH * s) / 2.0F + (float)Math.sin(this.time * 0.11F) * 1.5F;
      float horizon = oy + DateTracer.horizonY() * s;
      // Himmel: Verlauf von Nachtblau über Violett zu Orange am Horizont, wird heller
      int zenith = lerp(0xFF161A3C, 0xFF4E78C8, k);
      int mid = lerp(0xFF5A3A78, 0xFF9AA8D8, k);
      int hor = lerp(0xFFF0845E, 0xFFFFC890, k);
      int bands = 36;

      for (int i = 0; i < bands; i++) {
         float a0 = (float)i / bands;
         float a1 = (float)(i + 1) / bands;
         int col = a0 < 0.55F ? lerp(zenith, mid, a0 / 0.55F) : lerp(mid, hor, (a0 - 0.55F) / 0.45F);
         ctx.fill(0, Math.round(horizon * a0), w, Math.round(horizon * a1) + 1, col);
      }

      ctx.fill(0, Math.round(horizon), w, h, hor);
      // Sterne verblassen
      float stars = Math.max(0.0F, 1.0F - k * 2.5F);
      if (stars > 0.01F) {
         Random r = new Random(3L);

         for (int i = 0; i < 90; i++) {
            float sx = r.nextFloat() * w;
            float sy = r.nextFloat() * horizon * 0.6F;
            float tw = 0.5F + 0.5F * (float)Math.sin(this.time * (1.0F + r.nextFloat() * 2.0F) + i);
            ctx.fill(Math.round(sx), Math.round(sy), Math.round(sx) + 1, Math.round(sy) + 1, Math.round(200 * stars * tw) << 24 | 0xFFFFFF);
         }
      }

      // Sonne mit Schein
      float sunX = ox + this.sunImageX() * s;
      float sunY = oy + this.sunImageY(k) * s;
      float sunSize = 46.0F * s;
      int glowCol = lerp(0xFFFF7A3A, 0xFFFFD890, k);
      c.image(GLOW, sunX, sunY, sunSize * 9.0F, sunSize * 6.0F, 0, 0, 64, 64, 0.0F, false, Math.round(140 + 60 * k) << 24 | glowCol & 0xFFFFFF);
      c.image(GLOW, sunX, sunY, sunSize * 3.2F, sunSize * 3.2F, 0, 0, 64, 64, 0.0F, false, 0xD0000000 | lerp(0xFFFFA060, 0xFFFFF0C0, k) & 0xFFFFFF);
      c.image(SUN, sunX, sunY, sunSize, sunSize, 0, 0, b.sun.w, b.sun.h, 0.0F, false, 0xFF000000 | lerp(0xFFFFB070, 0xFFFFF4D8, k) & 0xFFFFFF);
      // Wolken (Minecraft-Wolken, rosa angestrahlt)
      int cloudCol = lerp(0xFFF29AA0, 0xFFFFE8D8, k);

      for (int band = 0; band < 2; band++) {
         float texel = (band == 0 ? 9.0F : 6.0F) * s;
         float by = horizon - (band == 0 ? 150.0F : 95.0F) * s;
         float span = 256 * texel;
         float scroll = this.time * (band == 0 ? 2.0F : 1.2F) * s;
         float start = -(scroll % span);

         for (float x = start; x < w; x += span) {
            c.image(CLOUDS, x + span / 2.0F, by + 4.0F * texel * 0.45F, span, 8.0F * texel * 0.45F, 0, band == 0 ? 60 : 180, 256, band == 0 ? 68 : 188, 0.0F, false, (band == 0 ? 0xB0 : 0x80) << 24 | cloudCol & 0xFFFFFF);
         }
      }

      // Landschaft: Morgendämmerung → goldene Stunde
      c.image(DAWN, w / 2.0F, oy + DateTracer.VH * s / 2.0F, DateTracer.VW * s, DateTracer.VH * s, 0, 0, DateTracer.VW, DateTracer.VH, 0.0F, false, -1);
      if (k > 0.004F) {
         c.image(GOLDEN, w / 2.0F, oy + DateTracer.VH * s / 2.0F, DateTracer.VW * s, DateTracer.VH * s, 0, 0, DateTracer.VW, DateTracer.VH, 0.0F, false, Math.round(255 * k) << 24 | 0xFFFFFF);
      }

      // Glitzern der Sonne auf dem Wasser
      float sparkle = 0.35F + 0.65F * k;

      for (int[] sp : this.sparkles) {
         float tw = (float)Math.sin(this.time * (2.0F + sp[2] % 7 * 0.4F) + sp[2]);
         if (tw > 0.55F) {
            float x = ox + sp[0] * s;
            float y = oy + sp[1] * s;
            float sz = Math.max(1.0F, s * (sp[1] > DateTracer.VH * 0.6F ? 2.0F : 1.0F));
            int a = Math.round(255 * sparkle * (tw - 0.55F) / 0.45F);
            ctx.fill(Math.round(x), Math.round(y), Math.round(x + sz * 2.0F), Math.round(y + sz), a << 24 | lerp(0xFFFFB878, 0xFFFFF4D0, k) & 0xFFFFFF);
         }
      }

      // Vögel
      for (int i = 0; i < 3; i++) {
         float t = (this.time * 0.025F + i * 0.37F) % 1.0F;
         float bx = -40.0F + t * (w + 80.0F);
         float by = horizon - (60.0F + i * 18.0F) * s + (float)Math.sin(t * 9.0F + i) * 6.0F * s;
         float flap = (float)Math.sin(this.time * 8.0F + i * 2.0F);
         int bs = Math.max(1, Math.round(s * 1.5F));
         int col = 0xC0302430;
         ctx.fill(Math.round(bx), Math.round(by), Math.round(bx) + bs, Math.round(by) + bs, col);
         ctx.fill(Math.round(bx) - bs, Math.round(by - flap * bs), Math.round(bx), Math.round(by - flap * bs) + bs, col);
         ctx.fill(Math.round(bx) + bs, Math.round(by - flap * bs), Math.round(bx) + bs * 2, Math.round(by - flap * bs) + bs, col);
         ctx.fill(Math.round(bx) - bs * 2, Math.round(by - flap * bs * 2), Math.round(bx) - bs, Math.round(by - flap * bs * 2) + bs, col);
         ctx.fill(Math.round(bx) + bs * 2, Math.round(by - flap * bs * 2), Math.round(bx) + bs * 3, Math.round(by - flap * bs * 2) + bs, col);
      }

      // Sitzplätze für die Figuren
      float[] st = b.tracer.seat;
      this.seat[0] = ox + st[0] * s;
      this.seat[1] = ox + st[1] * s;
      this.seat[2] = oy + st[2] * s;
      this.seat[3] = st[3] * s;
      return true;
   }

   /** Vordergrund über den Figuren: fallende Kirschblüten und Herzen. */
   public void renderFront(GuiGraphics ctx, int w, int h, float dt, boolean hearts) {
      if (this.built == null) {
         return;
      }

      McSpaceCanvas c = this.canvas.bind(ctx);
      Random r = new Random(11L);

      for (int i = 0; i < 26; i++) {
         float speed = 14.0F + r.nextFloat() * 16.0F;
         float x0 = r.nextFloat() * w;
         float phase = r.nextFloat() * h;
         float y = (phase + this.time * speed) % (h + 20.0F) - 10.0F;
         float x = x0 + (float)Math.sin(this.time * 0.8F + i) * 18.0F + this.time * 6.0F % w;
         x = ((x % w) + w) % w;
         int sz = 2 + r.nextInt(2);
         int col = r.nextBoolean() ? 0xE8FFB8D0 : 0xE8F590B8;
         ctx.fill(Math.round(x), Math.round(y), Math.round(x) + sz, Math.round(y) + sz - 1, col);
      }

      if (hearts) {
         this.heartT -= dt;
         if (this.heartT <= 0.0F) {
            this.heartT = 4.0F + this.rnd.nextFloat() * 5.0F;
            float mx = (this.seat[0] + this.seat[1]) / 2.0F;
            this.hearts.add(new float[]{mx + (this.rnd.nextFloat() - 0.5F) * this.seat[3] * 0.6F, this.seat[2] - this.seat[3] * 1.9F, 0.0F, this.rnd.nextFloat() * 6.0F});
         }

         float sz = Math.max(6.0F, this.seat[3] * 0.32F);

         for (int i = this.hearts.size() - 1; i >= 0; i--) {
            float[] hh = this.hearts.get(i);
            hh[2] += dt;
            if (hh[2] > 3.2F) {
               this.hearts.remove(i);
               continue;
            }

            float a = hh[2] < 0.4F ? hh[2] / 0.4F : Math.max(0.0F, 1.0F - (hh[2] - 2.2F));
            float x = hh[0] + (float)Math.sin(hh[2] * 2.2F + hh[3]) * sz * 0.4F;
            float y = hh[1] - hh[2] * sz * 1.4F;
            Built b = this.built;
            c.image(HEART, x, y, sz, sz, 0, 0, b.heart.w, b.heart.h, 0.0F, false, Math.round(Math.min(1.0F, a) * 255) << 24 | 0xFFFFFF);
         }
      }
   }
}
