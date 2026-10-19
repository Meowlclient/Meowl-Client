package com.lennart.nebula.afk;

import java.util.Random;

public final class BattleRenderer {
   private static final float CAM_H = 7.0F;
   private static final int SKY_TOP = 3024464;
   private static final int SKY_MID = 8011614;
   private static final int SKY_LOW = 14250047;
   private static final int SKY_HORIZON = 16757854;
   private static final int GROUND_FAR = 14849370;
   private static final int GROUND_NEAR = 9324072;
   private static final int HAZE = 15902824;
   private static final int ROBOT_METAL = 11040842;
   private static final int ROBOT_DARK = 6176551;
   private static final int ROBOT_EYE = 16738858;
   private static final int SOLDIER_ARMOR = 15525852;
   private static final int SOLDIER_SHADE = 11906980;
   private static final int SOLDIER_VISOR = 2830390;
   private static final int SOLDIER_ACCENT = 4165514;
   private static final int GUN = 3815998;
   private static final float[] GROUND_LINES = new float[]{24.0F, 28.0F, 33.0F, 39.0F, 46.0F, 55.0F, 66.0F, 80.0F, 97.0F, 118.0F, 145.0F};
   private final Random rnd = new Random();
   private final float[] cloudX = new float[16];
   private final float[] cloudY = new float[16];
   private final float[] cloudV = new float[16];
   private final int[][] cloudRows = new int[16][];
   private final float[] mesaFar = new float[72];
   private final float[] mesaMid = new float[54];
   private final float[] dustX = new float[80];
   private final float[] dustY = new float[80];
   private final float[] dustV = new float[80];
   private final float[] dustP = new float[80];
   private final float[] tmp = new float[3];
   private int w;
   private int h;
   private float hy;
   private float focal;
   private float cx;
   private float camX;
   private float zoom = 1.0F;
   private float panPx;
   private float alpha = 1.0F;
   private float t;
   private int block;
   private int ledgeX0;
   private int ledgeCols;
   private final int[] ledgeEdge = new int[24];
   public int playerX;
   public int playerFeetY;
   public int playerBlock;

   public BattleRenderer(long seed) {
      Random r = new Random(seed ^ 25214903917L);

      for (int i = 0; i < this.cloudX.length; i++) {
         this.cloudX[i] = -0.2F + r.nextFloat() * 1.4F;
         this.cloudY[i] = 0.03F + r.nextFloat() * 0.3F;
         this.cloudV[i] = (0.002F + r.nextFloat() * 0.004F) * (r.nextBoolean() ? 1 : -1);
         int rows = 2 + r.nextInt(2);
         int[] data = new int[rows * 2];
         int len = 6 + r.nextInt(12);

         for (int k = 0; k < rows; k++) {
            int shrink = k == 0 ? r.nextInt(3) + 1 : r.nextInt(2);
            int start = k == 0 ? shrink + r.nextInt(3) : r.nextInt(3);
            data[k * 2] = start;
            data[k * 2 + 1] = Math.max(3, len - start - shrink - (k == rows - 1 ? r.nextInt(4) : 0));
         }

         this.cloudRows[i] = data;
      }

      int i = 0;

      while (i < this.mesaFar.length) {
         float hgt = r.nextFloat() < 0.25F ? 0.012F + r.nextFloat() * 0.02F : 0.03F + r.nextFloat() * 0.07F;
         int run = 2 + r.nextInt(7);

         for (int k = 0; k < run && i < this.mesaFar.length; k++) {
            this.mesaFar[i++] = hgt + (k != 0 && k != run - 1 ? 0.0F : -0.01F);
         }
      }

      i = 0;

      while (i < this.mesaMid.length) {
         boolean butte = r.nextFloat() < 0.4F;
         int run = butte ? 3 + r.nextInt(3) : 2 + r.nextInt(5);
         float hgt = butte ? 0.04F + r.nextFloat() * 0.06F : 0.0F;

         for (int k = 0; k < run && i < this.mesaMid.length; k++) {
            boolean edge = k == 0 || k == run - 1;
            this.mesaMid[i++] = butte ? hgt * (edge ? 0.55F : 1.0F) : 0.0F;
         }
      }

      for (int k = 0; k < this.dustX.length; k++) {
         this.dustX[k] = r.nextFloat();
         this.dustY[k] = r.nextFloat();
         this.dustV[k] = 0.006F + r.nextFloat() * 0.03F;
         this.dustP[k] = r.nextFloat() * 6.28F;
      }
   }

   private int a(int rgb, float alpha) {
      int al = Math.round(Math.max(0.0F, Math.min(1.0F, alpha * this.alpha)) * 255.0F);
      return al << 24 | rgb & 16777215;
   }

