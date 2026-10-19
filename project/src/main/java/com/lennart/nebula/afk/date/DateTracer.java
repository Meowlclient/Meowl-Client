package com.lennart.nebula.afk.date;

import com.lennart.nebula.afk.space.Cam;
import com.lennart.nebula.afk.space.Noise;
import com.lennart.nebula.afk.space.Tex;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * „E-Date“: Klippe über einer Bucht im Minecraft-Stil, Blick aufs Meer, in dem gleich die Sonne aufgeht. Die Blockwelt
 * (Gras, Erde, Stein, Sand, Kirschbäume, Blumen, eine Decke) wird einmal per Raytracing gerendert – in zwei
 * Lichtstimmungen (Morgendämmerung und goldene Stunde), zwischen denen die Szene langsam überblendet. Himmel und Sonne
 * bleiben durchsichtig und werden live dahinter gezeichnet. Reines Java.
 */
public final class DateTracer {
   public static final int VW = 960;
   public static final int VH = 540;
   /** Kamera hinter dem Paar, leicht nach unten geneigt, Blick aufs Meer (+z). */
   public static final Cam CAM = new Cam(VW, VH, 0.0F, 13.4F, -6.5F, -9.0F, 50.0F);
   /** Sitzplatz: Oberkante der Klippe, Kante bei z = EDGE. */
   public static final float SEAT_Y = 10.0F;
   public static final float SEAT_Z = 1.72F;
   public static final float SEAT_DX = 0.56F;
   public static final float WATER_Y = 4.62F;
   public static final String[] TEXTURES = {
      "block/grass_block_top", "block/grass_block_side", "block/grass_block_side_overlay", "block/dirt", "block/stone", "block/andesite",
      "block/sand", "block/gravel", "block/cherry_log", "block/cherry_log_top", "block/cherry_leaves", "block/oak_log", "block/oak_log_top",
      "block/oak_leaves", "block/poppy", "block/dandelion", "block/allium", "block/oxeye_daisy", "block/cornflower", "block/lily_of_the_valley",
      "block/azure_bluet", "block/pink_tulip", "block/short_grass", "block/pink_wool", "block/red_wool", "block/water_still", "block/moss_block",
      "block/mossy_cobblestone", "block/torchflower", "block/lantern"
   };
   static final int X0 = -96;
   static final int NX = 192;
   static final int NY = 40;
   static final int Z0 = -12;
   static final int NZ = 210;
   static final byte AIR = 0;
   static final byte GRASS = 1;
   static final byte DIRT = 2;
   static final byte STONE = 3;
   static final byte ANDESITE = 4;
   static final byte SAND = 5;
   static final byte GRAVEL = 6;
   static final byte CHERRY_LOG = 7;
   static final byte CHERRY_LEAVES = 8;
   static final byte OAK_LOG = 9;
   static final byte OAK_LEAVES = 10;
   static final byte CARPET = 11;
   static final byte MOSSY = 12;
   /** Pflanzen ab hier (gekreuzte Bilder). */
   static final byte PLANT0 = 20;
   static final String[] PLANTS = {
      "block/poppy", "block/dandelion", "block/allium", "block/oxeye_daisy", "block/cornflower", "block/lily_of_the_valley", "block/azure_bluet",
      "block/pink_tulip", "block/short_grass", "block/torchflower"
   };
   private static final int[][] NORMAL = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
   private final byte[] grid = new byte[NX * NY * NZ];
   private final Noise noise = new Noise(77L);
   private final Map<String, Tex> tex;
   private final Tex grassTop;
   private final Tex grassSide;
   private final Tex leavesCherry;
   private final Tex leavesOak;
   /** Ergebnis: Morgendämmerung und goldene Stunde (Himmel durchsichtig). */
   public final Tex dawn = new Tex(VW, VH);
   public final Tex golden = new Tex(VW, VH);
   /** Wasserpixel (für das Glitzern der Sonne). */
   public final boolean[] water = new boolean[VW * VH];
   /** Bildschirm-Lage des Paares im Bild: {x links, x rechts, y Kante, Pixel je Block}. */
   public final float[] seat = new float[4];
   // aktuelle Lichtstimmung
   private float[] sun;
   private float[] sunCol;
   private float[] zenith;
   private float[] horizon;
   private float[] glow;
   private float ambient;

