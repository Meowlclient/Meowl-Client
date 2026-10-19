package com.lennart.nebula.afk.valley;

import com.lennart.nebula.afk.space.Cam;
import com.lennart.nebula.afk.space.Noise;
import com.lennart.nebula.afk.space.Tex;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * „Tal des Endes“ als Minecraft-Landschaft: Schlucht, Wasserfall und die beiden Statuen werden als Blockwelt
 * (1 Zelle = 1 Block, echte Blocktexturen aus dem aktiven Ressourcenpaket) aufgebaut und einmalig per Raytracing in
 * ein Bild gerendert – Sonnenlicht mit Schatten, Minecraft-Umgebungsverdeckung, spiegelndes Wasser, Dunst, Berge und
 * Wolken. Wasserfall und Fluss bekommen zusätzlich animierte Bildfolgen (echte Wasser-Animation von Minecraft), ein
 * Tiefenpuffer lässt die Kämpfer hinter Statuen und Felsen verschwinden.
 */
public final class ValleyTracer {
   public static final int VW = 960;
   public static final int VH = 540;
   /** Kamera weit hinter dem Spieler, leicht nach unten geneigt. */
   public static final Cam CAM = new Cam(VW, VH, 0.0F, 26.0F, -44.0F, -5.0F, 44.0F);
   /** Füße des Spielers auf dem Felsplateau im Vordergrund. */
   public static final float PLAYER_X = -6.5F;
   public static final float PLAYER_Y = 19.0F;
   public static final float PLAYER_Z = -24.5F;
   public static final float STATUE_X = 19.5F;
   public static final float STATUE_Z = 54.0F;
   public static final float FOOT_Y = 3.0F;
   /** Vorderseite des Wasserfalls (Zellen z = 69 liegen vor der Wand bei z = 70). */
   public static final float FALL_FRONT = 69.0F;
   public static final float FALL_TOP = 44.0F;
   public static final int FALL_HALF = 8;
   public static final int FALL_FRAMES = 16;
   public static final int RIVER_FRAMES = 8;
   public static final String[] BLOCKS = {
      "stone", "andesite", "mossy_cobblestone", "mossy_stone_bricks", "stone_bricks", "grass_block_top", "grass_block_side",
      "grass_block_side_overlay", "dirt", "oak_leaves", "oak_log", "oak_log_top", "water_still", "water_flow", "gravel", "moss_block",
      "tuff", "cracked_stone_bricks", "cobblestone", "chiseled_stone_bricks"
   };
   static final int X0 = -80;
   static final int Y0 = -6;
   static final int Z0 = -52;
   static final int NX = 160;
   static final int NY = 66;
   static final int NZ = 196;
   static final byte AIR = 0;
   static final byte STONE = 1;
   static final byte ANDESITE = 2;
   static final byte MCOBBLE = 3;
   static final byte TUFF = 4;
   static final byte GRASS = 5;
   static final byte DIRT = 6;
   static final byte LEAVES = 7;
   static final byte LOG = 8;
   static final byte WATER = 9;
   static final byte FALL = 10;
   static final byte GRAVEL = 11;
   static final byte MOSS = 12;
   static final byte COBBLE = 13;
   static final byte S_STONE = 14;
   static final byte S_ANDESITE = 15;
   static final byte S_MCOBBLE = 16;
   static final byte S_MBRICKS = 17;
   static final byte S_BRICKS = 18;
   static final byte S_TUFF = 19;
   static final byte S_CRACKED = 20;
   static final byte S_CHISELED = 21;
   static final byte ROCK = 22;
   private static final int MATS = 23;
   // Licht
   private static final float[] SUN = norm(-0.48F, 0.74F, -0.47F);
   private static final float[] SUN_COLOR = {1.0F, 0.93F, 0.8F};
   private static final float[] SKY_COLOR = {0.44F, 0.53F, 0.68F};
   private static final float[] HAZE = {0.72F, 0.82F, 0.93F};
   private static final float[] FACE_SHADE = {0.62F, 0.62F, 0.5F, 1.0F, 0.8F, 0.8F};
   private static final int[][] NORMAL = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
   private final byte[] grid = new byte[NX * NY * NZ];
   private final Noise noise = new Noise(4242L);
   private final Tex[] tex = new Tex[MATS];
   private final float[][] tint = new float[MATS][];
   private final Tex grassSide;
   private final Tex grassOverlay;
   private final Tex logTop;
   private final Tex waterStill;
   private final Tex waterFlow;
   private final int leafFill;
   // Ergebnisse
   public final Tex image = new Tex(VW, VH);
   /** Euklidischer Abstand von der Kamera je Pixel (Himmel = sehr groß). */
   public final float[] depth = new float[VW * VH];
   /** Animationsbilder: Bildfolgen untereinander; Box = {x, y, w, h} im Bild. */
   public Tex fallAtlas;
   public Tex riverAtlas;
   public final int[] fallBox = new int[4];
   public final int[] riverBox = new int[4];
   // Daten der Wasserpixel für die Animation
   private int waterCount;
   private int[] wPix;
   private byte[] wKind;
   private float[] wU;
   private float[] wV;
   private float[] wLight;
   private float[] wRefl;
   private float[] wFres;
   private float[] wFoam;
   private float[] wHaze;

