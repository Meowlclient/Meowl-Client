package com.lennart.nebula.afk.terra;

import com.lennart.nebula.afk.space.Tex;
import java.util.HashMap;
import java.util.Map;

/**
 * Alle Bilder der Szene in einer Textur: Minecraft-Blöcke (Gras und Laub eingefärbt), Items, Skins, Himmel und eigene
 * Pixel-Art im Terraria-Stil (Schleime, Dämonenaugen, das Auge von Cthulhu, Münzen, Sterne). Reines Java.
 */
public final class TerraAtlas {
   public static final int SIZE = 1024;
   public final Tex image = new Tex(SIZE, SIZE);
   private final Map<String, int[]> regions = new HashMap<>();
   private final Map<String, Integer> averages = new HashMap<>();
   private final java.util.List<String> missing = new java.util.ArrayList<>();
   private int cx = 1;
   private int cy = 1;
   private int rowH;
   private final int[] fallback;

   public static final int GRASS_TINT = 0xFF7CBD5A;
   private static final int OAK_TINT = 0xFF5DA82F;
   private static final int BIRCH_TINT = 0xFF80A755;
   private static final int SPRUCE_TINT = 0xFF619961;
   private static final int JUNGLE_TINT = 0xFF3FB517;
   private static final int WATER_TINT = 0xFF3F76E4;
   public static final int ANIM_FRAMES = 8;

   public TerraAtlas(Map<String, Tex> src) {
      this.fallback = this.add("p/missing", Tex.solid(0xFF7F7F7F));
      this.add("p/white", Tex.solid(0xFFFFFFFF));
      // große Bilder zuerst
      this.put(src, "environment/clouds", 0);
      for (String k : new String[]{"entity/zombie/zombie", "entity/player/wide/kai", "entity/player/slim/alex", "entity/player/wide/noor", "entity/player/wide/efe", "entity/player/slim/sunny", "entity/player/wide/steve"}) {
         this.put(src, k, 0);
      }

      this.add("p/boss1", boss(false));
      this.add("p/boss2", boss(true));
      for (String k : new String[]{"environment/celestial/sun", "environment/celestial/moon/full_moon"}) {
         Tex t = src.get(k);
         if (t != null) {
            this.add(k, celestial(t));
         } else {
            this.missing.add(k);
         }
      }
      // Animierte Flüssigkeiten: einige Einzelbilder
      for (String liq : new String[]{"block/water_still", "block/lava_still"}) {
         Tex t = src.get(liq);
         int frames = t == null ? 1 : Math.max(1, t.h / t.w);

         for (int k = 0; k < ANIM_FRAMES; k++) {
            Tex f = t == null ? Tex.solid(liq.contains("lava") ? 0xFFE06010 : 0xFF3F76E4) : frame(t, k * frames / ANIM_FRAMES);
            if (liq.contains("water")) {
               f = tint(f, WATER_TINT);
            }

            this.add(liq + "#" + k, f);
         }
      }

      // Gras: Seite + eingefärbte Überlagerung
      Tex side = src.get("block/grass_block_side");
      Tex over = src.get("block/grass_block_side_overlay");
      if (side != null && over != null) {
         Tex s = new Tex(16, 16, side.firstFrame().px.clone());
         Tex o = tint(over.firstFrame(), GRASS_TINT);

         for (int i = 0; i < 256 && i < o.px.length; i++) {
            if (o.px[i] >>> 24 > 128) {
               s.px[i] = o.px[i];
            }
         }

         this.add("block/grass_block_side", s);
      }

      for (String k : TerraTiles.textures()) {
         if (this.regions.containsKey(k) || k.startsWith("environment/") || k.startsWith("entity/") || k.equals("block/water_still") || k.equals("block/lava_still")) {
            continue;
         }

         int t = switch (k) {
            case "block/grass_block_top", "block/short_grass", "block/fern" -> GRASS_TINT;
            case "block/oak_leaves" -> OAK_TINT;
            case "block/birch_leaves" -> BIRCH_TINT;
            case "block/spruce_leaves" -> SPRUCE_TINT;
            case "block/jungle_leaves" -> JUNGLE_TINT;
            case "item/potion_overlay" -> 0xFFF0304A;
            default -> 0;
         };
         this.put(src, k, t);
      }

      // Heiltrank: Flasche + rote Füllung
      int[] bottle = this.regions.get("item/potion");
      int[] fill = this.regions.get("item/potion_overlay");
      if (bottle != null && fill != null && bottle != this.fallback && fill != this.fallback) {
         Tex p = new Tex(16, 16);

         for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
               int f = this.image.get(fill[0] + x, fill[1] + y);
               int b = this.image.get(bottle[0] + x, bottle[1] + y);
               p.set(x, y, f >>> 24 > 0 ? f : b);
            }
         }