   public DateTracer(Map<String, Tex> tex) {
      this.tex = tex;
      this.grassTop = tint(this.t("block/grass_block_top"), 0.55F, 0.78F, 0.36F);
      Tex side = this.t("block/grass_block_side");
      Tex over = tex.get("block/grass_block_side_overlay");
      Tex gs = new Tex(16, 16, side.px.clone());
      if (over != null) {
         Tex o = tint(over.firstFrame(), 0.55F, 0.78F, 0.36F);

         for (int i = 0; i < 256 && i < o.px.length; i++) {
            if (o.px[i] >>> 24 > 128) {
               gs.px[i] = o.px[i];
            }
         }
      }

      this.grassSide = gs;
      this.leavesCherry = this.t("block/cherry_leaves");
      this.leavesOak = tint(this.t("block/oak_leaves"), 0.4F, 0.66F, 0.25F);
      this.build();
   }

   private Tex t(String name) {
      Tex t = this.tex.get(name);
      return t == null ? Tex.solid(0xFF8A8A8A) : t.firstFrame();
   }

   private static Tex tint(Tex t, float r, float g, float b) {
      Tex o = new Tex(t.w, t.h);

      for (int i = 0; i < t.px.length; i++) {
         int c = t.px[i];
         o.px[i] = c & 0xFF000000 | Tex.clamp255((c >> 16 & 255) * r) << 16 | Tex.clamp255((c >> 8 & 255) * g) << 8 | Tex.clamp255((c & 255) * b);
      }

      return o;
   }

   private static float[] norm(float x, float y, float z) {
      float l = (float)Math.sqrt(x * x + y * y + z * z);
      return new float[]{x / l, y / l, z / l};
   }

   // ================================================================== Welt

   private int idx(int x, int y, int z) {
      return ((z - Z0) * NY + y) * NX + (x - X0);
   }

   private boolean in(int x, int y, int z) {
      return x >= X0 && x < X0 + NX && y >= 0 && y < NY && z >= Z0 && z < Z0 + NZ;
   }

   byte get(int x, int y, int z) {
      return this.in(x, y, z) ? this.grid[this.idx(x, y, z)] : AIR;
   }

   private void set(int x, int y, int z, byte b) {
      if (this.in(x, y, z)) {
         this.grid[this.idx(x, y, z)] = b;
      }
   }

   /** Kante der Klippe (erste Zelle vor der Kante) je Spalte x. */
   private float edge(int x) {
      int ax = Math.abs(x);
      if (ax <= 3) {
         return 2.0F;
      }

      return 2.0F + (ax - 3) * 0.55F + (this.noise.noise(x * 0.3F, 1.0F, 2.0F) - 0.5F) * 3.0F + (float)Math.pow(Math.max(0, ax - 12) * 0.25F, 1.6);
   }

   private int height(int x, int z) {
      float e = this.edge(x);
      int ax = Math.abs(x);
      if (z < e) {
         // Plateau, zu den Seiten leicht ansteigend
         return 10 + (ax > 6 ? (int)(Math.min(5.0F, (ax - 6) * 0.25F) + this.noise.noise(x * 0.2F, z * 0.2F, 3.0F) * 2.0F) : 0);
      }

      float d = z - e;
      // Strand unter der Klippe, dann sanft ins Meer
      float h = 5.2F - Math.max(0.0F, d - 3.0F) * 0.35F;
      // Landzungen links und rechts, die die Bucht umarmen
      float side = Math.max(0.0F, ax - 26.0F);
      if (side > 0.0F && z < 110) {
         float fall = Math.max(0.0F, 1.0F - z / 110.0F);
         h = Math.max(h, 5.0F + side * 0.55F * fall + this.noise.fbm(x * 0.06F, z * 0.06F, 4.0F, 3) * 14.0F * fall);
      }

      // Inseln am Horizont
      for (float[] is : ISLANDS) {
         float dx = x - is[0];
         float dz = z - is[1];
         float r = (float)Math.sqrt(dx * dx + dz * dz);
         if (r < is[2]) {
            float k = 1.0F - r / is[2];
            h = Math.max(h, 3.5F + is[3] * k * k * (0.8F + 0.4F * this.noise.noise(x * 0.2F, z * 0.2F, 5.0F)));
         }
      }

      return Math.max(0, Math.min(NY - 6, Math.round(h)));
   }