   public ValleyTracer(Function<String, Tex> textures) {
      Tex stone = this.load(textures, "stone");
      this.mat(STONE, stone, 1.0F, 1.0F, 1.0F);
      this.mat(ROCK, stone, 1.0F, 1.0F, 1.0F);
      this.mat(ANDESITE, this.load(textures, "andesite"), 1.0F, 1.0F, 1.0F);
      this.mat(MCOBBLE, this.load(textures, "mossy_cobblestone"), 1.0F, 1.0F, 1.0F);
      this.mat(TUFF, this.load(textures, "tuff"), 1.0F, 1.0F, 1.0F);
      this.mat(GRASS, this.load(textures, "grass_block_top"), 0.5F, 0.76F, 0.36F);
      this.mat(DIRT, this.load(textures, "dirt"), 1.0F, 1.0F, 1.0F);
      this.mat(LEAVES, this.load(textures, "oak_leaves"), 0.36F, 0.66F, 0.22F);
      this.mat(LOG, this.load(textures, "oak_log"), 1.0F, 1.0F, 1.0F);
      this.mat(GRAVEL, this.load(textures, "gravel"), 1.0F, 1.0F, 1.0F);
      this.mat(MOSS, this.load(textures, "moss_block"), 1.0F, 1.0F, 1.0F);
      this.mat(COBBLE, this.load(textures, "cobblestone"), 1.0F, 1.0F, 1.0F);
      // Statuen: verwitterter, grünlicher Stein
      float sr = 0.66F;
      float sg = 0.78F;
      float sb = 0.68F;
      this.mat(S_STONE, stone, sr, sg, sb);
      this.mat(S_ANDESITE, this.tex[ANDESITE], sr, sg, sb);
      this.mat(S_MCOBBLE, this.tex[MCOBBLE], sr, sg, sb);
      this.mat(S_MBRICKS, this.load(textures, "mossy_stone_bricks"), sr, sg, sb);
      this.mat(S_BRICKS, this.load(textures, "stone_bricks"), sr, sg, sb);
      this.mat(S_TUFF, this.tex[TUFF], sr * 0.72F, sg * 0.78F, sb * 0.72F);
      this.mat(S_CRACKED, this.load(textures, "cracked_stone_bricks"), sr, sg, sb);
      this.mat(S_CHISELED, this.load(textures, "chiseled_stone_bricks"), sr, sg, sb);
      this.mat(WATER, Tex.solid(-1), 1.0F, 1.0F, 1.0F);
      this.mat(FALL, Tex.solid(-1), 1.0F, 1.0F, 1.0F);
      this.grassSide = this.load(textures, "grass_block_side");
      Tex overlay = textures.apply("grass_block_side_overlay");
      this.grassOverlay = overlay != null ? overlay.firstFrame() : null;
      this.logTop = this.load(textures, "oak_log_top");
      Tex still = textures.apply("water_still");
      Tex flow = textures.apply("water_flow");
      this.waterStill = still != null ? still : Tex.solid(-5592406);
      this.waterFlow = flow != null ? flow : Tex.solid(-5592406);
      this.leafFill = darken(this.tex[LEAVES].average(), 0.45F);
      this.build();
   }

   private Tex load(Function<String, Tex> textures, String name) {
      Tex t = textures.apply(name);
      return t != null ? t.firstFrame() : Tex.solid(-8355712);
   }

   private void mat(byte id, Tex t, float r, float g, float b) {
      this.tex[id] = t;
      this.tint[id] = new float[]{r, g, b};
   }

   // ================================================================== Welt

   private static int idx(int x, int y, int z) {
      return ((x - X0) * NY + (y - Y0)) * NZ + (z - Z0);
   }

   private static boolean inside(int x, int y, int z) {
      return x >= X0 && y >= Y0 && z >= Z0 && x < X0 + NX && y < Y0 + NY && z < Z0 + NZ;
   }

   byte get(int x, int y, int z) {
      if (inside(x, y, z)) {
         return this.grid[idx(x, y, z)];
      } else {
         return y < Y0 ? STONE : AIR;
      }
   }

   private void set(int x, int y, int z, byte m) {
      if (inside(x, y, z)) {
         this.grid[idx(x, y, z)] = m;
      }
   }

   /** Höhe der obersten Oberfläche an einer Stelle (Wasser = 0) – Landepunkte der Kämpfer. */
   public float groundY(float x, float z) {
      int ix = (int)Math.floor(x);
      int iz = (int)Math.floor(z);

      for (int y = Y0 + NY - 1; y >= Y0; y--) {
         byte m = this.get(ix, y, iz);
         if (m != AIR && m != FALL) {
            return y + 1;
         }
      }

      return 0.0F;
   }

   private float n2(float a, float b, float salt) {
      return (this.noise.fbm(a, b, salt, 3) - 0.5F) * 2.0F;
   }