         this.add("p/potion", p);
      } else {
         this.regions.put("p/potion", this.get("item/potion"));
      }

      this.add("p/slime", slime());
      this.add("p/eye", demonEye());
      this.add("p/bunny", bunny());
      this.add("p/coin_gold", coin(0xFFFFD84A, 0xFFB07818));
      this.add("p/coin_silver", coin(0xFFE8ECF4, 0xFF8890A0));
      this.add("p/coin_copper", coin(0xFFF09058, 0xFF9A4A22));
      this.add("p/star", star());
      this.add("p/heart", heart());
      this.add("p/glow", glow());
   }

   public java.util.List<String> missing() {
      return this.missing;
   }

   /** {u, v, w, h} eines Bildes (graues Ersatzbild, falls es fehlt). */
   public int[] get(String name) {
      int[] r = this.regions.get(name);
      return r == null ? this.fallback : r;
   }

   public boolean has(String name) {
      return this.regions.containsKey(name);
   }

   /** Durchschnittsfarbe eines Bildes (für Minikarte und Partikel). */
   public int average(String name) {
      Integer a = this.averages.get(name);
      if (a == null) {
         int[] r = this.get(name);
         Tex t = new Tex(r[2], r[3]);

         for (int y = 0; y < r[3]; y++) {
            for (int x = 0; x < r[2]; x++) {
               t.set(x, y, this.image.get(r[0] + x, r[1] + y));
            }
         }

         a = t.average();
         this.averages.put(name, a);
      }

      return a;
   }

   private void put(Map<String, Tex> src, String name, int tint) {
      Tex t = src.get(name);
      if (t == null) {
         this.missing.add(name);
         return;
      }

      if (name.startsWith("block/") || name.startsWith("item/")) {
         t = t.firstFrame();
      }

      this.add(name, tint == 0 ? t : tint(t, tint));
   }

   private int[] add(String name, Tex t) {
      if (this.cx + t.w + 1 > SIZE) {
         this.cx = 1;
         this.cy += this.rowH + 2;
         this.rowH = 0;
      }

      if (this.cy + t.h + 1 > SIZE) {
         return this.fallback;
      }

      int x0 = this.cx;
      int y0 = this.cy;
      // mit 1 Pixel Rand (Kante wiederholt), damit nichts vom Nachbarn durchblutet
      for (int y = -1; y <= t.h; y++) {
         for (int x = -1; x <= t.w; x++) {
            int sx = Math.max(0, Math.min(t.w - 1, x));
            int sy = Math.max(0, Math.min(t.h - 1, y));
            this.image.set(x0 + x, y0 + y, t.px[sy * t.w + sx]);
         }
      }

      this.cx += t.w + 2;
      this.rowH = Math.max(this.rowH, t.h);
      int[] r = {x0, y0, t.w, t.h};
      this.regions.put(name, r);
      return r;
   }

   /** Sonne/Mond werden in Minecraft additiv gezeichnet: Schwarz wird durchsichtig, leerer Rand abgeschnitten. */
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

      // quadratisch um die Mitte des Inhalts
      int size = Math.max(x1 - x0, y1 - y0) + 1;
      int cx = (x0 + x1) / 2;
      int cy = (y0 + y1) / 2;
      Tex o = new Tex(size, size);

      for (int y = 0; y < size; y++) {
         for (int x = 0; x < size; x++) {
            int sx = cx - size / 2 + x;
            int sy = cy - size / 2 + y;
            if (sx >= 0 && sy >= 0 && sx < t.w && sy < t.h) {
               o.px[y * size + x] = px[sy * t.w + sx];
            }
         }
      }

      return o;
   }

   private static Tex frame(Tex t, int k) {
      Tex f = new Tex(t.w, t.w);
      System.arraycopy(t.px, Math.min(k, t.h / t.w - 1) * t.w * t.w, f.px, 0, t.w * t.w);
      return f;
   }

   /** Graustufen-Textur einfärben (wie Minecraft Gras/Laub/Wasser). */
   static Tex tint(Tex t, int tint) {
      Tex o = new Tex(t.w, t.h);
      float tr = (tint >> 16 & 255) / 255.0F;
      float tg = (tint >> 8 & 255) / 255.0F;
      float tb = (tint & 255) / 255.0F;

      for (int i = 0; i < t.px.length; i++) {
         int c = t.px[i];
         o.px[i] = c & 0xFF000000 | Tex.clamp255((c >> 16 & 255) * tr) << 16 | Tex.clamp255((c >> 8 & 255) * tg) << 8 | Tex.clamp255((c & 255) * tb);
      }

      return o;
   }

   // ================================================================== Pixel-Art

   private static Tex slime() {
      Tex t = new Tex(16, 12);

      for (int y = 0; y < 12; y++) {
         for (int x = 0; x < 16; x++) {
            float dx = (x + 0.5F - 8.0F) / 7.6F;
            float dy = (y + 0.5F - 8.5F) / (y < 8 ? 8.0F : 3.6F);
            float r = dx * dx + dy * dy;
            if (r <= 1.0F) {
               int c = r > 0.72F ? 0xFF6A6A6A : 0xD8D4D4D4;
               if (r < 0.72F && y > 7) {
                  c = 0xD8B8B8B8;
               }

               t.set(x, y, c);
            }
         }
      }

      // Glanz und Augen
      t.set(4, 3, 0xF0FFFFFF);
      t.set(5, 3, 0xF0FFFFFF);
      t.set(4, 4, 0xF0FFFFFF);
      t.set(3, 5, 0xC0FFFFFF);
      for (int[] e : new int[][]{{6, 6}, {6, 7}, {10, 6}, {10, 7}}) {
         t.set(e[0], e[1], 0xFF1A1A1A);
      }

      return t;
   }

   private static Tex demonEye() {
      Tex t = new Tex(20, 12);
      java.util.Random r = new java.util.Random(7L);
      // Schwanz aus roten Fasern
      for (int k = 0; k < 4; k++) {
         int y0 = 3 + k * 2;

         for (int x = 0; x < 10; x++) {
            int y = y0 + (int)Math.round(Math.sin(x * 0.8 + k) * 0.8);
            t.set(x, y, x < 3 ? 0xA0801010 : 0xFFA01818);
         }
      }

      for (int y = 0; y < 12; y++) {
         for (int x = 6; x < 20; x++) {
            float dx = x + 0.5F - 13.5F;
            float dy = y + 0.5F - 6.0F;
            float d = (float)Math.sqrt(dx * dx + dy * dy);
            if (d <= 5.8F) {
               int c = d > 5.0F ? 0xFF6A2020 : 0xFFF2E8DC;
               if (d <= 5.0F && r.nextFloat() < 0.18F && dx < 1.0F) {
                  c = 0xFFD03030;
               }

               t.set(x, y, c);
            }
         }
      }

      for (int y = 0; y < 12; y++) {
         for (int x = 13; x < 20; x++) {
            float dx = x + 0.5F - 16.5F;
            float dy = y + 0.5F - 6.0F;
            float d = (float)Math.sqrt(dx * dx + dy * dy);
            if (d <= 2.4F) {
               t.set(x, y, d <= 1.2F ? 0xFF101010 : 0xFF3A5AD0);
            }
         }
      }

      t.set(16, 4, 0xFFFFFFFF);
      return t;
   }

   /** Auge von Cthulhu (48 x 40): Phase 1 mit Iris, Phase 2 mit Maul voller Zähne. */
   private static Tex boss(boolean mouth) {
      Tex t = new Tex(48, 40);
      java.util.Random r = new java.util.Random(11L);
      // Fasern nach hinten
      for (int k = 0; k < 6; k++) {
         float y0 = 9 + k * 4.4F;

         for (int x = 0; x < 18; x++) {
            int y = Math.round(y0 + (float)Math.sin(x * 0.55 + k * 1.3) * 1.6F);
            int c = x < 4 ? 0x90701010 : (k % 2 == 0 ? 0xFFB01C1C : 0xFF8A1414);
            t.set(x, y, c);
            t.set(x, y + 1, c);
         }
      }

      for (int y = 0; y < 40; y++) {
         for (int x = 8; x < 48; x++) {
            float dx = x + 0.5F - 29.0F;
            float dy = y + 0.5F - 20.0F;
            float d = (float)Math.sqrt(dx * dx + dy * dy);
            if (d <= 17.5F) {
               int c;
               if (d > 16.0F) {
                  c = 0xFF5A1414;
               } else {
                  float shade = 1.0F - Math.max(0.0F, (dy - dx * 0.3F) / 26.0F);
                  c = Tex.argb(255, 236 * shade, 226 * shade, 214 * shade);
                  if (r.nextFloat() < 0.09F && dx < 4.0F) {
                     c = 0xFFC82828;
                  }
               }

               t.set(x, y, c);
            }
         }
      }

      // Adern
      for (int k = 0; k < 7; k++) {
         float a = (float)(Math.PI * 0.55 + k * 0.32);
         float px = 29.0F + (float)Math.cos(a) * 15.0F;
         float py = 20.0F + (float)Math.sin(a) * 15.0F;

         for (int s = 0; s < 9; s++) {
            px += 1.0F + r.nextFloat() * 0.5F;
            py += (r.nextFloat() - 0.5F) * 1.6F - (float)Math.sin(a) * 0.6F;
            t.set(Math.round(px), Math.round(py), 0xFFB82020);
         }
      }

      if (!mouth) {
         for (int y = 0; y < 40; y++) {
            for (int x = 28; x < 48; x++) {
               float dx = x + 0.5F - 38.0F;
               float dy = y + 0.5F - 20.0F;
               float d = (float)Math.sqrt(dx * dx + dy * dy);
               if (d <= 7.0F) {
                  int c = d <= 3.2F ? 0xFF0C0808 : (d > 6.2F ? 0xFF3A1010 : Tex.mix(0xFFD04020, 0xFF7A1A10, (d - 3.2F) / 3.0F));
                  t.set(x, y, c);
               }
            }
         }

         t.set(36, 17, 0xFFFFFFFF);
         t.set(37, 17, 0xFFFFFFFF);
         t.set(36, 18, 0xFFFFFFFF);
      } else {
         for (int y = 0; y < 40; y++) {
            for (int x = 26; x < 48; x++) {
               float dx = (x + 0.5F - 38.0F) / 9.0F;
               float dy = (y + 0.5F - 20.0F) / 9.5F;
               float d = dx * dx + dy * dy;
               if (d <= 1.0F) {
                  t.set(x, y, d > 0.8F ? 0xFF4A0A0A : Tex.mix(0xFF2A0404, 0xFF6A1010, d));
               }
            }
         }

         // Zähne oben und unten
         for (int k = 0; k < 6; k++) {
            int x0 = 31 + k * 2 + (k > 2 ? 1 : 0);

            for (int s = 0; s < 4; s++) {
               int top = 11 + Math.abs(k - 2) / 2 + s;
               int bottom = 28 - Math.abs(k - 2) / 2 - s;
               if (s < 3) {
                  t.set(x0, top, 0xFFF4F0E0);
                  t.set(x0, bottom, 0xFFF4F0E0);
               }

               if (s < 2) {
                  t.set(x0 + 1, top, 0xFFD8D0C0);
                  t.set(x0 + 1, bottom, 0xFFD8D0C0);
               }
            }
         }
      }

      return t;
   }

   private static Tex bunny() {
      Tex t = new Tex(12, 10);
      int body = 0xFFEDEAE6;
      int shade = 0xFFC8C2BC;

      for (int y = 0; y < 10; y++) {
         for (int x = 0; x < 12; x++) {
            float dx = (x + 0.5F - 5.0F) / 4.6F;
            float dy = (y + 0.5F - 7.0F) / 3.0F;
            if (dx * dx + dy * dy <= 1.0F) {
               t.set(x, y, y > 7 ? shade : body);
            }

            float hx = (x + 0.5F - 9.0F) / 2.4F;
            float hy = (y + 0.5F - 5.0F) / 2.2F;
            if (hx * hx + hy * hy <= 1.0F) {
               t.set(x, y, body);
            }
         }
      }

      for (int y = 0; y < 4; y++) {
         t.set(8, y, body);
         t.set(9, y, y == 0 ? shade : 0xFFF0B8C0);
      }

      t.set(10, 4, 0xFF181818);
      t.set(11, 5, 0xFFE07080);
      t.set(0, 6, 0xFFFFFFFF);
      t.set(0, 5, 0xFFFFFFFF);
      return t;
   }

   private static Tex coin(int light, int dark) {
      Tex t = new Tex(6, 6);

      for (int y = 0; y < 6; y++) {
         for (int x = 0; x < 6; x++) {
            float dx = x + 0.5F - 3.0F;
            float dy = y + 0.5F - 3.0F;
            float d = dx * dx + dy * dy;
            if (d <= 8.5F) {
               t.set(x, y, d > 5.0F ? dark : light);
            }
         }
      }

      t.set(2, 2, 0xFFFFFFFF);
      return t;
   }

   private static Tex star() {
      Tex t = new Tex(11, 11);

      for (int y = 0; y < 11; y++) {
         for (int x = 0; x < 11; x++) {
            float dx = x + 0.5F - 5.5F;
            float dy = y + 0.5F - 5.8F;
            double a = Math.atan2(dy, dx) + Math.PI / 2;
            double rr = 5.4 * (0.55 + 0.45 * Math.cos(5 * a)) * 0.95 + 0.4;
            double d = Math.sqrt(dx * dx + dy * dy);
            if (d <= rr) {
               t.set(x, y, d <= rr - 1.1 ? (d < 1.6 ? 0xFFC8D8FF : 0xFF4A6CF0) : 0xFF14205A);
            }
         }
      }

      return t;
   }

   private static Tex heart() {
      String[] rows = {"..XX.XX..", ".XRRXRRX.", "XRWRRRRRX", "XRRRRRRRX", "XRRRRRRRX", ".XRRRRRX.", "..XRRRX..", "...XRX...", "....X...."};
      Tex t = new Tex(9, 9);

      for (int y = 0; y < 9; y++) {
         for (int x = 0; x < 9; x++) {
            char c = rows[y].charAt(x);
            t.set(x, y, c == 'X' ? 0xFF3A0808 : (c == 'R' ? 0xFFE8202C : (c == 'W' ? 0xFFFFFFFF : 0)));
         }
      }

      return t;
   }

   /** Weicher runder Lichtfleck (für Fackel- und Lavaschein). */
   private static Tex glow() {
      Tex t = new Tex(32, 32);

      for (int y = 0; y < 32; y++) {
         for (int x = 0; x < 32; x++) {
            float dx = (x + 0.5F - 16.0F) / 16.0F;
            float dy = (y + 0.5F - 16.0F) / 16.0F;
            float d = Math.max(0.0F, 1.0F - (float)Math.sqrt(dx * dx + dy * dy));
            t.set(x, y, Math.round(255 * d * d) << 24 | 0xFFFFFF);
         }
      }

      return t;
   }
}