   private static final float[][] ISLANDS = {{-44.0F, 128.0F, 13.0F, 9.0F}, {38.0F, 150.0F, 10.0F, 6.0F}, {66.0F, 112.0F, 9.0F, 7.0F}};

   private void build() {
      for (int z = Z0; z < Z0 + NZ; z++) {
         for (int x = X0; x < X0 + NX; x++) {
            int h = this.height(x, z);

            for (int y = 0; y < h; y++) {
               int depth = h - 1 - y;
               byte b;
               if (h <= 6) {
                  b = depth < 3 ? SAND : (this.noise.rand(x, y, z) < 0.3F ? GRAVEL : STONE);
               } else if (depth == 0) {
                  b = GRASS;
               } else if (depth < 3) {
                  b = DIRT;
               } else {
                  float n = this.noise.noise(x * 0.25F, y * 0.25F, z * 0.25F);
                  b = n > 0.62F ? ANDESITE : (n < 0.25F ? MOSSY : STONE);
               }

               this.set(x, y, z, b);
            }
         }
      }

      // Decke für das Paar
      for (int x = -2; x <= 1; x++) {
         for (int z = 0; z <= 1; z++) {
            this.set(x, 10, z, CARPET);
         }
      }

      // Kirschbäume rahmen den Blick, auf den Landzungen mehr Bäume
      this.tree(-8, 3, true, 6);
      this.tree(9, 1, true, 7);
      this.tree(-15, 5, false, 5);
      java.util.Random r = new java.util.Random(9L);

      for (int i = 0; i < 70; i++) {
         int x = (r.nextBoolean() ? 1 : -1) * (28 + r.nextInt(60));
         int z = 4 + r.nextInt(90);
         int h = this.height(x, z);
         if (h >= 7 && this.get(x, h - 1, z) == GRASS) {
            this.tree(x, z, r.nextFloat() < 0.65F, 4 + r.nextInt(4));
         }
      }

      for (float[] is : ISLANDS) {
         int x = Math.round(is[0]);
         int z = Math.round(is[1]);
         if (this.get(x, this.height(x, z) - 1, z) == GRASS) {
            this.tree(x, z, true, 5);
         }
      }

      // Blumen auf dem Plateau und den Landzungen
      for (int z = Z0; z < 60; z++) {
         for (int x = -40; x <= 40; x++) {
            int h = this.height(x, z);
            if (h < NY && this.get(x, h - 1, z) == GRASS && this.get(x, h, z) == AIR) {
               float rr = this.noise.rand(x, 7, z);
               boolean nearSeat = Math.abs(x) <= 2 && z >= -1 && z <= 1;
               if (!nearSeat && rr < 0.33F) {
                  int p = (int)(this.noise.rand(x, 8, z) * PLANTS.length);
                  // mehr Gras als Blumen, weiter weg weniger Blumen
                  if (rr < 0.12F || Math.abs(x) > 12) {
                     p = 8;
                  }

                  this.set(x, h, z, (byte)(PLANT0 + p));
               }
            }
         }
      }
   }