   private void build() {
      // 1) Fels, Fluss, Wasserfall
      for (int x = X0; x < X0 + NX; x++) {
         for (int z = Z0; z < Z0 + NZ; z++) {
            float xc = x + 0.5F;
            float zc = z + 0.5F;
            float ax = Math.abs(xc);
            float sideTop = 48.0F + 7.0F * this.n2(xc / 24.0F, zc / 24.0F, 3.0F);
            float backTop = 47.0F + 4.0F * this.n2(xc / 16.0F, 0.0F, 9.0F) + Math.max(0.0F, ax - 20.0F) * 0.08F;

            for (int y = Y0; y < Y0 + NY; y++) {
               float yc = y + 0.5F;
               boolean rock;
               if (y < -3) {
                  rock = true;
               } else {
                  float wx = 28.5F + 5.5F * this.n2(zc / 15.0F, yc / 11.0F, 1.0F) + Math.max(0.0F, yc) * 0.1F + Math.max(0.0F, 34.0F - zc) * 0.55F;
                  boolean side = ax > wx && yc < sideTop;
                  float bz;
                  if (ax < FALL_HALF + 1.5F) {
                     bz = 70.0F;
                  } else {
                     bz = 70.0F + 3.5F * this.n2(xc / 9.0F, yc / 7.0F, 5.0F) - (ax < 16.0F ? (16.0F - ax) * 0.35F : 0.0F);
                  }

                  boolean back = zc > bz && yc < backTop;
                  rock = side || back;
                  if (back && ax < FALL_HALF && zc > 70.0F && yc > 42.0F) {
                     rock = false;
                  }
               }

               if (rock) {
                  this.set(x, y, z, ROCK);
               } else if (y <= -1) {
                  this.set(x, y, z, WATER);
               } else if (ax < FALL_HALF && z >= 70 && (y == 42 || y == 43)) {
                  this.set(x, y, z, WATER);
               } else if (z == 69 && x >= -FALL_HALF && x < FALL_HALF && y >= 0 && y <= 43) {
                  this.set(x, y, z, FALL);
               }
            }
         }
      }

      // 2) Felsinseln unter den Statuen und das Plateau des Spielers
      for (int side = -1; side <= 1; side += 2) {
         float cx = side * STATUE_X;

         for (int x = (int)cx - 14; x <= (int)cx + 14; x++) {
            for (int z = (int)STATUE_Z - 13; z <= (int)STATUE_Z + 13; z++) {
               float dx = (x + 0.5F - cx) / 10.5F;
               float dz = (z + 0.5F - STATUE_Z) / 10.0F;
               float d = dx * dx + dz * dz;
               float edge = 1.0F + 0.35F * this.n2(x / 3.0F, z / 3.0F, 11.0F);
               if (d < edge) {
                  int top = d < 0.55F ? 2 : (int)(2.0F - (d - 0.55F) * 6.0F + this.noise.rand(x, 7, z) * 1.5F);

                  for (int y = -3; y <= top; y++) {
                     this.set(x, y, z, ROCK);
                  }
               }
            }
         }
      }

      for (int x = -8; x <= -5; x++) {
         for (int z = -26; z <= -23; z++) {
            boolean corner = (x == -8 || x == -5) && (z == -26 || z == -23);
            int top = corner ? 17 : 18;

            for (int y = -3; y <= top; y++) {
               this.set(x, y, z, ROCK);
            }
         }
      }

      // 3) Oberflächen und Gesteinsarten
      for (int x = X0; x < X0 + NX; x++) {
         for (int z = Z0; z < Z0 + NZ; z++) {
            int cover = 99;

            for (int y = Y0 + NY - 1; y >= Y0; y--) {
               byte c = this.get(x, y, z);
               if (c != ROCK) {
                  cover = c == AIR ? 0 : 99;
               } else {
                  this.set(x, y, z, this.rockMaterial(x, y, z, cover));
                  cover = cover == 99 ? 99 : cover + 1;
               }
            }
         }
      }

      // Plateau oben mit Gras
      for (int x = -8; x <= -5; x++) {
         for (int z = -26; z <= -23; z++) {
            for (int y = 18; y >= 14; y--) {
               if (this.get(x, y, z) != AIR) {
                  this.set(x, y, z, GRASS);
                  this.set(x, y - 1, z, DIRT);
                  break;
               }
            }
         }
      }

      // 4) Bäume auf den Klippen
      for (int x = X0 + 3; x < X0 + NX - 3; x++) {
         for (int z = Z0 + 3; z < Z0 + NZ - 3; z++) {
            if (this.noise.rand(x, 99, z) < 0.022F && !(Math.abs(x) < 12 && z > 64)) {
               for (int y = Y0 + NY - 8; y > 24; y--) {
                  byte c = this.get(x, y, z);
                  if (c != AIR) {
                     if (c == GRASS) {
                        this.tree(x, y + 1, z);
                     }
                     break;
                  }
               }
            }
         }
      }

      // 5) Statuen: links Madara (schaut nach rechts), rechts Hashirama (schaut nach links)
      this.statue(-STATUE_X, 1, 0);
      this.statue(STATUE_X, -1, 1);
   }

   private byte rockMaterial(int x, int y, int z, int cover) {
      float r = this.noise.rand(x, y, z);
      if (cover == 0) {
         if (y < 0) {
            return GRAVEL;
         } else if (y >= 20) {
            return GRASS;
         } else {
            return r < 0.45F ? MOSS : (r < 0.75F ? GRASS : MCOBBLE);
         }
      } else if (cover >= 1 && cover <= 3 && y >= 18) {
         return DIRT;
      } else {
         float a = this.noise.fbm(x / 7.0F, y / 7.0F, z / 7.0F, 2);
         float wet = (6.0F + 4.0F * this.n2(x / 9.0F, z / 9.0F, 21.0F) - y) / 6.0F;
         if (Math.abs(x) < 16 && z > 60 && y < 38) {
            wet = Math.max(wet, 0.55F);
         }

         if (r < wet * 0.75F) {
            return MCOBBLE;
         } else if (a > 0.63F) {
            return ANDESITE;
         } else if (a < 0.34F) {
            return r < 0.5F ? TUFF : COBBLE;
         } else {
            return STONE;
         }
      }
   }

   private void tree(int x, int y, int z) {
      int h = 4 + (int)(this.noise.rand(x, 5, z) * 3.0F);

      for (int dy = h - 2; dy <= h + 1; dy++) {
         int rad = dy >= h ? 1 : 2;

         for (int dx = -rad; dx <= rad; dx++) {
            for (int dz = -rad; dz <= rad; dz++) {
               boolean corner = Math.abs(dx) == rad && Math.abs(dz) == rad;
               if ((!corner || this.noise.rand(x + dx, y + dy, z + dz) < 0.4F && dy < h) && this.get(x + dx, y + dy, z + dz) == AIR) {
                  this.set(x + dx, y + dy, z + dz, LEAVES);
               }
            }
         }
      }

      for (int dy = 0; dy < h; dy++) {
         this.set(x, y + dy, z, LOG);
      }

      this.set(x, y - 1, z, DIRT);
   }

   // ------------------------------------------------------------------ Statuen

   /** Vergrößerung der Statuen gegenüber dem Grundmodell (ca. 31 Blöcke hoch). */
   static final float STATUE_SCALE = 1.36F;
   /** Drehung der Statuen zur Kamera (Grad). */
   static final float STATUE_TURN = 35.0F;

