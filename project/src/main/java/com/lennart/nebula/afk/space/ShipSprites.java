package com.lennart.nebula.afk.space;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Sternenschiffe als Voxel-Modelle aus Minecraft-Blocktexturen (jedes Voxel = ein Texel eines Blocks). Jedes Modell
 * wird einmal schräg von oben/vorne gerendert (Minecraft-Flächenschattierung, Sonnenlicht, leuchtende Fenster und
 * Triebwerke) und als Sprite benutzt. Geschützpositionen und Triebwerke werden mitprojiziert.
 */
public final class ShipSprites {
   public static final int CRUISER = 0;
   public static final int CARRIER = 1;
   public static final int FRIGATE_A = 2;
   public static final int FRIGATE_B = 3;
   public static final int FIGHTER_A = 4;
   public static final int FIGHTER_B = 5;
   public static final int COUNT = 6;
   // Materialien
   private static final byte AIR = 0;
   private static final byte HULL = 1;
   private static final byte PLATE = 2;
   private static final byte DARK = 3;
   private static final byte ACCENT = 4;
   private static final byte WINDOW = 5;
   private static final byte ENGINE = 6;
   private static final byte DARK_HULL = 7;
   private static final byte DARK_PLATE = 8;
   private static final byte RUNNING = 9;
   private static final byte ENGINE_B = 10;
   private static final byte GLASS = 11;
   private final Tex[] mats = new Tex[12];
   private final boolean[] emissive = new boolean[12];
   public final Sprite[] sprites = new Sprite[COUNT];

   public static final class Sprite {
      public final Tex image;
      /** Länge in Weltblöcken (Bug bis Heck). */
      public final float length;
      /** Geschütze und Triebwerke in Sprite-Pixeln (Bug zeigt nach rechts). */
      public final List<float[]> guns = new ArrayList<>();
      public final List<float[]> engines = new ArrayList<>();
      /** Pixel pro Weltblock im Sprite. */
      public final float pxPerBlock;

      Sprite(Tex image, float length, float pxPerBlock) {
         this.image = image;
         this.length = length;
         this.pxPerBlock = pxPerBlock;
      }
   }

   public ShipSprites(Function<String, Tex> textures) {
      this.mat(HULL, textures, "light_gray_concrete", false);
      this.mat(PLATE, textures, "smooth_stone", false);
      this.mat(DARK, textures, "gray_concrete", false);
      this.mat(ACCENT, textures, "purple_concrete", false);
      this.mat(WINDOW, textures, "glowstone", true);
      this.mat(ENGINE, textures, "light_blue_concrete", true);
      this.mat(DARK_HULL, textures, "polished_deepslate", false);
      this.mat(DARK_PLATE, textures, "deepslate_tiles", false);
      this.mat(RUNNING, textures, "ochre_froglight_top", true);
      this.mat(ENGINE_B, textures, "lime_concrete", true);
      this.mat(GLASS, textures, "cyan_stained_glass", true);
   }

   private void mat(byte id, Function<String, Tex> textures, String name, boolean glow) {
      Tex t = textures.apply(name);
      this.mats[id] = t != null ? t.firstFrame() : Tex.solid(-7829368);
      this.emissive[id] = glow;
   }

   public void bakeAll() {
      this.sprites[CRUISER] = this.bake(this.cruiser(), 4.0F, 1.7F);
      this.sprites[CARRIER] = this.bake(this.carrier(), 4.5F, 1.6F);
      this.sprites[FRIGATE_A] = this.bake(this.frigateA(), 3.0F, 1.9F);
      this.sprites[FRIGATE_B] = this.bake(this.frigateB(), 3.0F, 1.9F);
      this.sprites[FIGHTER_A] = this.bake(this.fighterA(), 0.9F, 4.0F);
      this.sprites[FIGHTER_B] = this.bake(this.fighterB(), 0.9F, 4.0F);
   }

   // ------------------------------------------------------------------ Modelle (x = Länge, Bug bei x = L; y = oben; z = Breite)