   private void tree(int x, int z, boolean cherry, int trunk) {
      int base = this.height(x, z);
      byte log = cherry ? CHERRY_LOG : OAK_LOG;
      byte leaf = cherry ? CHERRY_LEAVES : OAK_LEAVES;

      for (int y = base; y < base + trunk; y++) {
         this.set(x, y, z, log);
      }

      int top = base + trunk;
      float rad = cherry ? 3.4F : 2.6F;

      for (int dy = -2; dy <= 2; dy++) {
         for (int dz = -4; dz <= 4; dz++) {
            for (int dx = -4; dx <= 4; dx++) {
               float d = dx * dx + dz * dz + dy * dy * (cherry ? 2.2F : 1.6F);
               float rr = rad + (this.noise.rand(x + dx, top + dy, z + dz) - 0.5F) * 1.2F;
               if (d <= rr * rr && this.get(x + dx, top + dy, z + dz) == AIR) {
                  this.set(x + dx, top + dy, z + dz, leaf);
               }
            }
         }
      }

      // Kirschblüten hängen etwas herunter
      if (cherry) {
         for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -3; dx <= 3; dx++) {
               if (this.noise.rand(x + dx, 3, z + dz) < 0.3F && this.get(x + dx, top - 3, z + dz) == AIR && this.get(x + dx, top - 2, z + dz) == leaf) {
                  this.set(x + dx, top - 3, z + dz, leaf);
               }
            }
         }
      }
   }

   // ================================================================== Licht und Himmel

   /** Stimmung setzen: 0 = Morgendämmerung, 1 = goldene Stunde. */
   private void mood(int m) {
      if (m == 0) {
         this.sun = norm(0.12F, 0.025F, 1.0F);
         this.sunCol = new float[]{1.25F, 0.62F, 0.38F};
         this.zenith = new float[]{0.16F, 0.17F, 0.36F};
         this.horizon = new float[]{0.98F, 0.55F, 0.42F};
         this.glow = new float[]{1.0F, 0.68F, 0.42F};
         this.ambient = 0.56F;
      } else {
         this.sun = norm(0.12F, 0.13F, 1.0F);
         this.sunCol = new float[]{1.45F, 1.02F, 0.62F};
         this.zenith = new float[]{0.3F, 0.46F, 0.78F};
         this.horizon = new float[]{1.0F, 0.78F, 0.55F};
         this.glow = new float[]{1.0F, 0.86F, 0.6F};
         this.ambient = 0.62F;
      }
   }

   /** Himmelsfarbe in einer Richtung (für Spiegelungen und Dunst; passend zum live gezeichneten Himmel). */
   private float[] sky(float dx, float dy, float dz) {
      float e = Math.max(0.0F, dy);
      float t = (float)Math.pow(e, 0.45);
      float[] c = new float[3];

      for (int i = 0; i < 3; i++) {
         c[i] = this.horizon[i] + (this.zenith[i] - this.horizon[i]) * Math.min(1.0F, t * 1.6F);
      }

      float s = Math.max(0.0F, dx * this.sun[0] + dy * this.sun[1] + dz * this.sun[2]);
      float g = (float)Math.pow(s, 12.0) * 0.6F;

      for (int i = 0; i < 3; i++) {
         c[i] += this.glow[i] * g;
      }

      return c;
   }

   // ================================================================== Strahlen

   private static final class Hit {
      float t;
      int x;
      int y;
      int z;
      int face;
      byte block;
      float u;
      float v;
      int color;
   }

   /** DDA durch das Raster; Pflanzen, Laub und Decke mit Durchsicht. null = nichts getroffen. */
   private Hit trace(float ox, float oy, float oz, float dx, float dy, float dz, float maxT, boolean shadow) {
      int x = (int)Math.floor(ox);
      int y = (int)Math.floor(oy);
      int z = (int)Math.floor(oz);
      int sx = dx > 0 ? 1 : -1;
      int sy = dy > 0 ? 1 : -1;
      int sz = dz > 0 ? 1 : -1;
      float tdx = dx == 0 ? Float.MAX_VALUE : Math.abs(1.0F / dx);
      float tdy = dy == 0 ? Float.MAX_VALUE : Math.abs(1.0F / dy);
      float tdz = dz == 0 ? Float.MAX_VALUE : Math.abs(1.0F / dz);
      float tmx = dx == 0 ? Float.MAX_VALUE : ((dx > 0 ? x + 1 - ox : ox - x) * tdx);
      float tmy = dy == 0 ? Float.MAX_VALUE : ((dy > 0 ? y + 1 - oy : oy - y) * tdy);
      float tmz = dz == 0 ? Float.MAX_VALUE : ((dz > 0 ? z + 1 - oz : oz - z) * tdz);
      float t = 0.0F;
      int face = -1;

      for (int step = 0; step < 900 && t < maxT; step++) {
         if (y >= NY && dy >= 0 || y < 0 && dy <= 0) {
            return null;
         }

         byte b = this.get(x, y, z);
         if (b != AIR) {
            Hit h = this.surface(b, x, y, z, face, ox, oy, oz, dx, dy, dz, t, shadow);
            if (h != null) {
               return h;
            }
         }

         if (tmx < tmy && tmx < tmz) {
            t = tmx;
            tmx += tdx;
            x += sx;
            face = sx > 0 ? 0 : 1;
         } else if (tmy < tmz) {
            t = tmy;
            tmy += tdy;
            y += sy;
            face = sy > 0 ? 2 : 3;
         } else {
            t = tmz;
            tmz += tdz;
            z += sz;
            face = sz > 0 ? 4 : 5;
         }

         if (!this.in(x, y, z) && (x < X0 && dx < 0 || x >= X0 + NX && dx > 0 || z < Z0 && dz < 0 || z >= Z0 + NZ && dz > 0)) {
            return null;
         }
      }

      return null;
   }

   /** Genaue Fläche im Block; null = durchsichtige Stelle, weiter laufen. */
   private Hit surface(byte b, int x, int y, int z, int face, float ox, float oy, float oz, float dx, float dy, float dz, float t, boolean shadow) {
      if (b >= PLANT0) {
         // zwei gekreuzte Bilder durch die Zellmitte
         Tex img = this.t(PLANTS[b - PLANT0]);
         Hit best = null;

         for (int k = 0; k < 2; k++) {
            // Ebene: (px - cx) * nx + (pz - cz) * nz = 0
            float nx = k == 0 ? 0.7071F : 0.7071F;
            float nz = k == 0 ? -0.7071F : 0.7071F;
            float den = dx * nx + dz * nz;
            if (Math.abs(den) < 1.0E-5F) {
               continue;
            }

            float tt = ((x + 0.5F - ox) * nx + (z + 0.5F - oz) * nz) / den;
            float px = ox + dx * tt;
            float py = oy + dy * tt;
            float pz = oz + dz * tt;
            if (tt <= 0 || px < x || px > x + 1 || pz < z || pz > z + 1 || py < y || py > y + 0.9F) {
               continue;
            }

            float u = k == 0 ? (px - x + (pz - z)) * 0.5F : (px - x - (pz - z) + 1.0F) * 0.5F;
            float v = 1.0F - (py - y) / 0.9F;
            int c = img.at((int)(u * img.w), (int)(Math.min(0.999F, v) * img.h));
            if (c >>> 24 > 100 && (best == null || tt < best.t)) {
               if (b - PLANT0 == 8) {
                  c = mulTint(c, 0.55F, 0.78F, 0.36F);
               }

               best = new Hit();
               best.t = tt;
               best.x = x;
               best.y = y;
               best.z = z;
               best.face = 3;
               best.block = b;
               best.color = c;
            }
         }

         return best;
      }

      if (b == CARPET) {
         // flache Decke (1/16 hoch)
         float top = y + 0.0625F;
         if (dy >= 0) {
            return null;
         }

         float tt = (top - oy) / dy;
         float px = ox + dx * tt;
         float pz = oz + dz * tt;
         if (px < x || px > x + 1 || pz < z || pz > z + 1) {
            return null;
         }

         Hit h = new Hit();
         h.t = tt;
         h.x = x;
         h.y = y;
         h.z = z;
         h.face = 3;
         h.block = b;
         h.u = px - x;
         h.v = pz - z;
         Tex w = this.t((x + z & 1) == 0 ? "block/pink_wool" : "block/red_wool");
         h.color = w.at((int)(h.u * w.w), (int)(h.v * w.h));
         return h;
      }

      if (face < 0) {
         return null;
      }

      float px = ox + dx * t;
      float py = oy + dy * t;
      float pz = oz + dz * t;
      float u;
      float v;
      switch (face) {
         case 0, 1 -> {
            u = pz - z;
            v = 1.0F - (py - y);
         }
         case 2, 3 -> {
            u = px - x;
            v = pz - z;
         }
         default -> {
            u = px - x;
            v = 1.0F - (py - y);
         }
      }

      u = Math.max(0.0F, Math.min(0.999F, u));
      v = Math.max(0.0F, Math.min(0.999F, v));
      Tex img = this.texture(b, face);
      int c = img.at((int)(u * img.w), (int)(v * img.h));
      if ((b == CHERRY_LEAVES || b == OAK_LEAVES) && c >>> 24 < 100) {
         return null;
      }

      Hit h = new Hit();
      h.t = t;
      h.x = x;
      h.y = y;
      h.z = z;
      h.face = face;
      h.block = b;
      h.u = u;
      h.v = v;
      h.color = c;
      return h;
   }

   private static int mulTint(int c, float r, float g, float b) {
      return c & 0xFF000000 | Tex.clamp255((c >> 16 & 255) * r) << 16 | Tex.clamp255((c >> 8 & 255) * g) << 8 | Tex.clamp255((c & 255) * b);
   }

   private Tex texture(byte b, int face) {
      boolean top = face == 2 || face == 3;
      return switch (b) {
         case GRASS -> top ? (face == 3 ? this.grassTop : this.t("block/dirt")) : this.grassSide;
         case DIRT -> this.t("block/dirt");
         case ANDESITE -> this.t("block/andesite");
         case SAND -> this.t("block/sand");
         case GRAVEL -> this.t("block/gravel");
         case MOSSY -> this.t("block/mossy_cobblestone");
         case CHERRY_LOG -> this.t(top ? "block/cherry_log_top" : "block/cherry_log");
         case OAK_LOG -> this.t(top ? "block/oak_log_top" : "block/oak_log");
         case CHERRY_LEAVES -> this.leavesCherry;
         case OAK_LEAVES -> this.leavesOak;
         default -> this.t("block/stone");
      };
   }

   /** Farbe eines Treffers mit Sonne (inkl. Schatten), Himmelslicht und Dunst. */
   private float[] shade(Hit h, float dist, boolean reflected) {
      int c = h.color;
      float r = (c >> 16 & 255) / 255.0F;
      float g = (c >> 8 & 255) / 255.0F;
      float b = (c & 255) / 255.0F;
      int[] n = NORMAL[h.face];
      boolean plant = h.block >= PLANT0 || h.block == CHERRY_LEAVES || h.block == OAK_LEAVES;
      float ndl = plant ? 0.55F : Math.max(0.0F, n[0] * this.sun[0] + n[1] * this.sun[1] + n[2] * this.sun[2]);
      float direct = 0.0F;
      if (ndl > 0.0F && !reflected) {
         float sx = h.x + 0.5F + n[0] * 0.51F;
         float sy = h.y + 0.5F + n[1] * 0.51F;
         float sz = h.z + 0.5F + n[2] * 0.51F;
         if (h.block >= PLANT0 || h.block == CARPET) {
            sy = h.y + 0.5F;
         }

         Hit s = this.trace(sx, sy, sz, this.sun[0], this.sun[1], this.sun[2], 80.0F, true);
         direct = s == null ? ndl : 0.0F;
      } else if (reflected) {
         direct = ndl * 0.7F;
      }

      // Himmelslicht: oben mehr Zenit, Seiten mehr Horizont; einfache Umgebungsverdeckung
      float up = n[1] > 0 ? 1.0F : (n[1] < 0 ? 0.35F : 0.7F);
      float ao = 1.0F;
      if (!plant) {
         int occ = 0;

         for (int k = 0; k < 4; k++) {
            int ax = h.x + n[0] + (k == 0 ? 1 : (k == 1 ? -1 : 0)) * (n[0] == 0 ? 1 : 0);
            int ay = h.y + n[1] + (n[1] == 0 && k >= 2 ? (k == 2 ? 1 : -1) : 0);
            int az = h.z + n[2] + (k >= 2 && n[2] == 0 && n[1] != 0 ? (k == 2 ? 1 : -1) : 0);
            byte o = this.get(ax, ay, az);
            if (o != AIR && o < PLANT0 && o != CARPET) {
               occ++;
            }
         }

         ao = 1.0F - occ * 0.12F;
      }

      float[] col = new float[3];
      float[] amb = new float[3];

      for (int i = 0; i < 3; i++) {
         amb[i] = (this.zenith[i] * up + this.horizon[i] * (1.0F - up) * 0.8F) * this.ambient * 1.25F;
      }

      // Gegenlicht: Kanten zur Sonne bekommen einen warmen Saum
      float rim = Math.max(0.0F, -(n[2])) < 0.5F && n[1] == 0 ? 0.0F : 0.0F;
      float[] alb = {r, g, b};

      for (int i = 0; i < 3; i++) {
         col[i] = alb[i] * (amb[i] * ao + this.sunCol[i] * direct * 0.95F + rim);
      }

      // Dunst zum Horizont
      float f = 1.0F - (float)Math.exp(-dist / (reflected ? 140.0F : 170.0F));
      float[] hz = this.sky(0.0F, 0.02F, 1.0F);

      for (int i = 0; i < 3; i++) {
         col[i] = col[i] + (hz[i] * 0.92F - col[i]) * f;
      }

      return col;
   }

   private float[] waterColor(float ox, float oy, float oz, float dx, float dy, float dz, float t, int px, int py) {
      float hx = ox + dx * t;
      float hz = oz + dz * t;
      // kleine Wellen: Normale leicht verwackelt
      float wx = (this.noise.noise(hx * 0.9F, hz * 0.45F, 9.0F) - 0.5F) * 0.16F;
      float wz = (this.noise.noise(hx * 0.45F, hz * 0.9F, 11.0F) - 0.5F) * 0.16F;
      float[] nn = norm(wx, 1.0F, wz);
      float dn = dx * nn[0] + dy * nn[1] + dz * nn[2];
      float rx = dx - 2 * dn * nn[0];
      float ry = Math.abs(dy - 2 * dn * nn[1]);
      float rz = dz - 2 * dn * nn[2];
      float cos = Math.abs(dy);
      float fres = 0.05F + 0.95F * (float)Math.pow(1.0F - cos, 5.0);
      fres = Math.min(1.0F, fres * 1.15F);
      float[] refl;
      Hit rh = this.trace(hx, WATER_Y + 0.01F, hz, rx, ry, rz, 120.0F, false);
      if (rh != null) {
         refl = this.shade(rh, t + rh.t, true);
      } else {
         refl = this.sky(rx, ry, rz);
      }

      // Unterwasser: Sand durchscheinen lassen (seicht am Strand)
      float[] body = {0.06F * this.ambient * 2.0F, 0.22F * this.ambient * 2.0F, 0.3F * this.ambient * 2.0F};
      Hit floor = this.trace(hx, WATER_Y - 0.01F, hz, dx * 0.8F, dy, dz * 0.8F, 12.0F, false);
      if (floor != null) {
         float depth = floor.t * Math.abs(dy);
         float clear = (float)Math.exp(-depth * 0.55F);
         float[] fc = this.shade(floor, t + floor.t, true);

         for (int i = 0; i < 3; i++) {
            body[i] = body[i] + (fc[i] * new float[]{0.55F, 0.85F, 0.9F}[i] - body[i]) * clear;
         }
      }

      float[] c = new float[3];

      for (int i = 0; i < 3; i++) {
         c[i] = body[i] * (1.0F - fres) + refl[i] * fres;
      }

      float f = 1.0F - (float)Math.exp(-t / 260.0F);
      float[] hz2 = this.sky(0.0F, 0.0F, 1.0F);

      for (int i = 0; i < 3; i++) {
         c[i] = c[i] + (hz2[i] * 0.85F - c[i]) * f * 0.6F;
      }

      return c;
   }

   private static int pack(float[] c) {
      // sanftes Abrollen heller Werte
      float r = c[0] / (1.0F + c[0] * 0.18F) * 1.12F;
      float g = c[1] / (1.0F + c[1] * 0.18F) * 1.12F;
      float b = c[2] / (1.0F + c[2] * 0.18F) * 1.12F;
      return 0xFF000000 | Tex.clamp255(r * 255.0F) << 16 | Tex.clamp255(g * 255.0F) << 8 | Tex.clamp255(b * 255.0F);
   }

   private void renderMood(Tex out) {
      Cam cam = CAM;
      IntStream.range(0, VH).parallel().forEach(py -> {
         float[] d = new float[3];

         for (int px = 0; px < VW; px++) {
            cam.ray(px + 0.5F, py + 0.5F, d);
            float maxT = 400.0F;
            float tw = d[1] < 0 ? (WATER_Y - cam.y) / d[1] : Float.MAX_VALUE;
            Hit h = this.trace(cam.x, cam.y, cam.z, d[0], d[1], d[2], Math.min(maxT, tw), false);
            int col;
            if (h != null && h.t < tw) {
               col = pack(this.shade(h, h.t, false));
            } else if (tw < Float.MAX_VALUE) {
               col = pack(this.waterColor(cam.x, cam.y, cam.z, d[0], d[1], d[2], tw, px, py));
               this.water[py * VW + px] = true;
            } else {
               col = 0;
            }

            out.px[py * VW + px] = col;
         }
      });
   }

   public void render() {
      this.mood(0);
      this.renderMood(this.dawn);
      this.mood(1);
      this.renderMood(this.golden);
      float[] p = new float[3];
      CAM.project(-SEAT_DX, SEAT_Y, SEAT_Z, p);
      this.seat[0] = p[0];
      this.seat[2] = p[1];
      this.seat[3] = CAM.scale(p[2]);
      CAM.project(SEAT_DX, SEAT_Y, SEAT_Z, p);
      this.seat[1] = p[0];
      // Kante (Vorderkante der Decke) liegt bei z = 2
      CAM.project(0.0F, SEAT_Y, 2.0F, p);
      this.seat[2] = p[1];
   }

   /** Horizont-Höhe im Bild (für Sonne und Himmel). */
   public static float horizonY() {
      float[] p = new float[3];
      CAM.project(0.0F, WATER_Y, 5000.0F, p);
      return p[1];
   }

   /** Pixel der Wasseroberfläche in Sonnenrichtung, für Glitzerpunkte. */
   public List<int[]> sparkleSpots(float sunX, int count, long seed) {
      java.util.Random r = new java.util.Random(seed);
      List<int[]> out = new ArrayList<>();
      int tries = 0;

      while (out.size() < count && tries++ < count * 60) {
         int y = (int)(horizonY() + 1 + Math.pow(r.nextFloat(), 1.6) * (VH - horizonY() - 2));
         float spread = 6.0F + (y - horizonY()) * 0.55F;
         int x = Math.round(sunX + (float)r.nextGaussian() * spread);
         if (x >= 0 && x < VW && y >= 0 && y < VH && this.water[y * VW + x]) {
            out.add(new int[]{x, y, r.nextInt(1000)});
         }
      }

      return out;
   }
}