   private void statue(float cx, int dir, int type) {
      float k = STATUE_SCALE;
      // leicht zur Kamera gedreht (Dreiviertelansicht wie im Anime)
      double a = Math.toRadians(STATUE_TURN);
      float fx = (float)Math.cos(a) * dir;
      float fz = (float)-Math.sin(a);
      float sx = (float)Math.sin(a) * dir;
      float sz = (float)Math.cos(a);

      for (int x = (int)cx - 22; x <= (int)cx + 22; x++) {
         for (int z = (int)STATUE_Z - 18; z <= (int)STATUE_Z + 18; z++) {
            for (int y = (int)FOOT_Y; y < (int)FOOT_Y + 46; y++) {
               float dx = x + 0.5F - cx;
               float dz = z + 0.5F - STATUE_Z;
               float f = (dx * fx + dz * fz) / k;
               float s = (dx * sx + dz * sz) / k;
               float h = (y + 0.5F - FOOT_Y) / k;
               byte m = this.statueCell(type, f, h, s, x, y, z);
               if (m != AIR) {
                  this.set(x, y, z, m);
               }
            }
         }
      }
   }

   private static boolean box(float f, float h, float s, float f0, float f1, float h0, float h1, float s0, float s1) {
      return f >= f0 && f <= f1 && h >= h0 && h <= h1 && s >= s0 && s <= s1;
   }

   private static boolean capsule(float f, float h, float s, float af, float ah, float as, float bf, float bh, float bs, float r) {
      float vf = bf - af;
      float vh = bh - ah;
      float vs = bs - as;
      float len2 = vf * vf + vh * vh + vs * vs;
      float t = len2 <= 0.0F ? 0.0F : Math.max(0.0F, Math.min(1.0F, ((f - af) * vf + (h - ah) * vh + (s - as) * vs) / len2));
      float df = f - (af + vf * t);
      float dh = h - (ah + vh * t);
      float ds = s - (as + vs * t);
      return df * df + dh * dh + ds * ds <= r * r;
   }

   private byte mix(byte a, byte b, float p, int x, int y, int z) {
      return this.noise.rand(x * 3 + 1, y * 5, z * 7) < p ? b : a;
   }

   /** Lokale Koordinaten: f = nach vorn (Blickrichtung), h = Höhe über den Füßen, s = seitlich (negativ = zur Kamera). */
   private byte statueCell(int type, float f, float h, float s, int x, int y, int z) {
      float as = Math.abs(s);
      // Haare
      if (type == 0) {
         float df = f + 2.4F;
         float dh = h - 22.0F;
         float theta = (float)Math.atan2(dh, df);
         float spike = 1.0F + 0.3F * Math.max(0.0F, (float)Math.sin(theta * 8.0F + s * 0.8F)) + 0.08F * this.n2(s, dh / 3.0F, 31.0F);
         float q = df * df / (3.7F * 3.7F) + dh * dh / (8.6F * 8.6F) + s * s / (4.5F * 4.5F);
         boolean mass = q < spike * spike && (f < 1.0F || h > 27.0F && f < 1.8F) && h > 12.0F;
         boolean bangs = box(f, h, s, 1.0F, 2.6F, 24.5F, 28.5F, -2.6F, -0.4F);
         if (mass || bangs || box(f, h, s, -2.6F, 1.8F, 27.0F, 29.6F, -2.8F, 2.8F)) {
            return this.mix(S_TUFF, S_MCOBBLE, 0.35F, x, y, z);
         }
      } else {
         boolean back = box(f, h, s, -3.2F, -1.0F, 12.5F, 28.5F, -2.8F, 2.8F) && (h > 14.0F || as < 2.0F);
         boolean cap = box(f, h, s, -2.6F, 2.0F, 27.5F, 29.5F, -2.7F, 2.7F);
         boolean locks = box(f, h, s, -1.0F, 1.6F, 20.5F, 27.6F, -3.0F, 3.0F) && as > 2.2F;
         if (back || cap || locks) {
            return this.mix(S_TUFF, S_MCOBBLE, 0.3F, x, y, z);
         }
      }

      // Kopf
      if (box(f, h, s, -2.2F, 2.3F, 23.0F, 28.0F, -2.3F, 2.3F)) {
         if (f > 1.0F && h > 25.0F && h < 26.0F) {
            return S_TUFF;
         }

         return S_STONE;
      }

      if (box(f, h, s, -1.0F, 1.2F, 22.0F, 23.0F, -1.3F, 1.3F)) {
         return S_STONE;
      }

      // erhobener Arm mit Fingerzeichen (zur Kamera), anderer Arm hängt
      if (capsule(f, h, s, 0.3F, 20.5F, -4.4F, 3.2F, 16.0F, -3.7F, 1.8F) || capsule(f, h, s, 3.2F, 16.0F, -3.7F, 4.0F, 22.3F, -1.9F, 1.4F)) {
         return this.mix(S_BRICKS, S_MBRICKS, 0.45F, x, y, z);
      }

      if (capsule(f, h, s, 4.0F, 22.0F, -1.9F, 4.0F, 24.0F, -1.9F, 1.05F) || capsule(f, h, s, 4.0F, 24.0F, -1.7F, 4.0F, 27.2F, -1.6F, 0.62F)) {
         return S_STONE;
      }

      if (capsule(f, h, s, 0.0F, 20.5F, 4.4F, 1.2F, 15.0F, 5.0F, 1.7F) || capsule(f, h, s, 1.2F, 15.0F, 5.0F, 2.3F, 12.5F, 3.8F, 1.35F)) {
         return this.mix(S_BRICKS, S_MBRICKS, 0.45F, x, y, z);
      }

      // Schulterpanzer
      if (as >= 3.6F && as <= 6.9F && f >= -3.2F && f <= 3.0F && h >= 18.6F && h <= 23.6F - (as - 3.6F) * 0.55F) {
         return ((int)h & 1) == 0 ? S_ANDESITE : S_CRACKED;
      }

      // Oberkörper mit Panzerplatten
      if (box(f, h, s, -2.8F, 2.6F, 14.0F, 22.0F, -4.2F, 4.2F)) {
         if (f > 2.0F) {
            return h > 18.0F ? S_CHISELED : S_BRICKS;
         }

         return ((int)h & 1) == 0 ? S_ANDESITE : S_STONE;
      }

      // Gürtel
      if (box(f, h, s, -3.0F, 2.9F, 13.0F, 14.0F, -4.7F, 4.7F)) {
         return S_TUFF;
      }

      // Panzerrock über dem Gewand
      if (box(f, h, s, -3.3F, 3.0F, 8.5F, 13.0F, -5.0F, 5.0F) && (as > 1.0F || f < 2.0F)) {
         return ((int)(h * 1.0F) & 1) == 0 ? S_ANDESITE : S_CRACKED;
      }

      // Gewand und Beine
      float robe = 4.6F + (h < 4.0F ? 0.6F : 0.0F);
      if (h >= 1.5F && h < 13.0F && as <= robe && f >= -2.8F && f <= 2.6F) {
         if (f > 1.9F && as < 0.8F) {
            return S_TUFF;
         }

         return this.mix(S_BRICKS, S_MBRICKS, 0.5F, x, y, z);
      }

      // Füße
      if (h >= 0.0F && h < 1.5F && as >= 1.2F && as <= 4.0F && f >= -1.5F && f <= 3.5F) {
         return S_STONE;
      }

      return AIR;
   }