   private static final class Model {
      final int lx;
      final int ly;
      final int lz;
      final byte[] v;
      final List<int[]> guns = new ArrayList<>();
      final List<int[]> engines = new ArrayList<>();

      Model(int lx, int ly, int lz) {
         this.lx = lx;
         this.ly = ly;
         this.lz = lz;
         this.v = new byte[lx * ly * lz];
      }

      byte get(int x, int y, int z) {
         return x >= 0 && y >= 0 && z >= 0 && x < this.lx && y < this.ly && z < this.lz ? this.v[(x * this.ly + y) * this.lz + z] : 0;
      }

      void set(int x, int y, int z, byte m) {
         if (x >= 0 && y >= 0 && z >= 0 && x < this.lx && y < this.ly && z < this.lz) {
            this.v[(x * this.ly + y) * this.lz + z] = m;
         }
      }

      void box(int x0, int y0, int z0, int x1, int y1, int z1, byte m) {
         for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
               for (int z = z0; z < z1; z++) {
                  this.set(x, y, z, m);
               }
            }
         }
      }

      /** Spiegelsymmetrische Box um die Mittelebene (Breite in z). */
      void sym(int x0, int y0, int x1, int y1, int halfWidth, byte m) {
         int c = this.lz / 2;
         this.box(x0, y0, c - halfWidth, x1, y1, c + halfWidth, m);
      }
   }

   private Model cruiser() {
      // Meowl-Kreuzer: langer Rumpf mit stumpfem Bug, Seitengondeln an Auslegern, flache Kuppelbrücke, Rückenflosse
      int L = 160;
      Model m = new Model(L, 40, 64);
      int c = m.lz / 2;

      for (int x = 6; x < L; x++) {
         float t = x / (float)L;
         int half = t < 0.72F ? 12 : Math.max(4, Math.round(12.0F - (t - 0.72F) / 0.28F * 8.0F));
         int bottom = t < 0.8F ? 4 : 4 + Math.round((t - 0.8F) * 20.0F);

         for (int y = bottom; y < 18; y++) {
            int round = Math.max(0, y - 14) * 2 + Math.max(0, bottom + 2 - y) * 2;
            int hw = Math.max(1, half - round);

            for (int z = c - hw; z < c + hw; z++) {
               boolean edge = Math.abs(z - c) >= hw - 1;
               byte mat = (x / 10 & 1) == 0 ? HULL : PLATE;
               if (edge && y >= 9 && y <= 10) {
                  mat = ACCENT;
               } else if (edge && y == 13 && x % 4 == 1 && t < 0.85F) {
                  mat = WINDOW;
               }

               m.set(x, y, z, mat);
            }
         }
      }

      // Ausleger und Triebwerksgondeln links/rechts
      for (int k = -1; k <= 1; k += 2) {
         int gz = c + k * 23;
         m.box(22, 9, Math.min(c + k * 11, gz), 40, 12, Math.max(c + k * 11, gz) + 1, DARK);
         m.box(0, 6, gz - 5, 62, 15, gz + 5, PLATE);
         m.box(2, 7, gz - 4, 60, 14, gz + 4, HULL);
         m.box(30, 10, gz + k * 5 - (k > 0 ? 1 : 0), 58, 11, gz + k * 5 + (k > 0 ? 0 : 1), ACCENT);
         m.box(0, 7, gz - 4, 1, 14, gz + 4, ENGINE);
         m.engines.add(new int[]{0, 10, gz});
      }

      // Hauptantrieb
      m.box(2, 6, c - 9, 6, 16, c + 9, DARK);
      m.box(1, 7, c - 7, 2, 15, c + 7, ENGINE);
      m.engines.add(new int[]{1, 11, c});
      // flache Kuppelbrücke und Rückenflosse
      for (int x = 46; x < 74; x++) {
         for (int y = 18; y < 25; y++) {
            for (int z = c - 9; z < c + 9; z++) {
               float dx = (x - 60) / 14.0F;
               float dy = (y - 17) / 7.0F;
               float dz = (z - c + 0.5F) / 9.0F;
               if (dx * dx + dy * dy + dz * dz < 1.0F) {
                  m.set(x, y, z, dy > 0.55F && dy < 0.75F && dx > -0.2F ? WINDOW : DARK);
               }
            }
         }
      }

      for (int x = 8; x < 40; x++) {
         int top = 18 + Math.min(12, (40 - x) / 2);
         m.box(x, 18, c - 1, x + 1, top, c + 1, x % 6 == 0 ? ACCENT : PLATE);
      }

      // Geschütztürme auf dem Deck
      int[][] guns = {{84, 18, c - 6}, {100, 18, c + 5}, {116, 18, c - 4}, {130, 17, c + 2}, {36, 15, c - 23}, {36, 15, c + 23}};

      for (int[] g : guns) {
         m.box(g[0] - 2, g[1], g[2] - 2, g[0] + 2, g[1] + 2, g[2] + 2, DARK);
         m.box(g[0] + 2, g[1] + 1, g[2] - 1, g[0] + 7, g[1] + 2, g[2], DARK);
         m.guns.add(new int[]{g[0] + 5, g[1] + 2, g[2]});
      }

      return m;
   }

   private Model carrier() {
      int L = 180;
      Model m = new Model(L, 40, 44);
      int c = m.lz / 2;
      // Rückgrat mit Rippen
      for (int x = 10; x < L - 22; x++) {
         boolean rib = x % 14 < 3;
         int hw = rib ? 15 : 11;
         int h0 = rib ? 6 : 9;
         int h1 = rib ? 26 : 22;

         for (int y = h0; y < h1; y++) {
            for (int z = c - hw; z < c + hw; z++) {
               boolean side = Math.abs(z - c) >= hw - 1;
               byte mat = rib ? DARK_PLATE : DARK_HULL;
               if (side && !rib && (y == 14 || y == 18) && x % 3 == 0) {
                  mat = RUNNING;
               }

               m.set(x, y, z, mat);
            }
         }
      }

      // Kommandokugel vorne
      for (int x = L - 26; x < L; x++) {
         for (int y = 4; y < 30; y++) {
            for (int z = 0; z < m.lz; z++) {
               float dx = (x - (L - 14)) / 13.0F;
               float dy = (y - 16) / 12.0F;
               float dz = (z - c) / 15.0F;
               if (dx * dx + dy * dy + dz * dz < 1.0F) {
                  m.set(x, y, z, Math.abs(dy) < 0.12F && dx > 0.2F ? RUNNING : DARK_PLATE);
               }
            }
         }
      }

      // Heck-Block mit Triebwerken
      m.sym(0, 4, 14, 30, 18, DARK_PLATE);

      for (int k = -1; k <= 1; k += 2) {
         m.box(0, 9, c + k * 9 - 5, 1, 25, c + k * 9 + 5, ENGINE_B);
         m.engines.add(new int[]{0, 17, c + k * 9});
      }

      // Antennen und Geschützreihen
      m.sym(40, 26, 42, 36, 1, DARK_HULL);
      m.sym(90, 26, 92, 33, 1, DARK_HULL);

      for (int x = 24; x < L - 30; x += 22) {
         m.box(x, 22, c - 3, x + 4, 25, c + 3, DARK_PLATE);
         m.box(x + 4, 23, c - 1, x + 9, 24, c + 1, DARK_HULL);
         m.guns.add(new int[]{x + 7, 24, c});
      }

      return m;
   }

   private Model frigateA() {
      int L = 90;
      Model m = new Model(L, 30, 34);
      int c = m.lz / 2;

      for (int x = 0; x < L; x++) {
         float t = x / (float)L;
         int half = Math.max(1, Math.round(13.0F * Math.min(1.0F, (1.0F - t) / 0.5F)));

         for (int y = 3; y < 11; y++) {
            int hw = Math.max(1, half - Math.max(0, y - 8));

            for (int z = c - hw; z < c + hw; z++) {
               m.set(x, y, z, Math.abs(z - c) >= hw - 1 && y == 6 ? ACCENT : ((x / 6 & 1) == 0 ? HULL : PLATE));
            }
         }
      }

      m.sym(10, 11, 36, 16, 6, HULL);
      m.sym(14, 16, 26, 20, 4, DARK);
      m.sym(15, 18, 25, 19, 5, WINDOW);
      m.box(0, 4, c - 6, 2, 10, c + 6, ENGINE);
      m.engines.add(new int[]{0, 7, c});
      m.box(46, 11, c - 2, 50, 13, c + 2, DARK);
      m.guns.add(new int[]{52, 12, c});
      m.box(62, 10, c - 2, 66, 12, c + 2, DARK);
      m.guns.add(new int[]{68, 11, c});
      return m;
   }

   private Model frigateB() {
      int L = 100;
      Model m = new Model(L, 30, 34);
      int c = m.lz / 2;
      m.sym(8, 8, 78, 20, 7, DARK_HULL);
      m.sym(20, 6, 70, 22, 10, DARK_PLATE);
      // Zinken vorne
      m.box(78, 14, c - 7, L, 18, c - 3, DARK_HULL);
      m.box(78, 14, c + 3, L, 18, c + 7, DARK_HULL);
      m.box(78, 10, c - 2, 92, 14, c + 2, DARK_PLATE);

      for (int x = 22; x < 70; x += 4) {
         m.set(x, 15, c - 10, RUNNING);
         m.set(x, 15, c + 9, RUNNING);
      }

      m.sym(0, 9, 8, 19, 6, DARK_PLATE);
      m.box(0, 10, c - 4, 1, 18, c + 4, ENGINE_B);
      m.engines.add(new int[]{0, 14, c});
      m.box(40, 22, c - 2, 44, 24, c + 2, DARK_HULL);
      m.guns.add(new int[]{46, 23, c});
      m.box(84, 18, c - 1, 88, 20, c + 1, DARK_HULL);
      m.guns.add(new int[]{90, 19, c});
      return m;
   }

   private Model fighterA() {
      Model m = new Model(20, 8, 18);
      int c = m.lz / 2;
      m.box(2, 3, c - 1, 18, 5, c + 1, HULL);
      m.box(18, 3, c - 1, 20, 4, c + 1, PLATE);
      m.box(10, 5, c - 1, 14, 6, c + 1, GLASS);

      for (int x = 4; x < 12; x++) {
         int span = (x - 3) * 1 + 2;
         m.box(x, 3, c - span, x + 1, 4, c + span, (x & 1) == 0 ? HULL : ACCENT);
      }

      m.box(0, 3, c - 1, 2, 5, c + 1, ENGINE);
      m.engines.add(new int[]{0, 4, c});
      m.guns.add(new int[]{20, 4, c});
      return m;
   }

   private Model fighterB() {
      Model m = new Model(20, 14, 14);
      int c = m.lz / 2;

      for (int x = 4; x < 14; x++) {
         for (int y = 2; y < 12; y++) {
            for (int z = 0; z < m.lz; z++) {
               float dx = (x - 9) / 5.0F;
               float dy = (y - 7) / 5.0F;
               float dz = (z - c) / 5.0F;
               if (dx * dx + dy * dy + dz * dz < 1.0F) {
                  m.set(x, y, z, dx > 0.55F ? GLASS : DARK_PLATE);
               }
            }
         }
      }

      m.box(12, 5, c - 5, 20, 7, c - 3, DARK_HULL);
      m.box(12, 5, c + 3, 20, 7, c + 5, DARK_HULL);
      m.box(2, 6, c - 2, 4, 8, c + 2, ENGINE_B);
      m.engines.add(new int[]{2, 7, c});
      m.guns.add(new int[]{20, 6, c});
      return m;
   }

   // ------------------------------------------------------------------ Rendern

   /** Blickrichtung (vom Betrachter zum Schiff): etwas von oben und leicht von vorne. */
   private static final float[] VIEW = norm(-0.36F, -0.3F, 0.88F);
   private static final float[] SUN = norm(-0.5F, 0.75F, -0.45F);

   private static float[] norm(float x, float y, float z) {
      float l = (float)Math.sqrt(x * x + y * y + z * z);
      return new float[]{x / l, y / l, z / l};
   }

   private Sprite bake(Model m, float blocksPerVoxel, float pxPerVoxel) {
      float[] v = VIEW;
      // Bildachsen: rechts = Bug-Richtung so gut es geht, oben = Weltoben projiziert
      float[] right = norm(v[2], 0.0F, -v[0]);
      if (right[0] < 0.0F) {
         right = new float[]{-right[0], -right[1], -right[2]};
      }

      float[] up = new float[]{v[1] * right[2] - v[2] * right[1], v[2] * right[0] - v[0] * right[2], v[0] * right[1] - v[1] * right[0]};
      if (up[1] < 0.0F) {
         up = new float[]{-up[0], -up[1], -up[2]};
      }

      // Bildgröße aus den projizierten Ecken
      float minU = Float.MAX_VALUE;
      float maxU = -Float.MAX_VALUE;
      float minV = Float.MAX_VALUE;
      float maxV = -Float.MAX_VALUE;

      for (int i = 0; i < 8; i++) {
         float x = (i & 1) * m.lx;
         float y = (i >> 1 & 1) * m.ly;
         float z = (i >> 2 & 1) * m.lz;
         float u = x * right[0] + y * right[1] + z * right[2];
         float w = x * up[0] + y * up[1] + z * up[2];
         minU = Math.min(minU, u);
         maxU = Math.max(maxU, u);
         minV = Math.min(minV, w);
         maxV = Math.max(maxV, w);
      }

      int w = (int)Math.ceil((maxU - minU) * pxPerVoxel) + 2;
      int h = (int)Math.ceil((maxV - minV) * pxPerVoxel) + 2;
      Tex img = new Tex(w, h);
      float far = m.lx + m.ly + m.lz;

      for (int py = 0; py < h; py++) {
         for (int px = 0; px < w; px++) {
            float u = minU + (px + 0.5F) / pxPerVoxel;
            float vv = maxV - (py + 0.5F) / pxPerVoxel;
            float ox = u * right[0] + vv * up[0] - v[0] * far;
            float oy = u * right[1] + vv * up[1] - v[1] * far;
            float oz = u * right[2] + vv * up[2] - v[2] * far;
            img.px[py * w + px] = this.march(m, ox, oy, oz, v);
         }
      }

      Sprite s = new Sprite(img, m.lx * blocksPerVoxel, pxPerVoxel / blocksPerVoxel);

      for (int[] g : m.guns) {
         s.guns.add(project(g, right, up, minU, maxV, pxPerVoxel));
      }

      for (int[] e : m.engines) {
         s.engines.add(project(e, right, up, minU, maxV, pxPerVoxel));
      }

      return s;
   }

   private static float[] project(int[] p, float[] right, float[] up, float minU, float maxV, float scale) {
      float u = p[0] * right[0] + p[1] * right[1] + p[2] * right[2];
      float w = p[0] * up[0] + p[1] * up[1] + p[2] * up[2];
      return new float[]{(u - minU) * scale, (maxV - w) * scale};
   }

   private static final float[] FACE_SHADE = {0.6F, 0.6F, 0.5F, 1.0F, 0.8F, 0.8F};

   /** Voxel-DDA: erstes festes Voxel, schattiert. */
   private int march(Model m, float ox, float oy, float oz, float[] d) {
      // Einstieg in das Gitter
      float t0 = 0.0F;
      float t1 = Float.MAX_VALUE;
      float[] o = {ox, oy, oz};
      int[] size = {m.lx, m.ly, m.lz};

      for (int a = 0; a < 3; a++) {
         if (Math.abs(d[a]) < 1.0E-6F) {
            if (o[a] < 0.0F || o[a] > size[a]) {
               return 0;
            }
         } else {
            float ta = (0.0F - o[a]) / d[a];
            float tb = (size[a] - o[a]) / d[a];
            t0 = Math.max(t0, Math.min(ta, tb));
            t1 = Math.min(t1, Math.max(ta, tb));
         }
      }

      if (t0 > t1) {
         return 0;
      }

      float t = t0 + 1.0E-4F;
      float x = ox + d[0] * t;
      float y = oy + d[1] * t;
      float z = oz + d[2] * t;
      int ix = Math.min(m.lx - 1, Math.max(0, (int)Math.floor(x)));
      int iy = Math.min(m.ly - 1, Math.max(0, (int)Math.floor(y)));
      int iz = Math.min(m.lz - 1, Math.max(0, (int)Math.floor(z)));
      int sx = d[0] > 0.0F ? 1 : -1;
      int sy = d[1] > 0.0F ? 1 : -1;
      int sz = d[2] > 0.0F ? 1 : -1;
      float dtx = Math.abs(1.0F / d[0]);
      float dty = Math.abs(1.0F / d[1]);
      float dtz = Math.abs(1.0F / d[2]);
      float tx = ((sx > 0 ? ix + 1 : ix) - x) / d[0];
      float ty = ((sy > 0 ? iy + 1 : iy) - y) / d[1];
      float tz = ((sz > 0 ? iz + 1 : iz) - z) / d[2];
      int face = -1;

      for (int steps = 0; steps < 600; steps++) {
         byte mat = m.get(ix, iy, iz);
         if (mat != AIR) {
            if (face < 0) {
               face = 3;
            }

            return this.shadeVoxel(m, mat, ix, iy, iz, face);
         }

         if (tx < ty && tx < tz) {
            ix += sx;
            tx += dtx;
            face = sx > 0 ? 0 : 1;
         } else if (ty < tz) {
            iy += sy;
            ty += dty;
            face = sy > 0 ? 2 : 3;
         } else {
            iz += sz;
            tz += dtz;
            face = sz > 0 ? 4 : 5;
         }

         if (ix < 0 || iy < 0 || iz < 0 || ix >= m.lx || iy >= m.ly || iz >= m.lz) {
            return 0;
         }
      }

      return 0;
   }

   private int shadeVoxel(Model m, byte mat, int x, int y, int z, int face) {
      Tex tex = this.mats[mat];
      int tu;
      int tv;
      switch (face) {
         case 0:
         case 1:
            tu = z;
            tv = -y;
            break;
         case 2:
         case 3:
            tu = x;
            tv = z;
            break;
         default:
            tu = x;
            tv = -y;
      }

      int c = tex.at(tu, tv);
      if (this.emissive[mat]) {
         return 0xFF000000 | c & 16777215;
      }

      float[] n = {face == 0 ? -1 : (face == 1 ? 1 : 0), face == 2 ? -1 : (face == 3 ? 1 : 0), face == 4 ? -1 : (face == 5 ? 1 : 0)};
      float sun = Math.max(0.0F, n[0] * SUN[0] + n[1] * SUN[1] + n[2] * SUN[2]);
      // einfache Verdeckung: Voxel über/neben dieser Fläche zur Sonne hin
      boolean shadow = m.get(x + Math.round(SUN[0] * 2.0F), y + 2, z + Math.round(SUN[2] * 2.0F)) != AIR;
      float l = 0.32F * FACE_SHADE[face] + (shadow ? 0.15F : 0.85F) * sun;
      // Planetenlicht von unten (rötlich)
      float below = n[1] < 0.0F ? 0.18F : 0.04F;
      float r = (c >> 16 & 0xFF) * (l + below * 1.2F);
      float g = (c >> 8 & 0xFF) * (l + below * 0.7F);
      float b = (c & 0xFF) * (l + below * 0.6F);
      return Tex.argb(255, r, g, b);
   }
}