   private static int mix(int c1, int c2, float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      int r = Math.round((c1 >> 16 & 0xFF) + ((c2 >> 16 & 0xFF) - (c1 >> 16 & 0xFF)) * t);
      int g = Math.round((c1 >> 8 & 0xFF) + ((c2 >> 8 & 0xFF) - (c1 >> 8 & 0xFF)) * t);
      int b = Math.round((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
      return r << 16 | g << 8 | b;
   }

   private static float clamp01(float v) {
      return Math.max(0.0F, Math.min(1.0F, v));
   }

   private float sx(float x, float d) {
      return this.cx + (x - this.camX) / d * this.focal;
   }

   private float sy(float y, float d) {
      return this.hy + (7.0F - y) / d * this.focal;
   }

   private float scale(float d) {
      return this.focal / d;
   }

   private float zx(float x) {
      return this.w / 2.0F + (x - this.w / 2.0F) * this.zoom;
   }

   private float zy(float y) {
      return this.h / 2.0F + (y - this.h / 2.0F) * this.zoom;
   }

   private void rect(Canvas c, float x1, float y1, float x2, float y2, int argb) {
      int ix1 = Math.round(x1);
      int iy1 = Math.round(y1);
      int ix2 = Math.round(x2);
      int iy2 = Math.round(y2);
      if (ix2 == ix1) {
         ix2 = ix1 + 1;
      }

      if (iy2 == iy1) {
         iy2 = iy1 + 1;
      }

      if (ix2 >= 0 && iy2 >= 0 && ix1 <= this.w && iy1 <= this.h && argb >>> 24 > 0) {
         c.fill(Math.min(ix1, ix2), Math.min(iy1, iy2), Math.max(ix1, ix2), Math.max(iy1, iy2), argb);
      }
   }

   private float haze(float d) {
      return clamp01((d - 22.0F) / 96.0F) * 0.55F;
   }

   public void setup(int width, int height, float sceneTime, boolean motion, float introZoom, float alpha) {
      this.w = width;
      this.h = height;
      this.t = sceneTime;
      this.alpha = alpha;
      float breathe = motion ? 1.0F + 0.012F * (float)Math.sin(sceneTime * 0.21F) : 1.0F;
      this.zoom = breathe * introZoom;
      this.camX = motion ? 1.8F * (float)Math.sin(sceneTime * 0.037F) : 0.0F;
      float bob = motion ? (float)Math.sin(sceneTime * 0.17F) * height * 0.003F : 0.0F;
      this.hy = this.zy(height * 0.47F + bob);
      this.cx = width * 0.54F;
      this.focal = height * 0.45F * 22.0F / 7.0F * this.zoom;
      this.panPx = this.camX * this.focal / 60.0F;
      this.block = Math.max(8, Math.round(height * 0.12F * this.zoom));
      this.layoutLedge();
   }

   private void layoutLedge() {
      int b = this.block;
      this.ledgeX0 = Math.round(this.zx(this.w * 0.02F)) - Math.round(this.block * 0.45F);
      int[] pattern = new int[]{0, -1, -1, 0, 0, 1, 2, 3, 5};
      this.ledgeCols = Math.min(this.ledgeEdge.length, Math.max(4, (int)Math.ceil((this.zx(this.w * 0.4F) - this.ledgeX0) / b) + 1));
      float base = this.zy(this.h * 0.7F);

      for (int c = 0; c < this.ledgeCols; c++) {
         int step = c < pattern.length ? pattern[c] : pattern[pattern.length - 1] + (c - pattern.length + 1) * 2;
         this.ledgeEdge[c] = Math.round(base + step * b * 0.22F);
      }

      this.playerBlock = b;
      this.playerX = this.ledgeX0 + Math.round(b * 2.5F);
      this.playerFeetY = this.ledgeEdge[2] + 1;
   }

   public void drawScene(Canvas c, BattleSim sim) {
      this.drawSky(c);
      this.drawSun(c);
      this.drawClouds(c);
      this.drawMesas(c);
      this.drawGround(c);
      this.drawScenery(c, sim);
      this.drawFlyers(c, sim);
      this.drawFires(c, sim);
      this.drawPlumes(c, sim);
      if (sim.battleEnabled()) {
         this.drawUnits(c, sim);
         this.drawBolts(c, sim);
      }

      this.drawMissiles(c, sim);
      this.drawExplosions(c, sim);
      this.drawLedge(c);
   }

   public void drawCover(Canvas c, int x1, int y1, int x2, int y2, float coverAlpha) {
      if (!(coverAlpha <= 0.003F)) {
         float saved = this.alpha;
         this.alpha = saved * coverAlpha;
         c.scissor(x1, y1, x2, y2);
         this.drawSky(c);
         this.drawSun(c);
         this.drawMesas(c);
         this.drawGround(c);
         this.drawLedge(c);
         c.noScissor();
         this.alpha = saved;
      }
   }

   private void drawSky(Canvas c) {
      int y1 = Math.round(this.zy(this.h * 0.2F));
      int y2 = Math.round(this.zy(this.h * 0.36F));
      int y3 = Math.round(this.hy) + 2;
      c.gradient(0, 0, this.w, Math.max(1, y1), this.a(3024464, 1.0F), this.a(8011614, 1.0F));
      c.gradient(0, y1, this.w, y2, this.a(8011614, 1.0F), this.a(14250047, 1.0F));
      c.gradient(0, y2, this.w, y3, this.a(14250047, 1.0F), this.a(16757854, 1.0F));
   }

   private void drawSun(Canvas c) {
      float sx = this.zx(this.w * 0.23F) - this.panPx * 0.2F;
      float sy = this.hy - this.h * 0.085F * this.zoom;
      float s = this.h * 0.075F * this.zoom;

      for (int k = 5; k >= 1; k--) {
         float g = s * (1.0F + k * 0.55F);
         this.rect(c, sx - g / 2.0F, sy - g / 2.0F, sx + g / 2.0F, sy + g / 2.0F, this.a(16766346, 0.07F));
      }

      this.rect(c, sx - s / 2.0F, sy - s / 2.0F, sx + s / 2.0F, sy + s / 2.0F, this.a(16773058, 1.0F));
      float in = s * 0.62F;
      this.rect(c, sx - in / 2.0F, sy - in / 2.0F, sx + in / 2.0F, sy + in / 2.0F, this.a(16776170, 1.0F));
      c.gradient(0, Math.round(this.hy - this.h * 0.03F), this.w, Math.round(this.hy), this.a(16765056, 0.0F), this.a(16765056, 0.35F));
   }

   private void drawClouds(Canvas c) {
      float cs = Math.max(2.0F, this.h / 46.0F * this.zoom);

      for (int i = 0; i < this.cloudX.length; i++) {
         float nx = this.cloudX[i] + this.cloudV[i] * this.t;
         nx = (float)((nx + 0.3) % 1.6 + 1.6) % 1.6F - 0.3F;
         float x = this.zx(nx * this.w) - this.panPx * 0.25F;
         float y = this.zy(this.cloudY[i] * this.h);
         float low = clamp01((this.cloudY[i] - 0.03F) / 0.3F);
         int lit = mix(11952250, 16761466, low);
         int shade = mix(5911896, 12607578, low);
         int[] rows = this.cloudRows[i];
         int n = rows.length / 2;

         for (int k = 0; k < n; k++) {
            float x1 = x + rows[k * 2] * cs;
            float x2 = x1 + rows[k * 2 + 1] * cs;
            float y1 = y + k * cs;
            int col = k == n - 1 ? shade : (k == 0 ? lit : mix(lit, shade, 0.4F));
            this.rect(c, x1, y1, x2, y1 + cs, this.a(col, 0.78F));
         }
      }
   }

   private void drawMesas(Canvas c) {
      int n = this.mesaFar.length;
      float colW = this.w * 1.3F / n * this.zoom;
      float start = this.zx(-this.w * 0.15F) - this.panPx * 0.15F;
      int far = mix(11822158, 15902824, 0.42F);
      int farTop = mix(far, 16765850, 0.35F);

      for (int i = 0; i < n; i++) {
         float x1 = start + i * colW;
         float top = this.hy - this.mesaFar[i] * this.h * this.zoom;
         this.rect(c, x1, top, x1 + colW + 1.0F, this.hy + 2.0F, this.a(far, 1.0F));
         this.rect(c, x1, top, x1 + colW + 1.0F, top + Math.max(1.0F, this.zoom), this.a(farTop, 0.9F));
      }

      int m = this.mesaMid.length;
      colW = this.w * 1.3F / m * this.zoom;
      start = this.zx(-this.w * 0.15F) - this.panPx * 0.35F;
      int mid = mix(8273712, 15902824, 0.3F);

      for (int i = 0; i < m; i++) {
         if (!(this.mesaMid[i] <= 0.0F)) {
            float x1 = start + i * colW;
            float top = this.hy - this.mesaMid[i] * this.h * this.zoom;
            this.rect(c, x1, top, x1 + colW + 1.0F, this.hy + 3.0F, this.a(mid, 1.0F));
            this.rect(c, x1, top, x1 + Math.max(1.0F, colW * 0.18F), this.hy + 3.0F, this.a(mix(12609610, 15902824, 0.3F), 0.9F));
            this.rect(c, x1, top, x1 + colW + 1.0F, top + Math.max(1.0F, this.zoom), this.a(15242852, 0.9F));
         }
      }
   }

   private void drawGround(Canvas c) {
      int top = Math.round(this.hy);
      c.gradient(0, top, this.w, this.h, this.a(14849370, 1.0F), this.a(9324072, 1.0F));

      for (int i = 0; i < GROUND_LINES.length; i++) {
         float y = this.sy(0.0F, GROUND_LINES[i]);
         this.rect(c, 0.0F, y, this.w, y + 1.0F, this.a(i % 2 == 0 ? 0 : 16777215, 0.05F));
      }

      c.gradient(0, Math.round(this.hy - this.h * 0.05F), this.w, top, this.a(15902824, 0.0F), this.a(16760954, 0.5F));
      c.gradient(0, top, this.w, Math.round(this.hy + this.h * 0.1F), this.a(16760954, 0.5F), this.a(16760954, 0.0F));
   }

   private void drawScenery(Canvas c, BattleSim sim) {
      for (int i = 0; i < sim.spireX.length; i++) {
         float d = sim.spireD[i];
         float s = this.scale(d);
         float x = this.sx(sim.spireX[i], d);
         float base = this.sy(0.0F, d);
         float hgt = sim.spireH[i] * s;
         float wid = hgt * 0.22F;
         int col = mix(7224362, 15902824, this.haze(d) + 0.15F);
         int lit = mix(12742730, 15902824, this.haze(d));

         for (int k = 0; k < 3; k++) {
            float ww = wid * (1.0F - k * 0.25F);
            float y1 = base - hgt * (k + 1) / 3.0F;
            float y2 = base - hgt * k / 3.0F;
            this.rect(c, x - ww / 2.0F, y1, x + ww / 2.0F, y2, this.a(col, 1.0F));
            this.rect(c, x - ww / 2.0F, y1, x - ww / 2.0F + Math.max(1.0F, ww * 0.2F), y2, this.a(lit, 1.0F));
         }
      }

      for (int i = 0; i < sim.archX.length; i++) {
         float d = sim.archD[i];
         float s = this.scale(d);
         float size = sim.archS[i];
         int col = mix(8014388, 15902824, this.haze(d) + 0.1F);
         int lit = mix(12614224, 15902824, this.haze(d));
         float bs = size * 0.34F * s;

         for (int k = 0; k <= 12; k++) {
            double th = Math.PI * k / 12.0;
            float wx = sim.archX[i] + (float)Math.cos(th) * size;
            float wy = (float)Math.sin(th) * size * 1.25F;
            float px = this.sx(wx, d);
            float py = this.sy(wy, d);
            this.rect(c, px - bs / 2.0F, py - bs / 2.0F, px + bs / 2.0F, py + bs / 2.0F, this.a(col, 1.0F));
            if (k > 6) {
               this.rect(c, px - bs / 2.0F, py - bs / 2.0F, px + bs / 2.0F, py - bs / 2.0F + Math.max(1.0F, bs * 0.25F), this.a(lit, 1.0F));
            }
         }

         for (int leg = -1; leg <= 1; leg += 2) {
            float px = this.sx(sim.archX[i] + leg * size, d);
            float py = this.sy(0.0F, d);
            this.rect(c, px - bs * 0.7F, py - bs * 1.2F, px + bs * 0.7F, py, this.a(col, 1.0F));
         }
      }

      for (int i = 0; i < sim.rockX.length; i++) {
         float d = sim.rockD[i];
         float s = this.scale(d) * sim.rockS[i];
         if (!(s < 1.0F)) {
            float x = this.sx(sim.rockX[i], d);
            float y = this.sy(0.0F, d);
            int col = mix(6961702, 15902824, this.haze(d));
            this.rect(c, x - s, y - s * 0.6F, x + s, y, this.a(col, 1.0F));
            this.rect(c, x - s, y - s * 0.6F, x + s, y - s * 0.6F + Math.max(1.0F, s * 0.2F), this.a(mix(12087370, 15902824, this.haze(d)), 1.0F));
         }
      }
   }

   private void drawFires(Canvas c, BattleSim sim) {
      for (int i = 0; i < 10; i++) {
         float d = sim.fd[i];
         float s = this.scale(d) * sim.fSize[i];
         float x = this.sx(sim.fx[i], d);
         float y = this.sy(0.0F, d);
         float g = s * 3.0F;
         this.rect(c, x - g, y - g * 0.7F, x + g, y + g * 0.15F, this.a(16747056, 0.14F));
         this.rect(c, x - s * 0.8F, y - s * 0.45F, x + s * 0.8F, y, this.a(3811876, 1.0F));

         for (int k = 0; k < 3; k++) {
            float fl = 0.6F + 0.45F * (float)Math.sin(this.t * (11.0F + k * 3.0F) + i * 2.1F + k * 1.7F);
            float fx = x + (k - 1) * s * 0.45F;
            float fh = s * (0.9F + fl * 0.8F) * (k == 1 ? 1.3F : 1.0F);
            float fw = s * 0.32F;
            this.rect(c, fx - fw, y - s * 0.3F - fh, fx + fw, y - s * 0.3F, this.a(13779996, 0.95F));
            this.rect(c, fx - fw * 0.6F, y - s * 0.3F - fh * 0.75F, fx + fw * 0.6F, y - s * 0.3F, this.a(16747050, 1.0F));
            this.rect(c, fx - fw * 0.3F, y - s * 0.3F - fh * 0.4F, fx + fw * 0.3F, y - s * 0.3F, this.a(16767072, 1.0F));
         }
      }
   }

   private void drawPlumes(Canvas c, BattleSim sim) {
      for (int i = 0; i < 10; i++) {
         if (sim.fPlume[i]) {
            float d = sim.fd[i];
            float s = this.scale(d);
            float bx = this.sx(sim.fx[i], d);
            float by = this.sy(0.0F, d) - s * 0.8F;
            float life = 9.0F;
            float maxSize = this.h * 0.07F * this.zoom;

            for (int k = 0; k < 14; k++) {
               float age = (this.t + k * (life / 14.0F) + i * 0.37F) % life;
               float f = age / life;
               float rise = age * (s * 1.1F + this.h * 0.017F * this.zoom);
               float drift = (float)Math.pow(age, 1.5) * this.h * 0.005F * this.zoom + (float)Math.sin(age * 0.9F + k) * s * 0.35F;
               float size = Math.min(maxSize, s * (0.9F + age * 0.5F));
               size = Math.max(2.0F, Math.round(size / 2.0F) * 2.0F);
               float al = 0.42F * (1.0F - f) * Math.min(1.0F, age * 2.0F);
               int col = mix(3089442, 8018510, f * 0.9F);
               float px = bx + drift;
               float py = by - rise;
               this.rect(c, px - size / 2.0F, py - size / 2.0F, px + size / 2.0F, py + size / 2.0F, this.a(col, al));
               if (size > 6.0F) {
                  float in = size * 0.55F;
                  this.rect(c, px - in / 2.0F, py - in / 2.0F, px + in / 2.0F, py + in / 2.0F, this.a(2365976, al * 0.6F));
               }
            }
         }
      }
   }

   private void drawUnits(Canvas c, BattleSim sim) {
      sim.sortByDepth();
      float minX = -40.0F;
      float maxX = this.w + 40.0F;

      for (int oi = 0; oi < sim.units; oi++) {
         int i = sim.order[oi];
         float d = sim.ud[i];
         float x = this.sx(sim.ux[i], d);
         if (!(x < minX) && !(x > maxX)) {
            float base = this.sy(0.0F, d);
            float px = this.scale(d) * 0.95F;
            boolean robot = sim.fac[i] == 0;
            float hz = this.haze(d);
            if (sim.deadT[i] > 0.0F) {
               this.drawFallen(c, x, base, px, robot, hz, sim.deadT[i]);
            } else if (px < 3.0F) {
               int col = mix(robot ? 8016438 : 14736080, 15902824, hz);
               float ww = Math.max(1.0F, px * 0.42F);
               this.rect(c, x - ww / 2.0F, base - Math.max(1.0F, px), x + ww / 2.0F, base, this.a(col, 1.0F));
            } else {
               float dir = robot ? -1.0F : 1.0F;
               float swing = sim.moving[i] ? (float)Math.sin(sim.phase[i]) : 0.0F;
               if (px < 8.0F) {
                  this.drawUnitSimple(c, x, base, px / 10.0F, robot, dir, swing, hz);
               } else {
                  this.drawUnitDetailed(c, x, base, px / 10.0F, robot, dir, swing, hz);
               }
            }
         }
      }
   }

   private void part(Canvas c, float x, float base, float u, float dir, float lx1, float ly1, float lx2, float ly2, int rgb, float hz) {
      float a1 = x + lx1 * u * dir;
      float a2 = x + lx2 * u * dir;
      this.rect(c, Math.min(a1, a2), base - (10.0F - ly1) * u, Math.max(a1, a2), base - (10.0F - ly2) * u, this.a(mix(rgb, 15902824, hz), 1.0F));
   }

   private void drawUnitSimple(Canvas c, float x, float base, float u, boolean robot, float dir, float swing, float hz) {
      float l = swing * 0.8F;
      if (robot) {
         this.part(c, x, base, u, dir, -1.3F, 0.2F, 1.3F, 2.6F, 11040842, hz);
         this.part(c, x, base, u, dir, -1.5F, 3.0F, 1.5F, 6.0F, 11040842, hz);
         this.part(c, x, base, u, dir, -1.0F + l, 6.0F, -0.2F + l, 10.0F, 6176551, hz);
         this.part(c, x, base, u, dir, 0.2F - l, 6.0F, 1.0F - l, 10.0F, 6176551, hz);
         if (u * 10.0F > 5.0F) {
            this.part(c, x, base, u, dir, 0.3F, 1.0F, 1.1F, 1.7F, 16738858, hz * 0.5F);
         }
      } else {
         this.part(c, x, base, u, dir, -1.5F, 0.0F, 1.5F, 6.2F, 15525852, hz);
         this.part(c, x, base, u, dir, 0.0F, 0.9F, 1.6F, 1.8F, 2830390, hz);
         this.part(c, x, base, u, dir, 0.4F, 3.8F, 2.6F, 4.6F, 3815998, hz);
         this.part(c, x, base, u, dir, -1.3F + l, 6.2F, -0.1F + l, 10.0F, 11906980, hz);
         this.part(c, x, base, u, dir, 0.1F - l, 6.2F, 1.3F - l, 10.0F, 15525852, hz);
      }
   }

   private void drawUnitDetailed(Canvas c, float x, float base, float u, boolean robot, float dir, float swing, float hz) {
      float l = swing * 0.7F;
      float lift = Math.abs(swing) * 0.4F;
      if (robot) {
         this.part(c, x, base, u, dir, -0.9F, -0.9F, -0.5F, 0.3F, 6176551, hz);
         this.part(c, x, base, u, dir, -1.3F, 0.2F, 1.4F, 2.5F, 11040842, hz);
         this.part(c, x, base, u, dir, -1.3F, 0.2F, 1.4F, 0.6F, 13146218, hz);
         this.part(c, x, base, u, dir, 0.3F, 1.0F, 1.1F, 1.7F, 16738858, hz * 0.4F);
         this.part(c, x, base, u, dir, -0.4F, 2.5F, 0.4F, 3.0F, 6176551, hz);
         this.part(c, x, base, u, dir, -1.6F, 3.0F, 1.6F, 6.0F, 11040842, hz);
         this.part(c, x, base, u, dir, -0.8F, 3.7F, 0.8F, 5.0F, 6176551, hz);
         this.part(c, x, base, u, dir, 1.6F, 3.2F, 2.1F, 5.6F, 6176551, hz);
         this.part(c, x, base, u, dir, 1.4F, 4.0F, 3.4F, 4.6F, 3815998, hz);
         this.part(c, x, base, u, dir, -1.2F + l, 6.0F + lift * (swing > 0.0F ? 1 : 0), -0.5F + l, 10.0F, 6176551, hz);
         this.part(c, x, base, u, dir, 0.5F - l, 6.0F + lift * (swing < 0.0F ? 1 : 0), 1.2F - l, 10.0F, 6176551, hz);
         this.part(c, x, base, u, dir, -1.4F + l, 9.4F, -0.3F + l, 10.0F, 11040842, hz);
         this.part(c, x, base, u, dir, 0.3F - l, 9.4F, 1.4F - l, 10.0F, 11040842, hz);
      } else {
         this.part(c, x, base, u, dir, -1.5F, 0.0F, 1.5F, 2.6F, 15525852, hz);
         this.part(c, x, base, u, dir, -1.5F, 2.0F, 1.5F, 2.6F, 11906980, hz);
         this.part(c, x, base, u, dir, 0.1F, 1.0F, 1.6F, 1.6F, 2830390, hz);
         this.part(c, x, base, u, dir, -1.6F, 2.6F, 1.6F, 5.8F, 15525852, hz);
         this.part(c, x, base, u, dir, -1.6F, 2.6F, -0.6F, 3.4F, 4165514, hz);
         this.part(c, x, base, u, dir, -1.6F, 5.1F, 1.6F, 5.8F, 11906980, hz);
         this.part(c, x, base, u, dir, -1.6F, 5.8F, 1.6F, 6.4F, 3815998, hz);
         this.part(c, x, base, u, dir, 0.0F, 3.2F, 1.2F, 4.6F, 11906980, hz);
         this.part(c, x, base, u, dir, 0.4F, 3.8F, 3.0F, 4.5F, 3815998, hz);
         this.part(c, x, base, u, dir, -1.4F + l, 6.4F + lift * (swing > 0.0F ? 1 : 0), -0.1F + l, 10.0F, 11906980, hz);
         this.part(c, x, base, u, dir, 0.1F - l, 6.4F + lift * (swing < 0.0F ? 1 : 0), 1.4F - l, 10.0F, 15525852, hz);
         this.part(c, x, base, u, dir, -1.4F + l, 9.3F, -0.1F + l, 10.0F, 3815998, hz);
         this.part(c, x, base, u, dir, 0.1F - l, 9.3F, 1.4F - l, 10.0F, 3815998, hz);
      }
   }

   private void drawFallen(Canvas c, float x, float base, float px, boolean robot, float hz, float deadT) {
      float len = Math.max(1.0F, px * 0.85F);
      float thick = Math.max(1.0F, px * 0.2F);
      int col = mix(robot ? 5914152 : 12104360, 15902824, hz);
      float fade = Math.min(1.0F, deadT / 1.5F);
      this.rect(c, x - len / 2.0F, base - thick, x + len / 2.0F, base, this.a(col, fade));
   }

   private void drawBolts(Canvas c, BattleSim sim) {
      for (int b = 0; b < sim.boltCap; b++) {
         if (sim.bLife[b] > 0.0F) {
            float d = sim.bd[b];
            float x1 = this.sx(sim.bx[b], d);
            float y1 = this.sy(0.55F, d);
            float d2 = d - sim.bvd[b] * 0.02F;
            float x2 = this.sx(sim.bx[b] - sim.bvx[b] * 0.02F, Math.max(1.0F, d2));
            float y2 = this.sy(0.55F, Math.max(1.0F, d2));
            int core = sim.bFac[b] == 0 ? 16734780 : 7330047;
            this.line(c, x1, y1, x2, y2, this.a(core, 0.95F), this.scale(d) > 12.0F ? 2 : 1);
         }
      }

      for (int s = 0; s < 48; s++) {
         if (sim.sAge[s] < 0.18F) {
            float d = sim.sd[s];
            float x = this.sx(sim.sx[s], d);
            float y = this.sy(0.4F, d);
            float r = Math.max(1.0F, this.scale(d) * 0.25F);
            this.rect(c, x - r, y - r, x + r, y + r, this.a(16773808, 1.0F - sim.sAge[s] / 0.18F));
         }
      }
   }

   private void line(Canvas c, float x1, float y1, float x2, float y2, int argb, int thick) {
      float dx = x2 - x1;
      float dy = y2 - y1;
      int steps = Math.min(14, Math.max(1, Math.round(Math.max(Math.abs(dx), Math.abs(dy)))));

      for (int i = 0; i <= steps; i++) {
         float f = (float)i / steps;
         float x = x1 + dx * f;
         float y = y1 + dy * f;
         this.rect(c, x, y, x + thick, y + thick, argb);
      }
   }

   private void drawExplosions(Canvas c, BattleSim sim) {
      for (int e = 0; e < 28; e++) {
         float age = sim.eAge[e];
         if (!(age >= 2.4F)) {
            float d = sim.ed[e];
            float s = this.scale(d);
            float r = sim.eSize[e] * s * 1.2F;
            float x = this.sx(sim.ex[e], d);
            float y = this.sy(0.0F, d);
            this.rnd.setSeed(sim.eSeed[e]);
            if (age < 0.12F) {
               float g = r * 2.6F;
               this.rect(c, x - g / 2.0F, y - r * 0.6F - g / 2.0F, x + g / 2.0F, y - r * 0.6F + g / 2.0F, this.a(16774864, (1.0F - age / 0.12F) * 0.85F));
            }

            if (age > 0.25F) {
               float sa = (age - 0.25F) / 2.15F;

               for (int k = 0; k < 5; k++) {
                  float ox = (this.rnd.nextFloat() - 0.5F) * r + sa * r * 1.4F;
                  float rise = (age - 0.25F) * r * 1.1F + k * r * 0.3F;
                  float size = r * (0.7F + (age - 0.25F) * 0.7F) * (0.7F + this.rnd.nextFloat() * 0.5F);
                  this.rect(
                     c,
                     x + ox - size / 2.0F,
                     y - r * 0.5F - rise - size / 2.0F,
                     x + ox + size / 2.0F,
                     y - r * 0.5F - rise + size / 2.0F,
                     this.a(3878186, 0.6F * (1.0F - sa))
                  );
               }
            } else {
               for (int k = 0; k < 5; k++) {
                  this.rnd.nextFloat();
                  this.rnd.nextFloat();
               }
            }

            if (age < 0.9F) {
               float grow = Math.min(1.0F, age / 0.22F);
               float shrink = 1.0F - Math.max(0.0F, (age - 0.5F) / 0.4F) * 0.6F;
               float heat = age / 0.9F;
               int col = heat < 0.3F ? 16771488 : (heat < 0.6F ? 16751152 : 13778974);
               int core = heat < 0.5F ? 16775384 : 16756800;

               for (int k = 0; k < 7; k++) {
                  float ox = (this.rnd.nextFloat() - 0.5F) * r * 1.4F;
                  float oy = -this.rnd.nextFloat() * r * 1.2F;
                  float size = r * (0.5F + this.rnd.nextFloat() * 0.6F) * grow * shrink;
                  float al = 1.0F - Math.max(0.0F, (age - 0.6F) / 0.3F);
                  this.rect(c, x + ox - size / 2.0F, y + oy - size / 2.0F, x + ox + size / 2.0F, y + oy + size / 2.0F, this.a(col, al));
                  if (k < 3) {
                     this.rect(c, x + ox - size / 4.0F, y + oy - size / 4.0F, x + ox + size / 4.0F, y + oy + size / 4.0F, this.a(core, al));
                  }
               }
            }

            if (age < 1.2F) {
               for (int kx = 0; kx < 6; kx++) {
                  float vx = (this.rnd.nextFloat() - 0.5F) * 2.4F;
                  float vy = 1.5F + this.rnd.nextFloat() * 1.6F;
                  float px = x + vx * age * r;
                  float py = y - (vy * age - 2.2F * age * age) * r * 0.9F;
                  if (py <= y) {
                     float ps = Math.max(1.0F, s * 0.18F);
                     this.rect(c, px, py, px + ps, py + ps, this.a(2759702, 1.0F - age / 1.2F));
                  }
               }
            }
         }
      }
   }

   private void drawMissiles(Canvas c, BattleSim sim) {
      for (int m = 0; m < 8; m++) {
         float age = sim.mAge[m];
         float dur = sim.mDur[m];
         if (!(age > dur + 1.6F)) {
            float head = Math.min(1.0F, age / dur);
            float tail = Math.max(0.0F, head - 0.5F);
            float fade = age > dur ? 1.0F - (age - dur) / 1.6F : 1.0F;
            int samples = 18;

            for (int j = 0; j <= samples; j++) {
               float f = (float)j / samples;
               float tt = tail + (head - tail) * f;
               sim.missileAt(m, tt, this.tmp);
               float d = Math.max(4.0F, this.tmp[2]);
               float px = this.sx(this.tmp[0], d);
               float py = this.sy(this.tmp[1], d);
               float size = Math.max(1.0F, (float)Math.round(this.scale(d) * 0.35F + (1.0F - f) * this.scale(d) * 0.25F));
               float wob = (float)Math.sin(tt * 40.0F + m) * size * 0.4F;
               this.rect(c, px - size / 2.0F, py - size / 2.0F + wob, px + size / 2.0F, py + size / 2.0F + wob, this.a(15920354, (0.15F + 0.65F * f) * fade));
            }

            if (age < dur) {
               sim.missileAt(m, head, this.tmp);
               float d = Math.max(4.0F, this.tmp[2]);
               float px = this.sx(this.tmp[0], d);
               float py = this.sy(this.tmp[1], d);
               float g = Math.max(2.0F, this.scale(d) * 0.6F);
               this.rect(c, px - g, py - g, px + g, py + g, this.a(16756832, 0.35F));
               this.rect(c, px - g / 2.0F, py - g / 2.0F, px + g / 2.0F, py + g / 2.0F, this.a(16777215, 1.0F));
            }
         }
      }
   }

   private void drawFlyers(Canvas c, BattleSim sim) {
      for (int f = 0; f < 4; f++) {
         if (sim.flAlive[f]) {
            float d = sim.flD[f];
            float s = this.scale(d);
            float bob = (float)Math.sin(this.t * 1.3F + f * 2.0F) * 0.3F;
            float x = this.sx(sim.flX[f], d);
            float y = this.sy(sim.flY[f] + bob, d);
            float dir = Math.signum(sim.flV[f]);
            if (sim.flType[f] == 0) {
               this.drawTransport(c, x, y, s * 6.0F / 12.0F, dir, this.haze(d) * 0.7F);
            } else {
               float u = s * 2.2F / 6.0F;
               this.drawFighter(c, x, y, u, dir, this.haze(d) * 0.7F);
               this.drawFighter(c, x - dir * u * 9.0F, y + u * 3.0F, u, dir, this.haze(d) * 0.7F);
            }
         }
      }
   }

   private void fly(Canvas c, float x, float y, float u, float dir, float lx1, float ly1, float lx2, float ly2, int rgb, float hz, float al) {
      float a1 = x + lx1 * u * dir;
      float a2 = x + lx2 * u * dir;
      this.rect(c, Math.min(a1, a2), y + ly1 * u, Math.max(a1, a2), y + ly2 * u, this.a(mix(rgb, 15902824, hz), al));
   }

   private void drawTransport(Canvas c, float x, float y, float u, float dir, float hz) {
      for (int k = 1; k <= 10; k++) {
         this.fly(c, x, y, u, dir, -5.5F - k * 3.0F, -0.2F, -5.5F - (k - 1) * 3.0F, 0.4F, 16770760, hz, 0.28F * (1.0F - k / 11.0F));
      }

      this.fly(c, x, y, u, dir, -5.0F, -1.0F, 5.0F, 1.2F, 9340798, hz, 1.0F);
      this.fly(c, x, y, u, dir, -5.0F, -1.0F, 5.0F, -0.5F, 12169380, hz, 1.0F);
      this.fly(c, x, y, u, dir, 4.0F, -0.6F, 6.2F, 0.9F, 7301731, hz, 1.0F);
      this.fly(c, x, y, u, dir, 4.6F, -0.3F, 5.8F, 0.3F, 3029572, hz, 1.0F);
      this.fly(c, x, y, u, dir, -3.2F, 0.3F, 3.2F, 0.8F, 7235423, hz, 1.0F);
      this.fly(c, x, y, u, dir, -3.6F, 1.2F, -1.4F, 2.3F, 7827562, hz, 1.0F);
      this.fly(c, x, y, u, dir, 1.4F, 1.2F, 3.6F, 2.3F, 7827562, hz, 1.0F);
      this.fly(c, x, y, u, dir, -6.0F, -0.4F, -5.0F, 0.7F, 10478591, hz * 0.3F, 1.0F);
      this.fly(c, x, y, u, dir, -4.1F, 1.4F, -3.6F, 2.1F, 10478591, hz * 0.3F, 1.0F);
      this.fly(c, x, y, u, dir, -1.9F, 1.4F, -1.4F, 2.1F, 10478591, hz * 0.3F, 1.0F);
   }

   private void drawFighter(Canvas c, float x, float y, float u, float dir, float hz) {
      for (int k = 1; k <= 6; k++) {
         this.fly(c, x, y, u, dir, -2.0F - k * 2.5F, -0.15F, -2.0F - (k - 1) * 2.5F, 0.25F, 16770760, hz, 0.22F * (1.0F - k / 7.0F));
      }

      this.fly(c, x, y, u, dir, -2.0F, -0.5F, 2.4F, 0.5F, 6183509, hz, 1.0F);
      this.fly(c, x, y, u, dir, -0.8F, -1.6F, 0.6F, 1.6F, 4867650, hz, 1.0F);
      this.fly(c, x, y, u, dir, 1.2F, -0.3F, 2.0F, 0.2F, 3029572, hz, 1.0F);
      this.fly(c, x, y, u, dir, -2.6F, -0.3F, -2.0F, 0.3F, 16756832, hz * 0.3F, 1.0F);
   }

   private static int shade(int argbTint, float k) {
      int r = Math.round((argbTint >> 16 & 0xFF) * k);
      int g = Math.round((argbTint >> 8 & 0xFF) * k);
      int b = Math.round((argbTint & 0xFF) * k);
      return r << 16 | g << 8 | b;
   }

   private void drawLedge(Canvas c) {
      int b = this.block;
      int lastX = this.ledgeX0;

      for (int col = 0; col < this.ledgeCols; col++) {
         int x = this.ledgeX0 + col * b;
         int y = this.ledgeEdge[col];

         for (int row = 0; y < this.h; row++) {
            int rh = Math.max(3, Math.round(b * (0.3F + 0.09F * row)));
            int hash = col * 73 + row * 31 & 0xFF;
            String tex = row == 0 ? "red_sand" : (hash % 10 < 3 ? "terracotta" : (hash % 10 < 4 ? "coarse_dirt" : "red_sand"));
            float k = row == 0 ? 1.0F : Math.max(0.42F, 0.86F - 0.1F * row);
            int tint = shade(16043940, k);
            c.texture(tex, x, y, b, rh, this.a(tint, 1.0F));
            y += rh;
         }

         this.rect(c, x, this.ledgeEdge[col], x + b, this.ledgeEdge[col] + Math.max(1, b / 16), this.a(16769192, 0.85F));
         if (col > 0 && this.ledgeEdge[col] != this.ledgeEdge[col - 1]) {
            int top = Math.min(this.ledgeEdge[col], this.ledgeEdge[col - 1]);
            int bottom = Math.max(this.ledgeEdge[col], this.ledgeEdge[col - 1]);
            boolean leftHigher = this.ledgeEdge[col - 1] < this.ledgeEdge[col];
            int fx = leftHigher ? x : x - Math.max(2, b / 8);
            int faceW = Math.max(2, b / 8);
            c.texture("red_sandstone", fx, top, faceW, bottom - top, this.a(shade(16043940, leftHigher ? 0.55F : 0.95F), 1.0F));
         }

         lastX = x + b;
      }

      int faceW = Math.round(b * 0.55F);

      for (int y = this.ledgeEdge[this.ledgeCols - 1]; y < this.h; y += b) {
         c.texture("red_sandstone", lastX, y, faceW, b, this.a(shade(16043940, 0.48F), 1.0F));
      }

      this.rect(c, lastX + faceW, this.ledgeEdge[this.ledgeCols - 1], lastX + faceW + 1, this.h, this.a(2757644, 0.5F));
      int bush = Math.round(b * 0.7F);

      for (int col : new int[]{1, 4}) {
         if (col < this.ledgeCols) {
            int bx = this.ledgeX0 + col * b + b / 4;
            int by = this.ledgeEdge[col] + Math.round(b * 0.18F) - bush;
            c.texture("dead_bush", bx, by, bush, bush, this.a(15255968, 1.0F));
         }
      }
   }

   public void drawAtmosphere(Canvas c, int width, int height, float time, float filter, float dust, boolean grain, float letterbox) {
      this.w = width;
      this.h = height;
      float savedAlpha = this.alpha;
      this.alpha = 1.0F;
      if (filter > 0.001F) {
         c.fill(0, 0, width, height, this.a(16747066, 0.15F * filter));
         c.gradient(0, 0, width, height / 2, this.a(16752720, 0.12F * filter), this.a(16752720, 0.0F));
         c.gradient(0, height / 2, width, height, this.a(2757126, 0.0F), this.a(2757126, 0.3F * filter));
         c.gradient(0, 0, width, height / 5, this.a(1181700, 0.35F * filter), this.a(1181700, 0.0F));
         int strips = 10;
         int stripW = Math.max(1, Math.round(width * 0.16F / strips));

         for (int i = 0; i < strips; i++) {
            float al = 0.3F * filter * (1.0F - (float)i / strips) / 2.2F;
            c.fill(i * stripW, 0, (i + 1) * stripW, height, this.a(1181700, al));
            c.fill(width - (i + 1) * stripW, 0, width - i * stripW, height, this.a(1181700, al));
         }
      }

      if (dust > 0.001F) {
         for (int k = 0; k < this.dustX.length; k++) {
            float x = (this.dustX[k] + time * this.dustV[k]) % 1.0F;
            float y = (this.dustY[k] + time * this.dustV[k] * 0.15F + (float)Math.sin(time * 0.6F + this.dustP[k]) * 0.015F) % 1.0F;
            float flick = 0.5F + 0.5F * (float)Math.sin(time * 2.0F + this.dustP[k] * 3.0F);
            int size = k % 9 == 0 ? 3 : (k % 3 == 0 ? 2 : 1);
            float al = (size == 3 ? 0.14F : 0.45F) * flick * dust;
            int px = Math.round(x * width);
            int py = Math.round(y * height);
            c.fill(px, py, px + size, py + size, this.a(16769712, al));
         }
      }

      if (grain && filter > 0.001F) {
         for (int k = 0; k < 110; k++) {
            int px = this.rnd.nextInt(Math.max(1, width));
            int py = this.rnd.nextInt(Math.max(1, height));
            c.fill(px, py, px + 1, py + 1, this.a(k % 2 == 0 ? 16777215 : 0, 0.07F * filter));
         }
      }

      if (letterbox > 0.001F) {
         int bar = Math.round(height * 0.075F * letterbox);
         c.fill(0, 0, width, bar, -16777216);
         c.fill(0, height - bar, width, height, -16777216);
      }

      this.alpha = savedAlpha;
   }
}