   // ================================================================== Raytracing

   private static final class Ray {
      int x;
      int y;
      int z;
      int face;
      float t;
      byte mat;
   }

   private boolean solid(byte m, boolean seeWater) {
      return m != AIR && (seeWater || m != WATER && m != FALL);
   }

   /** DDA-Durchlauf durch das Gitter; true = getroffen. */
   private boolean march(float ox, float oy, float oz, float dx, float dy, float dz, float maxT, boolean seeWater, Ray r) {
      float bx0 = X0;
      float by0 = Y0;
      float bz0 = Z0;
      float bx1 = X0 + NX;
      float by1 = Y0 + NY;
      float bz1 = Z0 + NZ;
      float tmin = 0.0F;
      float tmax = maxT;
      int face = -1;
      float[] o = {ox, oy, oz};
      float[] d = {dx, dy, dz};
      float[] lo = {bx0, by0, bz0};
      float[] hi = {bx1, by1, bz1};

      for (int a = 0; a < 3; a++) {
         if (Math.abs(d[a]) < 1.0E-9F) {
            if (o[a] < lo[a] || o[a] > hi[a]) {
               return false;
            }
         } else {
            float t0 = (lo[a] - o[a]) / d[a];
            float t1 = (hi[a] - o[a]) / d[a];
            int f = d[a] > 0.0F ? a * 2 : a * 2 + 1;
            if (t0 > t1) {
               float tmp = t0;
               t0 = t1;
               t1 = tmp;
            }

            if (t0 > tmin) {
               tmin = t0;
               face = f;
            }

            tmax = Math.min(tmax, t1);
         }
      }

      if (tmin > tmax) {
         return false;
      }

      float t = tmin + 1.0E-4F;
      int x = (int)Math.floor(ox + dx * t);
      int y = (int)Math.floor(oy + dy * t);
      int z = (int)Math.floor(oz + dz * t);
      int sx = dx > 0.0F ? 1 : -1;
      int sy = dy > 0.0F ? 1 : -1;
      int sz = dz > 0.0F ? 1 : -1;
      float tdx = Math.abs(dx) < 1.0E-9F ? Float.MAX_VALUE : Math.abs(1.0F / dx);
      float tdy = Math.abs(dy) < 1.0E-9F ? Float.MAX_VALUE : Math.abs(1.0F / dy);
      float tdz = Math.abs(dz) < 1.0E-9F ? Float.MAX_VALUE : Math.abs(1.0F / dz);
      float tx = Math.abs(dx) < 1.0E-9F ? Float.MAX_VALUE : ((sx > 0 ? x + 1 : x) - ox) / dx;
      float ty = Math.abs(dy) < 1.0E-9F ? Float.MAX_VALUE : ((sy > 0 ? y + 1 : y) - oy) / dy;
      float tz = Math.abs(dz) < 1.0E-9F ? Float.MAX_VALUE : ((sz > 0 ? z + 1 : z) - oz) / dz;
      float tEnter = tmin;

      for (int i = 0; i < 1200; i++) {
         if (!inside(x, y, z) || tEnter > tmax) {
            return false;
         }

         byte m = this.grid[idx(x, y, z)];
         if (this.solid(m, seeWater) && tEnter > 1.0E-3F) {
            r.x = x;
            r.y = y;
            r.z = z;
            r.t = tEnter;
            r.face = face < 0 ? 3 : face;
            r.mat = m;
            return true;
         }

         if (tx < ty && tx < tz) {
            tEnter = tx;
            x += sx;
            tx += tdx;
            face = sx > 0 ? 0 : 1;
         } else if (ty < tz) {
            tEnter = ty;
            y += sy;
            ty += tdy;
            face = sy > 0 ? 2 : 3;
         } else {
            tEnter = tz;
            z += sz;
            tz += tdz;
            face = sz > 0 ? 4 : 5;
         }
      }

      return false;
   }

   private boolean occl(int x, int y, int z) {
      byte m = this.get(x, y, z);
      return m != AIR && m != WATER && m != FALL && m != LEAVES;
   }

   /** Minecraft-Umgebungsverdeckung der getroffenen Fläche, weich über die Fläche interpoliert. */
   private float ao(Ray r, float fu, float fv) {
      int[] n = NORMAL[r.face];
      int lx = r.x + n[0];
      int ly = r.y + n[1];
      int lz = r.z + n[2];
      int[] ua;
      int[] va;
      if (n[0] != 0) {
         ua = new int[]{0, 0, 1};
         va = new int[]{0, 1, 0};
      } else if (n[1] != 0) {
         ua = new int[]{1, 0, 0};
         va = new int[]{0, 0, 1};
      } else {
         ua = new int[]{1, 0, 0};
         va = new int[]{0, 1, 0};
      }

      float[] corner = new float[4];
      int k = 0;

      for (int cv = -1; cv <= 1; cv += 2) {
         for (int cu = -1; cu <= 1; cu += 2) {
            boolean s1 = this.occl(lx + ua[0] * cu, ly + ua[1] * cu, lz + ua[2] * cu);
            boolean s2 = this.occl(lx + va[0] * cv, ly + va[1] * cv, lz + va[2] * cv);
            boolean c = this.occl(lx + ua[0] * cu + va[0] * cv, ly + ua[1] * cu + va[1] * cv, lz + ua[2] * cu + va[2] * cv);
            int occ = s1 && s2 ? 3 : (s1 ? 1 : 0) + (s2 ? 1 : 0) + (c ? 1 : 0);
            corner[k++] = 1.0F - occ * 0.19F;
         }
      }

      float top = corner[0] + (corner[1] - corner[0]) * fu;
      float bottom = corner[2] + (corner[3] - corner[2]) * fu;
      return top + (bottom - top) * fv;
   }

   private static float frac(float v) {
      return v - (float)Math.floor(v);
   }

   private static int darken(int c, float f) {
      return Tex.argb(255, (c >> 16 & 0xFF) * f, (c >> 8 & 0xFF) * f, (c & 0xFF) * f);
   }

   /** Texel der getroffenen Fläche (ARGB). */
   private int texel(Ray r, float fx, float fy, float fz) {
      float u;
      float v;
      int face = r.face;
      if (face < 2) {
         u = face == 0 ? fz : 1.0F - fz;
         v = 1.0F - fy;
      } else if (face < 4) {
         u = fx;
         v = fz;
      } else {
         u = face == 4 ? fx : 1.0F - fx;
         v = 1.0F - fy;
      }

      int tu = Math.min(15, (int)(u * 16.0F));
      int tv = Math.min(15, (int)(v * 16.0F));
      byte m = r.mat;
      if (m == GRASS) {
         if (face == 3) {
            return this.tex[GRASS].at(tu, tv);
         } else if (face == 2) {
            return this.tex[DIRT].at(tu, tv);
         } else {
            if (this.grassOverlay != null) {
               int o = this.grassOverlay.at(tu, tv);
               if ((o >>> 24) > 100) {
                  float[] g = this.tint[GRASS];
                  return Tex.argb(255, (o >> 16 & 0xFF) * g[0], (o >> 8 & 0xFF) * g[1], (o & 0xFF) * g[2]) | 0x01000000;
               }
            }

            return this.grassSide.at(tu, tv);
         }
      } else if (m == LOG && face >= 2 && face < 4) {
         return this.logTop.at(tu, tv);
      } else if (m == LEAVES) {
         int c = this.tex[LEAVES].at(tu, tv);
         return (c >>> 24) < 128 ? this.leafFill : c;
      } else {
         return this.tex[m].at(tu, tv);
      }
   }

   private float sunShadow(float px, float py, float pz) {
      Ray s = new Ray();
      return this.march(px, py, pz, SUN[0], SUN[1], SUN[2], 160.0F, false, s) ? 0.0F : 1.0F;
   }

   /**
    * Farbe eines Strahls. out: [0..2] Farbe, [3] Wasser-Art (-1 keins, 0 Fluss, 1 Fall), [4],[5] Wasser-u/v,
    * [6..8] Licht, [9..11] Spiegelung, [12] Fresnel, [13] Schaum, [14] Dunst, [15] Abstand.
    */
   private void shade(float ox, float oy, float oz, float dx, float dy, float dz, boolean primary, float[] out) {
      Ray r = new Ray();
      out[3] = -1.0F;
      if (!this.march(ox, oy, oz, dx, dy, dz, 900.0F, true, r)) {
         this.sky(ox, oy, oz, dx, dy, dz, out);
         out[15] = Float.MAX_VALUE;
         return;
      }

      float px = ox + dx * r.t;
      float py = oy + dy * r.t;
      float pz = oz + dz * r.t;
      float fx = Math.max(0.0F, Math.min(0.9999F, px - r.x));
      float fy = Math.max(0.0F, Math.min(0.9999F, py - r.y));
      float fz = Math.max(0.0F, Math.min(0.9999F, pz - r.z));
      int[] n = NORMAL[r.face];
      float nx = px + n[0] * 0.01F;
      float ny = py + n[1] * 0.01F;
      float nz = pz + n[2] * 0.01F;
      float lam = Math.max(0.0F, n[0] * SUN[0] + n[1] * SUN[1] + n[2] * SUN[2]);
      float shadow = lam > 0.0F ? this.sunShadow(nx, ny, nz) : 0.0F;
      float fu;
      float fv;
      if (r.face < 2) {
         fu = fz;
         fv = fy;
      } else if (r.face < 4) {
         fu = fx;
         fv = fz;
      } else {
         fu = fx;
         fv = fy;
      }

      float occ = r.mat == WATER || r.mat == FALL ? 1.0F : this.ao(r, fu, fv);
      float amb = FACE_SHADE[r.face] * occ;
      float lr = amb * SKY_COLOR[0] + lam * shadow * SUN_COLOR[0] * 0.95F;
      float lg = amb * SKY_COLOR[1] + lam * shadow * SUN_COLOR[1] * 0.95F;
      float lb = amb * SKY_COLOR[2] + lam * shadow * SUN_COLOR[2] * 0.95F;
      float haze = Math.max(0.0F, Math.min(0.62F, (r.t - 35.0F) / 330.0F));
      out[14] = haze;
      out[15] = r.t;
      if (r.mat == WATER || r.mat == FALL) {
         boolean fall = r.mat == FALL;
         float u = fall ? px : px;
         float v = fall ? -py : -pz;
         float fres = 0.0F;
         float rr = 0.0F;
         float rg = 0.0F;
         float rb = 0.0F;
         if (!fall && r.face == 3) {
            float[] refl = new float[16];
            this.shade(nx, ny, nz, dx, -dy, dz, false, refl);
            rr = refl[0];
            rg = refl[1];
            rb = refl[2];
            fres = 0.06F + 0.42F * (float)Math.pow(1.0F - Math.abs(dy), 5.0);
         }

         float foam = 0.0F;
         if (!fall) {
            float dzFall = FALL_FRONT - pz;
            if (Math.abs(px) < FALL_HALF + 3.0F && dzFall < 9.0F) {
               foam = Math.max(0.0F, 1.0F - dzFall / 9.0F) * (Math.abs(px) < FALL_HALF ? 1.0F : 0.5F);
            }

            for (int sd = -1; sd <= 1; sd += 2) {
               float ddx = (px - sd * STATUE_X) / 11.5F;
               float ddz = (pz - STATUE_Z) / 11.0F;
               float dd = ddx * ddx + ddz * ddz;
               if (dd < 1.3F) {
                  foam = Math.max(foam, (1.3F - dd) * 0.35F);
               }
            }
         } else {
            foam = Math.max(0.0F, (py - (FALL_TOP - 3.0F)) / 3.0F) * 0.6F + Math.max(0.0F, 1.0F - py / 5.0F) * 0.8F;
         }

         out[3] = fall ? 1.0F : 0.0F;
         out[4] = u;
         out[5] = v;
         out[6] = lr;
         out[7] = lg;
         out[8] = lb;
         out[9] = rr;
         out[10] = rg;
         out[11] = rb;
         out[12] = fres;
         out[13] = foam;
         int base = this.waterTexel(fall, 0, u, v);
         this.waterColor(fall, base, lr, lg, lb, rr, rg, rb, fres, foam, 0.0F, haze, u, v, out);
         return;
      }

      int texel = this.texel(r, fx, fy, fz);
      float[] tn = this.tint[r.mat];
      boolean preTinted = r.mat == GRASS && r.face != 3 && (texel & 0x01000000) != 0 && r.face != 2;
      float tr = preTinted ? 1.0F : tn[0];
      float tg = preTinted ? 1.0F : tn[1];
      float tb = preTinted ? 1.0F : tn[2];
      if (r.mat == GRASS && r.face != 3) {
         tr = 1.0F;
         tg = 1.0F;
         tb = 1.0F;
      }

      float cr = (texel >> 16 & 0xFF) / 255.0F * tr * lr;
      float cg = (texel >> 8 & 0xFF) / 255.0F * tg * lg;
      float cb = (texel & 0xFF) / 255.0F * tb * lb;
      out[0] = cr + (HAZE[0] - cr) * haze;
      out[1] = cg + (HAZE[1] - cg) * haze;
      out[2] = cb + (HAZE[2] - cb) * haze;
   }

   private int waterTexel(boolean fall, int frame, float u, float v) {
      int tu = (int)Math.floor(u * 16.0F);
      int tv = (int)Math.floor(v * 16.0F);
      if (fall) {
         Tex t = this.waterFlow;
         int fs = t.w;
         int frames = Math.max(1, t.h / fs);
         int f = frame * frames / FALL_FRAMES % frames;
         int x = Math.floorMod(tu, fs);
         int y = Math.floorMod(tv, fs);
         return t.px[(f * fs + y) * t.w + x];
      } else {
         Tex t = this.waterStill;
         int fs = t.w;
         int frames = Math.max(1, t.h / fs);
         int f = frame * frames / RIVER_FRAMES % frames;
         int x = Math.floorMod(tu, fs);
         int y = Math.floorMod(tv, fs);
         return t.px[(f * fs + y) * t.w + x];
      }
   }

   private void waterColor(boolean fall, int texel, float lr, float lg, float lb, float rr, float rg, float rb, float fres, float foam, float phase, float haze,
      float u, float v, float[] out) {
      float g = ((texel >> 16 & 0xFF) + (texel >> 8 & 0xFF) + (texel & 0xFF)) / 765.0F;
      float cr;
      float cg;
      float cb;
      if (fall) {
         cr = (0.5F + 0.55F * g) * 0.82F;
         cg = (0.62F + 0.5F * g) * 0.92F;
         cb = (0.74F + 0.4F * g) * 1.0F;
      } else {
         cr = 0.1F + 0.2F * g;
         cg = 0.26F + 0.3F * g;
         cb = 0.38F + 0.32F * g;
      }

      cr *= lr * 1.05F;
      cg *= lg * 1.05F;
      cb *= lb * 1.05F;
      cr += (rr - cr) * fres;
      cg += (rg - cg) * fres;
      cb += (rb - cb) * fres;
      if (foam > 0.0F) {
         // Phase auf einem Kreis, damit die Bildfolge nahtlos wiederholt
         double ang = phase * Math.PI * 2.0;
         float fn = this.noise.noise(u * 3.0F + (float)Math.cos(ang) * 0.7F, v * 3.0F + (float)Math.sin(ang) * 0.7F, 77.0F);
         float f = Math.max(0.0F, Math.min(1.0F, foam * (0.55F + fn * 0.9F) - 0.2F));
         float w = 0.95F * Math.min(1.2F, lg + 0.25F);
         cr += (w - cr) * f;
         cg += (w - cg) * f;
         cb += (w * 1.02F - cb) * f;
      }

      out[0] = cr + (HAZE[0] - cr) * haze;
      out[1] = cg + (HAZE[1] - cg) * haze;
      out[2] = cb + (HAZE[2] - cb) * haze;
   }

   /** Himmel mit fernen Bergen und Minecraft-Wolken. */
   private void sky(float ox, float oy, float oz, float dx, float dy, float dz, float[] out) {
      float e = dy;
      float k = Math.max(0.0F, Math.min(1.0F, e * 2.4F));
      float r = 0.74F + (0.38F - 0.74F) * k;
      float g = 0.85F + (0.6F - 0.85F) * k;
      float b = 0.96F + (0.92F - 0.96F) * k;
      float az = (float)Math.atan2(dx, dz);
      float far = 0.12F + 0.07F * this.noise.fbm(az * 2.2F + 10.0F, 0.0F, 3.0F, 4);
      float near = 0.085F + 0.05F * this.noise.fbm(az * 4.5F + 30.0F, 1.0F, 5.0F, 4);
      if (e < far) {
         float m = Math.min(1.0F, (far - e) * 60.0F);
         r += (0.6F - r) * m * 0.75F;
         g += (0.7F - g) * m * 0.75F;
         b += (0.82F - b) * m * 0.75F;
      }

      if (e < near) {
         float m = Math.min(1.0F, (near - e) * 60.0F);
         r += (0.46F - r) * m * 0.8F;
         g += (0.6F - g) * m * 0.8F;
         b += (0.68F - b) * m * 0.8F;
      }

      if (dy > 0.01F) {
         float t = (88.0F - oy) / dy;
         if (t < 1400.0F) {
            float cx = ox + dx * t;
            float cz = oz + dz * t;
            int gx = (int)Math.floor(cx / 12.0F);
            int gz = (int)Math.floor(cz / 12.0F);
            float c = this.noise.noise(gx * 0.31F, gz * 0.31F, 3.0F);
            if (c > 0.56F) {
               float fade = Math.max(0.0F, 1.0F - t / 1400.0F);
               float a = 0.85F * fade;
               float shade = 0.93F + 0.07F * this.noise.rand(gx, 0, gz);
               r += (shade - r) * a;
               g += (shade - g) * a;
               b += (shade * 1.02F - b) * a;
            }
         }
      }

      out[0] = r;
      out[1] = g;
      out[2] = b;
   }

   // ================================================================== Rendern

   private static float tone(float v) {
      v = Math.max(0.0F, v);
      return v / (1.0F + v * 0.3F) * 1.12F;
   }

   private static int pack(float r, float g, float b) {
      return Tex.argb(255, tone(r) * 255.0F, tone(g) * 255.0F, tone(b) * 255.0F);
   }

   /** Rendert Bild, Tiefen und Wasser-Animationen (ein paar Sekunden – im Hintergrund aufrufen). */
   public void render() {
      float[][] rows = new float[VH * VW][];
      IntStream.range(0, VH).parallel().forEach(y -> {
         float[] dir = new float[3];

         for (int x = 0; x < VW; x++) {
            float[] out = new float[16];
            CAM.ray(x + 0.5F, y + 0.5F, dir);
            this.shade(CAM.x, CAM.y, CAM.z, dir[0], dir[1], dir[2], true, out);
            int i = y * VW + x;
            this.image.px[i] = pack(out[0], out[1], out[2]);
            this.depth[i] = out[15];
            if (out[3] >= 0.0F) {
               rows[i] = out;
            }
         }
      });
      // Wasserpixel sammeln
      int count = 0;

      for (float[] o : rows) {
         if (o != null) {
            count++;
         }
      }

      this.waterCount = count;
      this.wPix = new int[count];
      this.wKind = new byte[count];
      this.wU = new float[count];
      this.wV = new float[count];
      this.wLight = new float[count * 3];
      this.wRefl = new float[count * 3];
      this.wFres = new float[count];
      this.wFoam = new float[count];
      this.wHaze = new float[count];
      int k = 0;
      int[] fb = {VW, VH, -1, -1};
      int[] rb = {VW, VH, -1, -1};

      for (int i = 0; i < rows.length; i++) {
         float[] o = rows[i];
         if (o != null) {
            this.wPix[k] = i;
            this.wKind[k] = (byte)o[3];
            this.wU[k] = o[4];
            this.wV[k] = o[5];
            this.wLight[k * 3] = o[6];
            this.wLight[k * 3 + 1] = o[7];
            this.wLight[k * 3 + 2] = o[8];
            this.wRefl[k * 3] = o[9];
            this.wRefl[k * 3 + 1] = o[10];
            this.wRefl[k * 3 + 2] = o[11];
            this.wFres[k] = o[12];
            this.wFoam[k] = o[13];
            this.wHaze[k] = o[14];
            int[] b = o[3] > 0.5F ? fb : rb;
            int px = i % VW;
            int py = i / VW;
            b[0] = Math.min(b[0], px);
            b[1] = Math.min(b[1], py);
            b[2] = Math.max(b[2], px);
            b[3] = Math.max(b[3], py);
            k++;
         }
      }

      this.fallAtlas = this.atlas(true, fb, this.fallBox, FALL_FRAMES);
      this.riverAtlas = this.atlas(false, rb, this.riverBox, RIVER_FRAMES);
   }

   private Tex atlas(boolean fall, int[] bounds, int[] box, int frames) {
      if (bounds[2] < bounds[0]) {
         box[0] = 0;
         box[1] = 0;
         box[2] = 1;
         box[3] = 1;
         return new Tex(1, frames);
      }

      int bw = bounds[2] - bounds[0] + 1;
      int bh = bounds[3] - bounds[1] + 1;
      box[0] = bounds[0];
      box[1] = bounds[1];
      box[2] = bw;
      box[3] = bh;
      Tex t = new Tex(bw, bh * frames);
      IntStream.range(0, frames).parallel().forEach(f -> {
         float[] out = new float[3];
         float phase = (float)f / frames;

         for (int k = 0; k < this.waterCount; k++) {
            if (this.wKind[k] == 1 == fall) {
               int i = this.wPix[k];
               int x = i % VW - box[0];
               int y = i / VW - box[1];
               int texel = this.waterTexel(fall, f, this.wU[k], this.wV[k]);
               this.waterColor(
                  fall, texel, this.wLight[k * 3], this.wLight[k * 3 + 1], this.wLight[k * 3 + 2], this.wRefl[k * 3], this.wRefl[k * 3 + 1],
                  this.wRefl[k * 3 + 2], this.wFres[k], this.wFoam[k], phase, this.wHaze[k], this.wU[k], this.wV[k], out
               );
               t.px[(f * bh + y) * bw + x] = pack(out[0], out[1], out[2]);
            }
         }
      });
      return t;
   }

   private static float[] norm(float x, float y, float z) {
      float l = (float)Math.sqrt(x * x + y * y + z * z);
      return new float[]{x / l, y / l, z / l};
   }
}
