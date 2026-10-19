package com.lennart.nebula.afk.terra;

import static com.lennart.nebula.afk.terra.TerraTiles.*;

import com.lennart.nebula.afk.space.Tex;

/**
 * Zeichnet die Terraria-Welt als lebendes Diorama: ein Schnitt durch die Welt aus echten Minecraft-Würfeln, mit
 * Zentralperspektive auf ein Auge, das langsam um den Helden kreist – so drehen sich Ober- und Seitenflächen der Blöcke
 * mit, während vorne alles Terraria-flach bleibt. Licht pro Block wie in Terraria (Fackeln, Lava, Leuchtpilze, Himmel),
 * dazu Parallax-Hintergründe, Minecraft-Wolken, Terraria-HUD. Reines Java – die Szene läuft auch ohne Spiel.
 */
public final class TerraView {
   /** Abstand des Auges zur vorderen Ebene (in Blöcken): kleiner = stärkere Perspektive. */
   private static final float DC = 20.0F;
   private static final float D_ENTITY = 0.5F;
   private final TerraAtlas atlas;
   private final int[][][] faces = new int[COUNT][][];
   private final int[][] decoUV = new int[D_COUNT][];
   private final int[] skinUV;
   private final int[][] npcSkin = new int[5][];
   private final int[] zombieUV;
   private final int[] white;
   // Kamera
   private float camX = Float.NaN;
   private float camY;
   private float ex;
   private float ey;
   private float fx;
   private float fy;
   private float s;
   private float w2;
   private float h2;
   private float time;
   private int sw;
   private int sh;
   // Licht
   private int lx0;
   private int ly0;
   private int lw;
   private int lh;
   private float[] lr = new float[0];
   private float[] lg = new float[0];
   private float[] lb = new float[0];
   private final float[] tmp = new float[2];
   // Minikarte
   public final Tex map = new Tex(96, 60);
   public boolean mapDirty = true;
   private float mapT;
   private TerraSim sim;
   private TerraWorld world;
   /** Abstand für Ränder (Kinobalken) oben und unten. */
   private float inset;

   public TerraView(TerraAtlas atlas) {
      this.atlas = atlas;
      for (int t = 1; t < COUNT; t++) {
         String[] n = TerraTiles.TEX[t];
         if (n != null) {
            this.faces[t] = new int[][]{atlas.get(n[0]), atlas.get(n[1]), atlas.get(n[2]), atlas.get(n[3])};
         }
      }

      for (int d = 1; d < D_COUNT; d++) {
         this.decoUV[d] = atlas.get(TerraTiles.DECO_TEX[d]);
      }

      this.skinUV = new int[]{0, 0, 64, 64};
      String[] npcs = {"entity/player/wide/kai", "entity/player/slim/alex", "entity/player/wide/noor", "entity/player/wide/efe", "entity/player/slim/sunny"};

      for (int i = 0; i < npcs.length; i++) {
         this.npcSkin[i] = atlas.has(npcs[i]) ? atlas.get(npcs[i]) : atlas.get("entity/player/wide/steve");
      }

      this.zombieUV = atlas.get("entity/zombie/zombie");
      this.white = atlas.get("p/white");
   }

   // ================================================================== Projektion

   private float k(float d) {
      return DC / (DC + d);
   }

   private float px(float x, float d) {
      return this.w2 + ((x - this.ex) * this.k(d) + (this.ex - this.fx)) * this.s;
   }

   private float py(float y, float d) {
      return this.h2 - ((y - this.ey) * this.k(d) + (this.ey - this.fy)) * this.s;
   }

   /** Weltbereich, der in Tiefe d sichtbar ist: {x0, x1, y0, y1}. */
   private float[] range(float d) {
      float k = this.k(d);
      float x0 = this.ex + ((-this.w2) / this.s - (this.ex - this.fx)) / k;
      float x1 = this.ex + ((this.w2) / this.s - (this.ex - this.fx)) / k;
      float y1 = this.ey + ((this.h2) / this.s - (this.ey - this.fy)) / k;
      float y0 = this.ey + ((-this.h2) / this.s - (this.ey - this.fy)) / k;
      return new float[]{x0, x1, y0, y1};
   }

   private void q(TerraCanvas c, int[] r, float ax, float ay, float ad, float bx, float by, float bd, float cx, float cy, float cd, int tint) {
      c.quad(TerraCanvas.ATLAS, this.px(ax, ad), this.py(ay, ad), this.px(bx, bd), this.py(by, bd), this.px(cx, cd), this.py(cy, cd), r[0], r[1], r[2], r[3], tint);
   }

   /** Teilausschnitt eines Bildes (Bruchteile 0..1) auf ein Rechteck in Tiefe d. */
   private void qSub(TerraCanvas c, int tex, int[] r, float u0, float v0, float u1, float v1, float x0, float yTop, float x1, float yBot, float d, int tint) {
      int u = r[0] + Math.round(r[2] * u0);
      int v = r[1] + Math.round(r[3] * v0);
      int uw = Math.max(1, Math.round(r[2] * (u1 - u0)));
      int vh = Math.max(1, Math.round(r[3] * (v1 - v0)));
      c.quad(tex, this.px(x0, d), this.py(yTop, d), this.px(x1, d), this.py(yTop, d), this.px(x0, d), this.py(yBot, d), u, v, uw, vh, tint);
   }

   // ================================================================== Licht

   private int li(int x, int y) {
      int ix = x - this.lx0;
      int iy = y - this.ly0;
      return ix >= 0 && iy >= 0 && ix < this.lw && iy < this.lh ? iy * this.lw + ix : -1;
   }

   private static int rgb(float r, float g, float b) {
      return 0xFF000000 | Tex.clamp255(r * 255.0F) << 16 | Tex.clamp255(g * 255.0F) << 8 | Tex.clamp255(b * 255.0F);
   }

   private int light(int x, int y, float shade) {
      int i = this.li(x, y);
      return i < 0 ? 0xFF000000 : rgb(this.lr[i] * shade, this.lg[i] * shade, this.lb[i] * shade);
   }

   private float lum(int x, int y) {
      int i = this.li(x, y);
      return i < 0 ? 0.0F : Math.max(this.lr[i], Math.max(this.lg[i], this.lb[i]));
   }

   private void computeLight(float[] rr) {
      TerraWorld wd = this.world;
      this.lx0 = (int)Math.floor(rr[0]) - 14;
      int lx1 = (int)Math.ceil(rr[1]) + 14;
      this.ly0 = Math.max(0, (int)Math.floor(rr[2]) - 14);
      int ly1 = Math.min(TerraWorld.WH - 1, (int)Math.ceil(rr[3]) + 14);
      this.lw = lx1 - this.lx0 + 1;
      this.lh = Math.max(1, ly1 - this.ly0 + 1);
      int n = this.lw * this.lh;
      if (this.lr.length < n) {
         this.lr = new float[n];
         this.lg = new float[n];
         this.lb = new float[n];
      }

      // winziges Grundlicht, damit tief unten noch Struktur zu ahnen ist
      java.util.Arrays.fill(this.lr, 0, n, 0.03F);
      java.util.Arrays.fill(this.lg, 0, n, 0.03F);
      java.util.Arrays.fill(this.lb, 0, n, 0.045F);
      float dl = this.sim.daylight();
      float du = this.sim.dusk();
      float skyR = dl * (1.0F - 0.05F * du);
      float skyG = dl * (1.0F - 0.25F * du);
      float skyB = dl * (1.0F - 0.42F * du);
      if (dl < 0.5F) {
         float nt = (0.5F - dl) / 0.25F;
         skyR *= 1.0F - 0.3F * nt;
         skyB *= 1.0F + 0.7F * nt;
      }

      float[] decay = new float[n];

      for (int x = this.lx0; x <= lx1; x++) {
         boolean open = true;
         float att = 1.0F;

         for (int y = TerraWorld.WH - 1; y >= this.ly0; y--) {
            int t = wd.tile(x, y);
            if (TerraTiles.solid(t) && !TerraTiles.SEE_THROUGH[t] || t == LAVA) {
               open = false;
            }

            if (t == WATER) {
               att *= 0.82F;
            }

            if (y > ly1) {
               continue;
            }

            int i = (y - this.ly0) * this.lw + (x - this.lx0);
            decay[i] = TerraTiles.solid(t) && !TerraTiles.SEE_THROUGH[t] ? 0.42F : (t == WATER ? 0.8F : 0.845F);
            int bt = wd.backTile(x, y);
            if (open && TerraTiles.SKY_PASS[bt]) {
               this.lr[i] = skyR * att;
               this.lg[i] = skyG * att;
               this.lb[i] = skyB * att;
            } else if (open) {
               // unter einer Wand (Haus): etwas Streulicht
               this.lr[i] = skyR * att * 0.45F;
               this.lg[i] = skyG * att * 0.45F;
               this.lb[i] = skyB * att * 0.45F;
            }

            float[] gl = TerraTiles.GLOW[t];
            if (gl != null) {
               this.emit(i, gl, 1.0F);
            }

            int d = wd.deco(x, y);
            float[] dg = TerraTiles.DECO_GLOW[d];
            if (dg != null) {
               this.emit(i, dg, d == D_TORCH ? 0.95F + 0.05F * (float)Math.sin(this.time * 9.0F + x * 3 + y) : 1.0F);
            }
         }
      }

      // Laternen in Häusern, Fackel des Helden
      for (TerraWorld.House h : wd.houses) {
         for (int[] p : h.props) {
            if (p[0] == TerraWorld.P_LANTERN) {
               int i = this.li(p[1], p[2]);
               if (i >= 0) {
                  this.emit(i, new float[]{1.0F, 0.85F, 0.55F}, 1.0F);
               }
            }
         }
      }

      TerraSim.Hero hero = this.sim.hero;
      if (hero.holdTorch && hero.death <= 0.0F) {
         int i = this.li((int)Math.floor(hero.x - hero.dir * 0.4F), (int)Math.floor(hero.y + 2.0F));
         if (i >= 0) {
            float f = 0.92F + 0.08F * (float)Math.sin(this.time * 11.0F);
            this.emit(i, new float[]{1.0F, 0.82F, 0.55F}, f);
         }
      }

      for (TerraSim.Mob m : this.sim.mobs) {
         if (m.type == TerraSim.BOSS) {
            int i = this.li((int)Math.floor(m.x), (int)Math.floor(m.y + m.h / 2.0F));
            if (i >= 0) {
               this.emit(i, new float[]{0.5F, 0.12F, 0.1F}, 1.0F);
            }
         }
      }

      // Ausbreitung: vier Richtungen, zweimal
      int lwv = this.lw;

      for (int pass = 0; pass < 2; pass++) {
         for (int y = 0; y < this.lh; y++) {
            int row = y * lwv;

            for (int x = 1; x < lwv; x++) {
               this.spread(row + x - 1, row + x, decay);
            }

            for (int x = lwv - 2; x >= 0; x--) {
               this.spread(row + x + 1, row + x, decay);
            }
         }

         for (int x = 0; x < lwv; x++) {
            for (int y = 1; y < this.lh; y++) {
               this.spread((y - 1) * lwv + x, y * lwv + x, decay);
            }

            for (int y = this.lh - 2; y >= 0; y--) {
               this.spread((y + 1) * lwv + x, y * lwv + x, decay);
            }
         }
      }
   }

   private void emit(int i, float[] c, float f) {
      this.lr[i] = Math.max(this.lr[i], c[0] * f);
      this.lg[i] = Math.max(this.lg[i], c[1] * f);
      this.lb[i] = Math.max(this.lb[i], c[2] * f);
   }

   private void spread(int from, int to, float[] decay) {
      float k = decay[to];
      float r = this.lr[from] * k;
      float g = this.lg[from] * k;
      float b = this.lb[from] * k;
      if (r > this.lr[to]) {
         this.lr[to] = r;
      }

      if (g > this.lg[to]) {
         this.lg[to] = g;
      }

      if (b > this.lb[to]) {
         this.lb[to] = b;
      }
   }

   // ================================================================== Hauptzeichnen

   public void render(TerraCanvas c, int w, int h, float pxRatio, TerraSim sim, float dt, boolean camMove, float inset, boolean hud) {
      this.sim = sim;
      this.world = sim.world;
      this.inset = inset;
      this.time += dt;
      this.sw = w;
      this.sh = h;
      this.w2 = w / 2.0F;
      this.h2 = h / 2.0F;
      // Ganzzahlige Pixel je Texel (scharfe Blöcke)
      float realH = h * pxRatio;
      int texel = Math.max(2, Math.round(realH / 23.0F / 16.0F));
      this.s = texel * 16.0F / pxRatio;
      // Kamera folgt dem Helden mit etwas Vorausschau
      TerraSim.Hero hero = sim.hero;
      float tx = hero.x + hero.dir * 2.5F + hero.vx * 0.3F;
      float ty = hero.drawY + 0.6F;
      if (Float.isNaN(this.camX)) {
         this.camX = tx;
         this.camY = ty;
      }

      this.camX += (tx - this.camX) * Math.min(1.0F, dt * 2.2F);
      this.camY += (ty - this.camY) * Math.min(1.0F, dt * 2.6F);
      this.fx = this.camX;
      this.fy = this.camY;
      // Das Auge kreist langsam: Ober- und Seitenflächen drehen sich mit
      float orbitX = camMove ? (float)Math.sin(this.time * 0.08F) * 6.5F : 2.5F;
      float orbitY = camMove ? 6.0F + (float)Math.sin(this.time * 0.057F + 1.0F) * 3.0F : 6.0F;
      this.ex = this.fx + orbitX;
      this.ey = this.fy + orbitY;
      float[] far = this.range(3.0F);
      this.computeLight(far);
      this.sky(c, w, h);
      this.backdrop(c);
      this.terrain(c, far);
      this.updateMap();
      if (hud) {
         this.hud(c, w, h);
      }
   }

   // ================================================================== Himmel

   private static int lerp3(int day, int dusk, int night, float dl, float du) {
      int base = Tex.mix(night, day, (dl - 0.25F) / 0.75F);
      return Tex.mix(base, dusk, du * 0.85F);
   }

   private int biomeTint(int c, float k) {
      int b = this.world.biomeAt((int)Math.floor(this.fx));
      int t = switch (b) {
         case TerraWorld.CRIMSON -> 0xFFC04848;
         case TerraWorld.JUNGLE -> 0xFF78C878;
         case TerraWorld.SNOW -> 0xFFD8E4F4;
         case TerraWorld.DESERT -> 0xFFF0D090;
         default -> c;
      };
      return Tex.mix(c, t, k);
   }

   private void sky(TerraCanvas c, int w, int h) {
      float dl = this.sim.daylight();
      float du = this.sim.dusk();
      int top = this.biomeTint(lerp3(0xFF3E7EDC, 0xFF3A3470, 0xFF04061A, dl, du), 0.12F);
      int hor = this.biomeTint(lerp3(0xFFA6D2FF, 0xFFF08A58, 0xFF141A44, dl, du), 0.25F);
      float horizon = this.py(this.ey, 1000.0F);
      int bands = 28;
      float y0 = 0.0F;
      float y1 = Math.max(10.0F, horizon + h * 0.08F);

      for (int i = 0; i < bands; i++) {
         float a = (float)i / bands;
         float b = (float)(i + 1) / bands;
         c.fill(0.0F, y0 + (y1 - y0) * a, w, y0 + (y1 - y0) * b + 1.0F, Tex.mix(top, hor, a * a));
      }

      if (y1 < h) {
         c.fill(0.0F, y1, w, h, hor);
      }

      // Sterne
      float night = Math.max(0.0F, 1.0F - (dl - 0.25F) / 0.45F);
      if (night > 0.01F) {
         java.util.Random r = new java.util.Random(99L);

         for (int i = 0; i < 140; i++) {
            float sx = r.nextFloat() * w;
            float sy = r.nextFloat() * Math.max(1.0F, horizon);
            float tw = 0.55F + 0.45F * (float)Math.sin(this.time * (1.0F + r.nextFloat() * 2.0F) + i);
            float sz = r.nextFloat() < 0.15F ? 2.0F : 1.0F;
            int a = Math.round(220 * night * tw);
            c.fill(sx, sy, sx + sz, sy + sz, a << 24 | (r.nextFloat() < 0.2F ? 0xFFE8C0 : 0xFFFFFF));
         }
      }

      // Sonne und Mond auf ihrem Bogen
      float t = this.sim.dayT;
      boolean isNight = this.sim.night();
      float p = isNight ? (t - TerraSim.NIGHT_START) / (TerraSim.DAY_LEN - TerraSim.NIGHT_START) : t / TerraSim.NIGHT_START;
      float cx = w * (0.08F + 0.84F * p);
      float cy = h * (0.34F - 0.24F * (float)Math.sin(Math.PI * p)) + this.inset;
      float size = h * (isNight ? 0.085F : 0.12F);
      int[] body = this.atlas.get(isNight ? "environment/celestial/moon/full_moon" : "environment/celestial/sun");
      c.quad(TerraCanvas.ATLAS, cx - size / 2.0F, cy - size / 2.0F, cx + size / 2.0F, cy - size / 2.0F, cx - size / 2.0F, cy + size / 2.0F, body[0], body[1], body[2], body[3], isNight ? 0xFFE8ECFF : 0xFFFFF4D0);
      this.clouds(c, w, h, horizon, dl, du);
   }

   /** Minecraft-Wolken (clouds.png) als zwei langsam ziehende Bänder. */
   private void clouds(TerraCanvas c, int w, int h, float horizon, float dl, float du) {
      int[] r = this.atlas.get("environment/clouds");
      if (r[2] < 64) {
         return;
      }

      int col = Tex.mix(Tex.mix(0xFF3A4060, 0xFFFFFFFF, (dl - 0.25F) / 0.75F), 0xFFFFB090, du * 0.6F);

      for (int band = 0; band < 2; band++) {
         float texelW = (band == 0 ? 7.0F : 5.0F) * this.s / 24.0F;
         float texelH = texelW * 0.45F;
         float y = horizon - h * (band == 0 ? 0.36F : 0.22F);
         int rows = 10;
         int v = r[1] + (band == 0 ? 40 : 150);
         float scroll = this.time * (band == 0 ? 1.2F : 0.7F) + this.fx * (band == 0 ? 0.6F : 0.35F) * texelW / 4.0F;
         float span = r[2] * texelW;
         float start = -(scroll % span);
         if (start > 0.0F) {
            start -= span;
         }

         int alpha = band == 0 ? 0xE0 : 0xA0;

         for (float x = start; x < w; x += span) {
            c.quad(TerraCanvas.ATLAS, x, y, x + span, y, x, y + rows * texelH, r[0], v, r[2], rows, alpha << 24 | col & 0xFFFFFF);
         }
      }
   }

   private void backdrop(TerraCanvas c) {
      float dl = this.sim.daylight();
      float du = this.sim.dusk();
      int tint = Tex.mix(Tex.mix(0xFF262C50, 0xFFFFFFFF, (dl - 0.25F) / 0.75F), 0xFFFFB890, du * 0.45F);
      // Biome überblenden
      int bx = (int)Math.floor(this.fx);
      int b0 = this.world.biomeAt(bx);
      int b1 = b0;
      float mixB = 0.0F;

      for (int d = 2; d <= 40; d += 2) {
         int r = this.world.biomeAt(bx + d);
         if (r != b0) {
            b1 = r;
            mixB = Math.max(0.0F, 0.5F - d / 80.0F);
            break;
         }

         int l = this.world.biomeAt(bx - d);
         if (l != b0) {
            b1 = l;
            mixB = Math.max(0.0F, 0.5F - d / 80.0F);
            break;
         }
      }

      this.layer(c, TerraCanvas.BG_FAR, TerraBackdrop.W, TerraBackdrop.FAR_H, 90.0F, 0.42F, 52.0F, tint);
      for (int pass = 0; pass < 2; pass++) {
         int b = pass == 0 ? b0 : b1;
         float a = pass == 0 ? 1.0F : mixB;
         if (a <= 0.01F) {
            continue;
         }

         int t = Math.round(a * 255) << 24 | tint & 0xFFFFFF;
         this.layer(c, 16 + b * 2, TerraBackdrop.W, TerraBackdrop.MID_H, 32.0F, 0.26F, 50.0F, t);
      }

      for (int pass = 0; pass < 2; pass++) {
         int b = pass == 0 ? b0 : b1;
         float a = pass == 0 ? 1.0F : mixB;
         if (a <= 0.01F) {
            continue;
         }

         int t = Math.round(a * 255) << 24 | Tex.mix(tint, 0xFF000000, 0.12F) & 0xFFFFFF;
         this.layer(c, 17 + b * 2, TerraBackdrop.W, TerraBackdrop.NEAR_H, 12.0F, 0.17F, 50.0F, t);
      }
   }

   private void layer(TerraCanvas c, int tex, int tw, int th, float d, float q, float bottom, int tint) {
      float[] r = this.range(d);
      float period = tw * q;
      float top = bottom + th * q;
      if (this.py(top, d) > this.sh) {
         return;
      }

      float start = (float)Math.floor(r[0] / period) * period;

      for (float x = start; x < r[1]; x += period) {
         c.quad(tex, this.px(x, d), this.py(top, d), this.px(x + period, d), this.py(top, d), this.px(x, d), this.py(bottom, d), 0, 0, tw, th, tint);
      }

      // unter der Ebene: dunkle Füllung, damit kein Himmel durchscheint
      float yb = this.py(bottom, d);
      if (yb < this.sh) {
         c.fill(0.0F, yb - 1.0F, this.sw, this.sh, Tex.mix(0xFF101820, tint | 0xFF000000, 0.15F));
      }
   }

   // ================================================================== Welt

   private boolean opaque(int t) {
      return t != AIR && TerraTiles.solid(t) && !TerraTiles.SEE_THROUGH[t];
   }

   /** Fläche zu einem Nachbarn sichtbar? */
   private boolean showFace(int t, int n) {
      if (n == AIR) {
         return true;
      } else if (TerraTiles.liquid(t)) {
         return false;
      } else {
         return (TerraTiles.SEE_THROUGH[n] || !TerraTiles.solid(n)) && n != t;
      }
   }

   private int[] uv(int t, int face) {
      if (t == WATER || t == LAVA) {
         int f = (int)(this.time * (t == LAVA ? 5.0F : 8.0F)) % TerraAtlas.ANIM_FRAMES;
         return this.atlas.get((t == LAVA ? "block/lava_still#" : "block/water_still#") + f);
      }

      return this.faces[t][face];
   }

   private void terrain(TerraCanvas c, float[] far) {
      TerraWorld wd = this.world;
      int x0 = (int)Math.floor(far[0]) - 1;
      int x1 = (int)Math.ceil(far[1]) + 1;
      int y0 = Math.max(0, (int)Math.floor(far[2]) - 1);
      int y1 = Math.min(TerraWorld.WH - 1, (int)Math.ceil(far[3]) + 1);
      // 1) Höhlen-Hintergrund und hintere Ebene (Wände, Bäume, Hauswände)
      for (int y = y0; y <= y1; y++) {
         for (int x = x0; x <= x1; x++) {
            int b = wd.backTile(x, y);
            if (b == AIR) {
               if (y < wd.surface(x) - 3) {
                  int[] r = this.faces[DEEPSLATE][0];
                  this.q(c, r, x, y + 1, 2.6F, x + 1, y + 1, 2.6F, x, y, 2.6F, this.light(x, y, 0.22F));
               }
            } else if (this.visibleBehind(x, y)) {
               this.cube(c, x, y, b, 1.0F, 2.0F, true, 0);
            }
         }
      }

      for (int y = y0; y <= y1; y++) {
         for (int x = x0; x <= x1; x++) {
            int b = wd.backTile(x, y);
            if (b != AIR && this.visibleBehind(x, y)) {
               this.cube(c, x, y, b, 1.0F, 2.0F, true, 1);
            }
         }
      }

      // 2) Möbel und Wandfackeln
      this.props(c);

      for (int y = y0; y <= y1; y++) {
         for (int x = x0; x <= x1; x++) {
            int d = wd.deco(x, y);
            if (d == D_TORCH) {
               this.torch(c, x, y);
            }
         }
      }

      // 3) Seiten-, Ober- und Unterflächen der vorderen Ebene
      for (int y = y0; y <= y1; y++) {
         for (int x = x0; x <= x1; x++) {
            int t = wd.tile(x, y);
            if (t != AIR) {
               this.cube(c, x, y, t, 0.0F, 1.0F, false, 0);
            }
         }
      }

      // 4) Pflanzen, Figuren, Effekte
      for (int y = y0; y <= y1; y++) {
         for (int x = x0; x <= x1; x++) {
            int d = wd.deco(x, y);
            if (d != D_NONE && d != D_TORCH) {
               this.plant(c, x, y, d);
            }
         }
      }

      this.entities(c);
      // 5) Vorderflächen (verdecken, was hinter Wänden steht); Wasser halb durchsichtig über den Figuren
      for (int y = y0; y <= y1; y++) {
         for (int x = x0; x <= x1; x++) {
            int t = wd.tile(x, y);
            if (t != AIR) {
               this.cube(c, x, y, t, 0.0F, 1.0F, false, 1);
            }
         }
      }

      this.overlays(c);
   }

   /** Hinterer Block: nur nötig, wenn vorne in der Nähe Luft ist. */
   private boolean visibleBehind(int x, int y) {
      TerraWorld wd = this.world;

      for (int dy = -1; dy <= 1; dy++) {
         for (int dx = -1; dx <= 1; dx++) {
            if (!this.opaque(wd.tile(x + dx, y + dy))) {
               return true;
            }
         }
      }

      return false;
   }

   /** Würfel zeichnen: pass 0 = Seiten/Oben/Unten, pass 1 = Vorderseite. */
   private void cube(TerraCanvas c, int x, int y, int t, float d0, float d1, boolean back, int pass) {
      TerraWorld wd = this.world;
      boolean liquid = TerraTiles.liquid(t);
      boolean glow = t == LAVA;
      float dim = back ? 0.42F : 1.0F;
      if (t == DOOR_LOW || t == DOOR_HIGH) {
         this.door(c, x, y, t, pass);
         return;
      }

      if (pass == 0) {
         float top = liquid ? y + 0.88F : y + 1;
         int above = back ? wd.backTile(x, y + 1) : wd.tile(x, y + 1);
         int below = back ? wd.backTile(x, y - 1) : wd.tile(x, y - 1);
         int right = back ? wd.backTile(x + 1, y) : wd.tile(x + 1, y);
         int left = back ? wd.backTile(x - 1, y) : wd.tile(x - 1, y);
         if (this.ey > top && (liquid ? above == AIR : this.showFace(t, above))) {
            int col = glow ? -1 : this.light(x, y + 1, 1.08F * dim);
            if (liquid && !glow) {
               col = 0xC0000000 | col & 0xFFFFFF;
            }

            this.q(c, this.uv(t, 1), x, top, d0, x + 1, top, d0, x, top, d1, col);
         }

         if (this.ey < y && this.showFace(t, below) && !liquid) {
            this.q(c, this.uv(t, 3), x, y, d0, x + 1, y, d0, x, y, d1, glow ? -1 : this.light(x, y - 1, 0.55F * dim));
         }

         if (this.ex > x + 1 && this.showFace(t, right)) {
            this.q(c, this.uv(t, 2), x + 1, top, d0, x + 1, top, d1, x + 1, y, d0, glow ? -1 : this.light(x + 1, y, 0.74F * dim));
         }

         if (this.ex < x && this.showFace(t, left)) {
            this.q(c, this.uv(t, 2), x, top, d0, x, top, d1, x, y, d0, glow ? -1 : this.light(x - 1, y, 0.74F * dim));
         }
      } else {
         int[] r = this.uv(t, 0);
         if (glow) {
            this.q(c, r, x, y + 1, d0, x + 1, y + 1, d0, x, y, d0, -1);
            return;
         }

         if (liquid) {
            int above = wd.tile(x, y + 1);
            float top = above == t ? y + 1 : y + 0.88F;
            int col = this.light(x, y, 1.0F);
            c.quad(TerraCanvas.ATLAS, this.px(x, d0), this.py(top, d0), this.px(x + 1, d0), this.py(top, d0), this.px(x, d0), this.py(y, d0), r[0], r[1], r[2], Math.max(1, Math.round(r[3] * (top - y))), 0xA8000000 | col & 0xFFFFFF);
            return;
         }

         float mul = dim;
         this.smoothFront(c, r, x, y, d0, mul);
         if (!back) {
            this.edges(c, x, y, t, d0);
         }
      }
   }

   /** Vorderseite mit weichem Licht (2 x 2 Teilflächen, Ecken gemittelt), wenn sich das Licht ändert. */
   private void smoothFront(TerraCanvas c, int[] r, int x, int y, float d, float mul) {
      int ic = this.li(x, y);
      if (ic < 0) {
         return;
      }

      float cr = this.lr[ic];
      float cg = this.lg[ic];
      float cb = this.lb[ic];
      if (mul >= 1.0F) {
         // freiliegende Blöcke werden von der Luft daneben beleuchtet (Terraria: Ränder hell, Inneres dunkel)
         for (int k = 0; k < 4; k++) {
            int nx = x + (k == 0 ? 1 : (k == 1 ? -1 : 0));
            int ny = y + (k == 2 ? 1 : (k == 3 ? -1 : 0));
            int ni = this.li(nx, ny);
            if (ni >= 0 && !this.opaque(this.world.tile(nx, ny))) {
               cr = Math.max(cr, this.lr[ni] * 0.82F);
               cg = Math.max(cg, this.lg[ni] * 0.82F);
               cb = Math.max(cb, this.lb[ni] * 0.82F);
            }
         }
      }

      float[][] corner = new float[4][];
      float maxDiff = 0.0F;

      for (int k = 0; k < 4; k++) {
         int cx = x + (k & 1);
         int cy = y + (k >> 1);
         float sr = 0.0F;
         float sg = 0.0F;
         float sb = 0.0F;
         int n = 0;

         for (int dy = -1; dy <= 0; dy++) {
            for (int dx = -1; dx <= 0; dx++) {
               int i = this.li(cx + dx, cy + dy);
               if (i >= 0) {
                  sr += this.lr[i];
                  sg += this.lg[i];
                  sb += this.lb[i];
                  n++;
               }
            }
         }

         if (n > 0) {
            sr /= n;
            sg /= n;
            sb /= n;
         }

         corner[k] = new float[]{sr, sg, sb};
         maxDiff = Math.max(maxDiff, Math.abs(sr - cr) + Math.abs(sg - cg) + Math.abs(sb - cb));
      }

      if (maxDiff < 0.06F || r[2] < 2 || r[3] < 2) {
         this.q(c, r, x, y + 1, d, x + 1, y + 1, d, x, y, d, rgb(cr * mul, cg * mul, cb * mul));
         return;
      }

      int hw = r[2] / 2;
      int hh = r[3] / 2;

      for (int k = 0; k < 4; k++) {
         int i = k & 1;
         int j = k >> 1;
         float[] cc = corner[k];
         int col = rgb((cr + cc[0]) * 0.5F * mul, (cg + cc[1]) * 0.5F * mul, (cb + cc[2]) * 0.5F * mul);
         float sx = x + i * 0.5F;
         float sy = y + (j + 1) * 0.5F;
         int[] sub = {r[0] + i * hw, r[1] + (1 - j) * hh, hw, hh};
         this.q(c, sub, sx, sy, d, sx + 0.5F, sy, d, sx, sy - 0.5F, d, col);
      }
   }

   /** Dunkle Kanten an freien Seiten (Terraria-Blockrahmen). */
   private void edges(TerraCanvas c, int x, int y, int t, float d) {
      TerraWorld wd = this.world;
      float e = 1.5F / 16.0F;
      int col = 0x80000000;
      float sx0 = this.px(x, d);
      float sx1 = this.px(x + 1, d);
      float sy0 = this.py(y + 1, d);
      float sy1 = this.py(y, d);
      float ep = this.s * e;
      if (!this.opaque(wd.tile(x, y + 1)) && !TerraTiles.SEE_THROUGH[t]) {
         c.fill(sx0, sy0, sx1, sy0 + ep, col);
      }

      if (!this.opaque(wd.tile(x, y - 1))) {
         c.fill(sx0, sy1 - ep, sx1, sy1, col);
      }

      if (!this.opaque(wd.tile(x - 1, y))) {
         c.fill(sx0, sy0, sx0 + ep, sy1, col);
      }

      if (!this.opaque(wd.tile(x + 1, y))) {
         c.fill(sx1 - ep, sy0, sx1, sy1, col);
      }
   }

   private void door(TerraCanvas c, int x, int y, int t, int pass) {
      TerraWorld.House h = this.world.houseAt(x);
      float open = h == null ? 0.0F : (x == h.x0 ? h.doorAnimL : h.doorAnimR);
      int[] r = this.faces[t][0];
      int col = this.light(x, y, 1.0F);
      if (open < 0.05F) {
         if (pass == 1) {
            this.q(c, r, x, y + 1, 0.0F, x + 1, y + 1, 0.0F, x, y, 0.0F, col);
         }

         return;
      }

      if (pass == 0) {
         // geöffnet: Tür klappt nach hinten (Scharnier an der Hausseite)
         boolean leftHouse = h != null && x == h.x0;
         float hinge = leftHouse ? x + 1 : x;
         float ang = open * 1.45F;
         float dx = (float)Math.cos(ang) * (leftHouse ? -1.0F : 1.0F);
         float dd = (float)Math.sin(ang);
         this.q(c, r, hinge, y + 1, 0.05F, hinge + dx, y + 1, 0.05F + dd, hinge, y, 0.05F, col);
      }
   }

   private void props(TerraCanvas c) {
      for (TerraWorld.House h : this.world.houses) {
         for (int[] p : h.props) {
            int x = p[1];
            int y = p[2];
            if (p[0] == TerraWorld.P_LANTERN) {
               int[] r = this.atlas.get("block/lantern");
               this.qSub(c, TerraCanvas.ATLAS, r, 0.0F, 0.0F, 1.0F, 1.0F, x + 0.15F, y + 1.0F, x + 0.85F, y + 0.25F, 0.8F, -1);
               continue;
            }

            String front;
            String top;
            String side;
            switch (p[0]) {
               case TerraWorld.P_TABLE -> {
                  front = "block/crafting_table_front";
                  top = "block/crafting_table_top";
                  side = "block/crafting_table_side";
               }
               case TerraWorld.P_BARREL -> {
                  front = "block/barrel_side";
                  top = "block/barrel_top";
                  side = "block/barrel_side";
               }
               case TerraWorld.P_FURNACE -> {
                  front = "block/furnace_front";
                  top = "block/furnace_top";
                  side = "block/furnace_side";
               }
               default -> {
                  front = "block/bookshelf";
                  top = "block/oak_planks";
                  side = "block/bookshelf";
               }
            }

            float d0 = 0.58F;
            float d1 = 1.0F;
            int col = this.light(x, y, 1.0F);
            boolean stacked = p[0] == TerraWorld.P_BOOKS && y > h.floor;
            boolean under = p[0] == TerraWorld.P_BOOKS && y == h.floor;
            if (this.ey > y + 1 && !under) {
               this.q(c, this.atlas.get(top), x, y + 1, d0, x + 1, y + 1, d0, x, y + 1, d1, this.light(x, y + 1, 1.08F));
            }

            if (this.ex > x + 1) {
               this.q(c, this.atlas.get(side), x + 1, y + 1, d0, x + 1, y + 1, d1, x + 1, y, d0, this.light(x + 1, y, 0.72F));
            } else if (this.ex < x) {
               this.q(c, this.atlas.get(side), x, y + 1, d0, x, y + 1, d1, x, y, d0, this.light(x - 1, y, 0.72F));
            }

            this.q(c, this.atlas.get(front), x, y + 1, d0, x + 1, y + 1, d0, x, y, d0, col);
            if (stacked) {
               continue;
            }
         }
      }
   }

   private void torch(TerraCanvas c, int x, int y) {
      int[] r = this.decoUV[D_TORCH];
      float d = 0.96F;
      this.qSub(c, TerraCanvas.ATLAS, r, 0.0F, 0.0F, 1.0F, 1.0F, x, y + 1.0F, x + 1, y, d, -1);
      // warmer Schein
      int[] g = this.atlas.get("p/glow");
      float f = 0.8F + 0.2F * (float)Math.sin(this.time * 9.0F + x * 3 + y);
      this.qSub(c, TerraCanvas.ATLAS, g, 0.0F, 0.0F, 1.0F, 1.0F, x - 1.0F, y + 2.6F, x + 2.0F, y - 0.4F, d, Math.round(70 * f) << 24 | 0xFFC060);
   }

   private void plant(TerraCanvas c, int x, int y, int d) {
      int[] r = this.decoUV[d];
      boolean glow = TerraTiles.DECO_GLOW[d] != null && d != D_CRIMSON_FUNGUS;
      int col = glow ? -1 : this.light(x, y, 1.0F);
      float sway = d <= D_BLUET ? (float)Math.sin(this.time * 1.6F + x * 0.7F) * 0.06F : 0.0F;
      float dd = D_ENTITY;
      c.quad(TerraCanvas.ATLAS, this.px(x + sway, dd), this.py(y + 1, dd), this.px(x + 1 + sway, dd), this.py(y + 1, dd), this.px(x, dd), this.py(y, dd), r[0], r[1], r[2], r[3], col);
   }

   // ================================================================== Figuren

   private int lightAt(float x, float y) {
      int c = this.light((int)Math.floor(x), (int)Math.floor(y), 1.0F);
      // nie ganz schwarz, damit man die Figuren erkennt
      int r = Math.max(28, c >> 16 & 255);
      int g = Math.max(28, c >> 8 & 255);
      int b = Math.max(36, c & 255);
      return 0xFF000000 | r << 16 | g << 8 | b;
   }

   private static int mul(int a, int b) {
      int r = (a >> 16 & 255) * (b >> 16 & 255) / 255;
      int g = (a >> 8 & 255) * (b >> 8 & 255) / 255;
      int bl = (a & 255) * (b & 255) / 255;
      int al = (a >>> 24) * (b >>> 24) / 255;
      return al << 24 | r << 16 | g << 8 | bl;
   }

   /** Weltpunkt in Figurentiefe → Bildschirm. */
   private float ex(float x) {
      return this.px(x, D_ENTITY);
   }

   private float ey(float y) {
      return this.py(y, D_ENTITY);
   }

   private void wq(TerraCanvas c, int tex, float p0x, float p0y, float p1x, float p1y, float p3x, float p3y, int u, int v, int uw, int vh, int tint) {
      c.quad(tex, this.ex(p0x), this.ey(p0y), this.ex(p1x), this.ey(p1y), this.ex(p3x), this.ey(p3y), u, v, uw, vh, tint);
   }

   /** Gedrehtes Bild um einen Mittelpunkt (Welt, y nach oben). */
   private void sprite(TerraCanvas c, int[] r, float cx, float cy, float w, float h, float angle, boolean flip, int tint) {
      float ux = (float)Math.cos(angle) * w;
      float uy = (float)Math.sin(angle) * w;
      float vx = (float)Math.sin(angle) * h;
      float vy = -(float)Math.cos(angle) * h;
      if (flip) {
         ux = -ux;
         vx = -vx;
      }

      float p0x = cx - ux / 2.0F - vx / 2.0F;
      float p0y = cy - uy / 2.0F - vy / 2.0F;
      this.wq(c, TerraCanvas.ATLAS, p0x, p0y, p0x + ux, p0y + uy, p0x + vx, p0y + vy, r[0], r[1], r[2], r[3], tint);
   }

   /** Item-Bild (Spitze oben rechts) an einer Hand, Klinge zeigt in Richtung (dx, dy). */
   private void held(TerraCanvas c, int[] r, float hx, float hy, float dx, float dy, float size, int dir, int tint, float tdx, float tdy, float hu, float hv) {
      // Texturrichtung (tdx, tdy) [y nach unten] auf Weltrichtung (dx, dy) drehen
      float ta = (float)Math.atan2(-tdy, tdx);
      float wa = (float)Math.atan2(dy, dx * dir);
      float a = wa - ta;
      float sc = size / 16.0F;
      float uxl = (float)Math.cos(a) * sc;
      float uyl = (float)Math.sin(a) * sc;
      float vxl = (float)Math.sin(a) * sc;
      float vyl = -(float)Math.cos(a) * sc;
      // lokal (Blick nach rechts), dann spiegeln
      float p0xl = -(hu * uxl + hv * vxl);
      float p0yl = -(hu * uyl + hv * vyl);
      float p0x = hx + p0xl * dir;
      float p0y = hy + p0yl;
      float p1x = p0x + 16.0F * uxl * dir;
      float p1y = p0y + 16.0F * uyl;
      float p3x = p0x + 16.0F * vxl * dir;
      float p3y = p0y + 16.0F * vyl;
      this.wq(c, TerraCanvas.ATLAS, p0x, p0y, p1x, p1y, p3x, p3y, r[0], r[1], r[2], r[3], tint);
   }

   /** Haltung einer Figur. Winkel: 0 = hängt nach unten, positiv = nach vorne. */
   private static final class Pose {
      float legA;
      float legB;
      float armNear;
      float armFar;
      float bob;
   }

   /**
    * Minecraft-Skin von der Seite (Terraria-Proportionen: etwas größerer Kopf). Liefert {Hand vorne x, y, Hand hinten x,
    * y, Schulter vorne x, y}.
    */
   private float[] humanoid(TerraCanvas c, int tex, int bu, int bv, boolean legacy, float x, float y, float height, int dir, Pose p, int tint) {
      float hs = 1.15F;
      float k = height / (24.0F + 8.0F * hs);
      float yb = y + p.bob * k;
      int far = mul(tint, 0xFFB4B4B4);
      // ferne Gliedmaßen (linke Seite, Innenfläche)
      int lArmU = legacy ? 40 : 32;
      int lArmV = legacy ? 20 : 52;
      int lLegU = legacy ? 0 : 16;
      int lLegV = legacy ? 20 : 52;
      float[] farHand = this.limb(c, tex, bu + lArmU, bv + lArmV, x, yb + 22.5F * k, p.armFar, k, dir, far, legacy ? -1 : bu + 48, bv + 52);
      this.limb(c, tex, bu + lLegU, bv + lLegV, x, yb + 12.0F * k, p.legB, k, dir, far, legacy ? -1 : bu, bv + 52);
      // Körper
      this.part(c, tex, bu + 16, bv + 20, 4, 12, x - 2.0F * k * dir, yb + 24.0F * k, 4.0F * k, 12.0F * k, dir, tint);
      if (!legacy) {
         this.part(c, tex, bu + 16, bv + 36, 4, 12, x - 2.1F * k * dir, yb + 24.2F * k, 4.2F * k, 12.4F * k, dir, tint);
      }

      // nahes Bein
      this.limb(c, tex, bu, bv + 20, x, yb + 12.0F * k, p.legA, k, dir, tint, legacy ? -1 : bu, bv + 36);
      // Kopf (rechte Seite) + Hut
      float hw = 8.0F * k * hs;
      this.part(c, tex, bu, bv + 8, 8, 8, x - hw / 2.0F * dir, yb + 24.0F * k + hw, hw, hw, dir, tint);
      this.part(c, tex, bu + 32, bv + 8, 8, 8, x - hw * 0.53F * dir, yb + 24.0F * k + hw * 1.06F, hw * 1.06F, hw * 1.06F, dir, tint);
      // nahes Arm
      float[] nearHand = this.limb(c, tex, bu + 40, bv + 20, x, yb + 22.5F * k, p.armNear, k, dir, tint, legacy ? -1 : bu + 40, bv + 36);
      return new float[]{nearHand[0], nearHand[1], farHand[0], farHand[1], x, yb + 22.5F * k};
   }

   /** Gerades Teil, links oben bei (x0, yTop), Breite nach vorne. */
   private void part(TerraCanvas c, int tex, int u, int v, int uw, int vh, float x0, float yTop, float w, float h, int dir, int tint) {
      this.wq(c, tex, x0, yTop, x0 + w * dir, yTop, x0, yTop - h, u, v, uw, vh, tint);
   }

   /** Arm oder Bein (4 x 12) um einen Drehpunkt; liefert das untere Ende (Hand/Fuß). */
   private float[] limb(TerraCanvas c, int tex, int u, int v, float px, float py, float a, float k, int dir, int tint, int ou, int ov) {
      float dx = (float)Math.sin(a) * dir;
      float dy = -(float)Math.cos(a);
      float fxv = (float)Math.cos(a) * dir;
      float fyv = (float)Math.sin(a);
      float w = 4.0F * k;
      float len = 12.0F * k;
      float p0x = px - fxv * w / 2.0F;
      float p0y = py - fyv * w / 2.0F;
      this.wq(c, tex, p0x, p0y, p0x + fxv * w, p0y + fyv * w, p0x + dx * len, p0y + dy * len, u, v, 4, 12, tint);
      if (ou >= 0) {
         float e = 0.15F * k;
         float q0x = p0x - fxv * e;
         float q0y = p0y - fyv * e;
         this.wq(c, tex, q0x, q0y, q0x + fxv * (w + 2.0F * e), q0y + fyv * (w + 2.0F * e), q0x + dx * (len + e), q0y + dy * (len + e), ou, ov, 4, 12, tint);
      }

      return new float[]{px + dx * len * 0.92F, py + dy * len * 0.92F};
   }

   private void entities(TerraCanvas c) {
      TerraSim sm = this.sim;
      // NPCs
      for (TerraSim.Npc n : sm.npcs) {
         Pose p = new Pose();
         float sw = (float)Math.sin(n.walk) * Math.min(1.0F, Math.abs(n.walk) > 0.01F ? 0.6F : 0.0F);
         p.legA = sw;
         p.legB = -sw;
         p.armNear = -sw * 0.8F;
         p.armFar = sw * 0.8F;
         int[] r = this.npcSkin[n.skin % this.npcSkin.length];
         this.humanoid(c, TerraCanvas.ATLAS, r[0], r[1], r[3] < 64, n.x, n.y, 2.75F, n.dir, p, this.lightAt(n.x, n.y + 1.5F));
      }

      // Gegner
      for (TerraSim.Mob m : sm.mobs) {
         int lt = this.lightAt(m.x, m.y + m.h / 2.0F);
         if (m.hurt > 0.0F) {
            lt = Tex.mix(lt, 0xFFFF6060, 0.6F);
         }

         switch (m.type) {
            case TerraSim.SLIME -> {
               float sq = m.squash;
               float stretch = m.ground ? 1.0F : 1.12F;
               float w = 1.5F * (1.0F + 0.25F * sq) / stretch;
               float h = 1.1F * (1.0F - 0.22F * sq) * stretch;
               int col = mul(m.color, lt);
               this.sprite(c, this.atlas.get("p/slime"), m.x, m.y + h / 2.0F, w, h, 0.0F, m.dir < 0, 0xE0000000 | col & 0xFFFFFF);
            }
            case TerraSim.ZOMBIE -> {
               Pose p = new Pose();
               float sw = (float)Math.sin(m.t * 5.0F) * 0.55F;
               p.legA = sw;
               p.legB = -sw;
               p.armNear = 1.5F + sw * 0.1F;
               p.armFar = 1.45F - sw * 0.1F;
               int[] r = this.zombieUV;
               this.humanoid(c, TerraCanvas.ATLAS, r[0], r[1], r[3] < 64, m.x, m.y, 2.75F, m.dir, p, lt);
            }
            case TerraSim.EYE, TerraSim.SERVANT -> {
               float sz = m.type == TerraSim.EYE ? 1.0F : 0.75F;
               this.sprite(c, this.atlas.get("p/eye"), m.x, m.y + m.h / 2.0F, 1.7F * sz, 1.0F * sz, (float)Math.atan2(m.vy, m.vx), false, Tex.mix(lt, 0xFFFFFFFF, 0.35F));
            }
            case TerraSim.BUNNY -> this.sprite(c, this.atlas.get("p/bunny"), m.x, m.y + 0.34F, 0.9F, 0.68F, 0.0F, m.dir < 0, lt);
            default -> {
               int[] r = this.atlas.get(m.phase == 1 ? "p/boss1" : "p/boss2");
               this.sprite(c, r, m.x, m.y + m.h / 2.0F, 6.0F, 5.0F, m.angle, false, Tex.mix(lt, 0xFFFFFFFF, 0.45F));
            }
         }
      }

      this.hero(c);
      // Pfeile
      int[] ar = this.atlas.get("item/arrow");

      for (TerraSim.Arrow a : sm.arrows) {
         float sp = (float)Math.hypot(a.vx, a.vy);
         float dx = sp > 0.01F ? a.vx / sp : 1.0F;
         float dy = sp > 0.01F ? a.vy / sp : 0.0F;
         this.held(c, ar, a.x, a.y, dx, dy, 1.0F, 1, this.lightAt(a.x, a.y), 1.0F, -1.0F, 13.0F, 3.0F);
      }

      // Beute
      for (TerraSim.Drop d : sm.drops) {
         int[] r = this.atlas.get(d.sprite);
         boolean coin = d.coins > 0;
         float size = coin ? 0.4F : 0.62F;
         float bob = (float)Math.sin(this.time * 4.0F + d.x) * 0.06F;
         int tint = coin ? -1 : this.lightAt(d.x, d.y + 0.3F);
         this.sprite(c, r, d.x, d.y + size / 2.0F + bob, size, size, 0.0F, false, tint);
         if (coin && ((int)(this.time * 6.0F + d.x * 7.0F) & 7) == 0) {
            c.fill(this.ex(d.x) - 1.0F, this.ey(d.y + size) - 1.0F, this.ex(d.x) + 1.0F, this.ey(d.y + size) + 1.0F, 0xFFFFFFFF);
         }
      }

      // Partikel
      for (TerraSim.Particle p : sm.particles) {
         float a = Math.min(1.0F, p.life / Math.max(0.01F, p.max) * 2.0F);
         float sx = this.ex(p.x);
         float sy = this.ey(p.y);
         float sz = p.size * this.s * (p.kind == TerraSim.P_GORE ? 1.6F : 1.0F);
         if (p.tex != null) {
            int[] r = this.atlas.get(p.tex);
            int u = r[0] + p.texOff % 3 * (r[2] / 4);
            int v = r[1] + p.texOff / 3 * (r[3] / 4);
            int lt = this.light((int)Math.floor(p.x), (int)Math.floor(p.y), 1.0F);
            c.quad(TerraCanvas.ATLAS, sx, sy, sx + sz, sy, sx, sy + sz, u, v, Math.max(1, r[2] / 4), Math.max(1, r[3] / 4), Math.round(a * 255) << 24 | lt & 0xFFFFFF);
         } else if (p.kind == TerraSim.P_GLOW) {
            c.fill(sx - sz, sy - sz, sx + sz, sy + sz, Math.round(a * 120) << 24 | p.color & 0xFFFFFF);
         } else {
            c.fill(sx - sz / 2.0F, sy - sz / 2.0F, sx + sz / 2.0F, sy + sz / 2.0F, Math.round(a * 255) << 24 | p.color & 0xFFFFFF);
         }
      }

      // Glühwürmchen in der Nacht
      float night = Math.max(0.0F, 1.0F - (this.sim.daylight() - 0.25F) / 0.4F);
      if (night > 0.02F) {
         java.util.Random r = new java.util.Random(5L);
         float base = (float)Math.floor(this.fx / 40.0F) * 40.0F;

         for (int i = 0; i < 26; i++) {
            float wx = base - 30.0F + r.nextFloat() * 100.0F + (float)Math.sin(this.time * 0.4F + i) * 1.5F;
            int gx = (int)Math.floor(wx);
            float wy = this.world.surface(gx) + 0.8F + r.nextFloat() * 4.0F + (float)Math.cos(this.time * 0.6F + i * 2) * 0.8F;
            float tw = Math.max(0.0F, (float)Math.sin(this.time * 2.0F + i * 1.7F));
            float sx = this.ex(wx);
            float sy = this.ey(wy);
            float sz = this.s * 0.07F;
            c.fill(sx - sz * 2.0F, sy - sz * 2.0F, sx + sz * 2.0F, sy + sz * 2.0F, Math.round(50 * tw * night) << 24 | 0xD8FF60);
            c.fill(sx - sz, sy - sz, sx + sz, sy + sz, Math.round(230 * tw * night) << 24 | 0xF4FFB0);
         }
      }
   }

   private void hero(TerraCanvas c) {
      TerraSim.Hero h = this.sim.hero;
      if (h.death > 0.0F) {
         return;
      }

      if (h.immune > 0.0F && ((int)(h.immune * 14.0F) & 1) == 1 && h.immune < 2.5F) {
         return;
      }

      Pose p = new Pose();
      float moving = Math.min(1.0F, Math.abs(h.vx) / 4.0F);
      float sw = (float)Math.sin(h.walk) * 0.7F * moving;
      p.legA = sw;
      p.legB = -sw;
      p.armNear = -sw * 0.8F;
      p.armFar = sw * 0.8F;
      p.bob = Math.abs((float)Math.cos(h.walk)) * 0.6F * moving;
      if (!h.ground && !h.inWater) {
         p.legA = 0.5F;
         p.legB = -0.35F;
         p.armNear = h.vy > 0.0F ? 2.6F : 2.1F;
         p.armFar = h.vy > 0.0F ? -2.4F : -2.0F;
      }

      int[] item = null;
      float itemSize = 1.2F;
      float tdx = 1.0F;
      float tdy = -1.0F;
      float hu = 3.0F;
      float hv = 13.0F;
      boolean extend = false;
      if (h.swing >= 0.0F) {
         float t = h.swing;
         float e = t < 0.5F ? 2.0F * t * t : 1.0F - (float)Math.pow(-2.0F * t + 2.0F, 2.0) / 2.0F;
         p.armNear = 3.7F - e * 3.5F;
         item = this.atlas.get(h.item == TerraSim.ITEM_SWORD ? "item/diamond_sword" : "item/diamond_pickaxe");
         itemSize = h.item == TerraSim.ITEM_SWORD ? 1.55F : 1.3F;
         extend = true;
      } else if (h.bow >= 0.0F) {
         p.armNear = (float)(Math.PI / 2) + h.aim;
         p.armFar = (float)(Math.PI / 2) + h.aim - 0.15F;
         item = this.atlas.get(h.bow > 0.4F ? "item/bow_pulling_2" : "item/bow");
         itemSize = 1.15F;
         tdx = 1.0F;
         tdy = -1.0F;
         hu = 8.0F;
         hv = 8.0F;
         extend = true;
      } else if (h.drink >= 0.0F) {
         p.armNear = 2.5F + (float)Math.sin(h.drink * Math.PI) * 0.3F;
         item = this.atlas.get("p/potion");
         itemSize = 0.8F;
         tdx = 0.0F;
         tdy = -1.0F;
         hu = 8.0F;
         hv = 12.0F;
      }

      if (h.holdTorch && h.bow < 0.0F) {
         p.armFar = 1.25F;
      }

      int lt = this.lightAt(h.x, h.y + 1.6F);
      float[] hands = this.humanoidWithItems(c, h, p, lt);
      // Item in der vorderen Hand
      if (item != null) {
         float a = p.armNear;
         float dx = (float)Math.sin(a);
         float dy = -(float)Math.cos(a);
         if (h.bow >= 0.0F) {
            // Bogen quer zur Schussrichtung halten
            this.held(c, item, hands[0], hands[1], dx, dy, itemSize, h.dir, lt, tdx, tdy, hu, hv);
         } else if (h.drink >= 0.0F) {
            this.held(c, item, hands[0], hands[1], 0.0F, 1.0F, itemSize, h.dir, lt, tdx, tdy, hu, hv);
         } else {
            this.held(c, item, hands[0], hands[1], dx, dy, itemSize, h.dir, lt, tdx, tdy, hu, hv);
         }

         if (extend && h.item == TerraSim.ITEM_SWORD && h.swing > 0.15F && h.swing < 0.8F) {
            // leichter Schwungbogen
            float[] sh = {hands[4], hands[5]};
            float len = 2.1F;

            for (int i = 0; i < 6; i++) {
               float aa = p.armNear + i * 0.12F;
               float ax = sh[0] + (float)Math.sin(aa) * len * h.dir;
               float ay = sh[1] - (float)Math.cos(aa) * len;
               float sx = this.ex(ax);
               float sy = this.ey(ay);
               float sz = this.s * 0.06F * (6 - i);
               c.fill(sx - sz, sy - sz, sx + sz, sy + sz, (90 - i * 14) << 24 | 0x9CF4FF);
            }
         }
      }
   }

   private float[] humanoidWithItems(TerraCanvas c, TerraSim.Hero h, Pose p, int lt) {
      // Fackel in der hinteren Hand zuerst (liegt hinter dem Körper)
      if (h.holdTorch && h.bow < 0.0F) {
         float hs = 1.15F;
         float k = 2.9F / (24.0F + 8.0F * hs);
         float sx = h.x;
         float sy = h.drawY + 22.5F * k;
         float hx = sx + (float)Math.sin(p.armFar) * 12.0F * k * 0.92F * h.dir;
         float hy = sy - (float)Math.cos(p.armFar) * 12.0F * k * 0.92F;
         int[] tr = this.decoUV[D_TORCH];
         this.wq(c, TerraCanvas.ATLAS, hx - 0.45F, hy + 0.75F, hx + 0.45F, hy + 0.75F, hx - 0.45F, hy - 0.15F, tr[0], tr[1], tr[2], tr[3], -1);
      }

      return this.humanoid(c, TerraCanvas.SKIN, 0, 0, false, h.x, h.drawY, 2.9F, h.dir, p, lt);
   }

   // ================================================================== Texte über der Welt

   private void overlays(TerraCanvas c) {
      TerraSim sm = this.sim;
      float ts = this.s / 24.0F;

      for (TerraSim.Popup p : sm.popups) {
         float a = Math.min(1.0F, p.life / 0.4F);
         float sc = ts * p.scale * (p.scale > 1.0F ? 1.25F : 1.05F);
         String s = p.text;
         float tw = c.textWidth(s) * sc;
         c.text(s, this.ex(p.x) - tw / 2.0F, this.ey(p.y), sc, Math.round(a * 255) << 24 | p.color & 0xFFFFFF, true);
      }

      for (TerraSim.Npc n : sm.npcs) {
         if (n.bubbleT > 0.0F && n.bubble != null) {
            float a = Math.min(1.0F, n.bubbleT / 0.5F);
            float sc = ts * 0.7F;
            String name = n.name;
            float tw = Math.max(c.textWidth(n.bubble), c.textWidth(name)) * sc;
            float bx = this.ex(n.x) - tw / 2.0F;
            float by = this.ey(n.y + 3.6F) - 20.0F * sc;
            c.fill(bx - 4.0F, by - 3.0F, bx + tw + 4.0F, by + 20.0F * sc + 2.0F, Math.round(a * 170) << 24 | 0x141830);
            c.fill(bx - 4.0F, by - 3.0F, bx + tw + 4.0F, by - 2.0F, Math.round(a * 255) << 24 | 0x6070C0);
            c.text(name, bx, by, sc, Math.round(a * 255) << 24 | 0x9CC4FF, true);
            c.text(n.bubble, bx, by + 10.0F * sc, sc, Math.round(a * 255) << 24 | 0xFFFFFF, true);
         }
      }
   }

   // ================================================================== Minikarte

   private void updateMap() {
      this.mapT -= 1.0F / 60.0F;
      if (this.mapT > 0.0F) {
         return;
      }

      this.mapT = 0.5F;
      TerraWorld wd = this.world;
      int cx = (int)Math.floor(this.sim.hero.x);
      int cy = (int)Math.floor(this.sim.hero.y);
      int mw = this.map.w;
      int mh = this.map.h;
      int sky = Tex.mix(0xFF0E1430, 0xFF6AA6E8, (this.sim.daylight() - 0.25F) / 0.75F);

      for (int yy = 0; yy < mh; yy++) {
         for (int xx = 0; xx < mw; xx++) {
            int x = cx - mw / 2 + xx;
            int y = cy + mh / 2 - yy;
            int col;
            if (y < 0 || y >= TerraWorld.WH) {
               col = 0xFF000000;
            } else {
               int t = wd.tile(x, y);
               int b = wd.backTile(x, y);
               if (t != AIR) {
                  col = this.atlas.average(TerraTiles.TEX[t][t == GRASS || t == SNOW_GRASS || t == CRIMSON_GRASS || t == MUSHROOM_GRASS ? 1 : 0]);
               } else if (b != AIR) {
                  col = Tex.mix(this.atlas.average(TerraTiles.TEX[b][0]), 0xFF000000, 0.55F);
               } else if (y >= wd.surface(x) - 2) {
                  col = sky;
               } else {
                  col = 0xFF0C0C14;
               }

               if (wd.deco(x, y) == D_TORCH) {
                  col = 0xFFFFD060;
               }
            }

            this.map.px[yy * mw + xx] = col;
         }
      }

      this.mapDirty = true;
   }

   // ================================================================== HUD

   private static final String[] HOTBAR = {
      "item/diamond_pickaxe", "item/diamond_sword", "item/bow", "block/torch", "p/potion", "block/dirt", "block/oak_planks", "block/warped_fungus", "item/slime_ball", "item/stick"
   };
   private static final String[] HOTBAR_NAMES = {
      "Diamantspitzhacke", "Diamantschwert", "Holzbogen", "Fackel", "Heiltrank", "Erdblock", "Holz", "Leuchtpilz", "Glibber", "Stock"
   };

   private void hud(TerraCanvas c, int w, int h) {
      TerraSim.Hero hero = this.sim.hero;
      float top = this.inset + 5.0F;
      float s = Math.max(14.0F, Math.min(24.0F, h * 0.052F));
      float ts = s / 20.0F;
      // Hotbar oben links
      int sel = switch (hero.item) {
         case TerraSim.ITEM_SWORD -> 1;
         case TerraSim.ITEM_BOW -> 2;
         case TerraSim.ITEM_POTION -> 4;
         default -> 0;
      };
      if (hero.holdTorch && hero.swing < 0.0F && hero.bow < 0.0F && hero.drink < 0.0F) {
         sel = 3;
      }

      c.text(HOTBAR_NAMES[sel], 6.0F, top, ts * 0.8F, 0xFFFFFFFF, true);
      float y = top + 11.0F * ts;
      float x = 6.0F;

      for (int i = 0; i < 10; i++) {
         float sz = i == sel ? s * 1.12F : s;
         float yy = y + (i == sel ? -s * 0.06F : 0.0F);
         c.fill(x, yy, x + sz, yy + sz, i == sel ? 0xD0283C9C : 0xA0283070);
         int edge = i == sel ? 0xFFFFE070 : 0xFF141C48;
         c.fill(x, yy, x + sz, yy + 1.0F, edge);
         c.fill(x, yy + sz - 1.0F, x + sz, yy + sz, edge);
         c.fill(x, yy, x + 1.0F, yy + sz, edge);
         c.fill(x + sz - 1.0F, yy, x + sz, yy + sz, edge);
         int[] r = this.atlas.get(HOTBAR[i]);
         float pad = sz * 0.16F;
         c.quad(TerraCanvas.ATLAS, x + pad, yy + pad, x + sz - pad, yy + pad, x + pad, yy + sz - pad, r[0], r[1], r[2], r[3], -1);
         c.text(String.valueOf((i + 1) % 10), x + 2.0F, yy + 1.5F, ts * 0.5F, 0xE0FFFFFF, true);
         x += sz + 2.0F;
      }

      // Münzen unter der Hotbar
      long coins = hero.coins;
      long gold = coins / 10000;
      long silver = coins / 100 % 100;
      long copper = coins % 100;
      float cy = y + s * 1.2F;
      float cx = 6.0F;
      String[] cs = {"p/coin_gold", "p/coin_silver", "p/coin_copper"};
      long[] cv = {gold, silver, copper};

      for (int i = 0; i < 3; i++) {
         if (i == 0 && gold == 0) {
            continue;
         }

         int[] r = this.atlas.get(cs[i]);
         float isz = 7.0F * ts;
         c.quad(TerraCanvas.ATLAS, cx, cy, cx + isz, cy, cx, cy + isz, r[0], r[1], r[2], r[3], -1);
         String t = String.valueOf(cv[i]);
         c.text(t, cx + isz + 2.0F, cy, ts * 0.6F, 0xFFFFFFFF, true);
         cx += isz + 6.0F + c.textWidth(t) * ts * 0.6F;
      }

      // Leben und Mana oben rechts
      float hs = s * 0.48F;
      float starCol = w - 6.0F - hs;
      float heartsRight = starCol - 6.0F;
      String life = "Leben: " + Math.round(hero.hp) + "/" + Math.round(hero.maxHp);
      c.text(life, heartsRight - c.textWidth(life) * ts * 0.7F, top, ts * 0.7F, 0xFFFFFFFF, true);
      int[] heart = this.atlas.has("gui/sprites/hud/heart/full") ? this.atlas.get("gui/sprites/hud/heart/full") : this.atlas.get("p/heart");
      int[] cont = this.atlas.has("gui/sprites/hud/heart/container") ? this.atlas.get("gui/sprites/hud/heart/container") : heart;

      for (int i = 0; i < 20; i++) {
         int row = i / 10;
         int col = i % 10;
         float hx = heartsRight - (10 - col) * (hs + 1.0F);
         float hy = top + 10.0F * ts + row * (hs + 1.0F);
         float f = Math.max(0.0F, Math.min(1.0F, (hero.hp - i * 20.0F) / 20.0F));
         c.quad(TerraCanvas.ATLAS, hx, hy, hx + hs, hy, hx, hy + hs, cont[0], cont[1], cont[2], cont[3], 0xC0FFFFFF);
         if (f > 0.0F) {
            float sz = hs * (0.55F + 0.45F * f);
            float o = (hs - sz) / 2.0F;
            c.quad(TerraCanvas.ATLAS, hx + o, hy + o, hx + o + sz, hy + o, hx + o, hy + o + sz, heart[0], heart[1], heart[2], heart[3], Math.round(140 + 115 * f) << 24 | 0xFFFFFF);
         }
      }

      c.text("Mana", starCol + hs / 2.0F - c.textWidth("Mana") * ts * 0.35F, top, ts * 0.7F, 0xFFFFFFFF, true);
      int[] star = this.atlas.get("p/star");

      for (int i = 0; i < 10; i++) {
         float f = Math.max(0.0F, Math.min(1.0F, (hero.mana - i * 20.0F) / 20.0F));
         float sy = top + 10.0F * ts + i * (hs + 1.0F);
         float sz = hs * (0.5F + 0.5F * f);
         float o = (hs - sz) / 2.0F;
         c.quad(TerraCanvas.ATLAS, starCol + o, sy + o, starCol + o + sz, sy + o, starCol + o, sy + o + sz, star[0], star[1], star[2], star[3], Math.round(90 + 165 * f) << 24 | 0xFFFFFF);
      }

      // Minikarte
      float mw = s * 5.6F;
      float mh = mw * this.map.h / this.map.w;
      float mx = heartsRight - mw;
      float my = top + 10.0F * ts + 2.0F * (hs + 1.0F) + 6.0F;
      c.fill(mx - 3.0F, my - 3.0F, mx + mw + 3.0F, my + mh + 3.0F, 0xFF8C6A34);
      c.fill(mx - 2.0F, my - 2.0F, mx + mw + 2.0F, my + mh + 2.0F, 0xFFD8B060);
      c.fill(mx - 1.0F, my - 1.0F, mx + mw + 1.0F, my + mh + 1.0F, 0xFF3A2814);
      c.quad(TerraCanvas.MAP, mx, my, mx + mw, my, mx, my + mh, 0, 0, this.map.w, this.map.h, -1);
      float mcx = mx + mw / 2.0F;
      float mcy = my + mh / 2.0F;
      float face = Math.max(5.0F, mw / 14.0F);
      c.quad(TerraCanvas.SKIN, mcx - face / 2.0F, mcy - face, mcx + face / 2.0F, mcy - face, mcx - face / 2.0F, mcy, 8, 8, 8, 8, -1);
      float px = mw / this.map.w;

      for (TerraSim.Mob m : this.sim.mobs) {
         if (m.type == TerraSim.BUNNY) {
            continue;
         }

         float dx = (m.x - hero.x) * px;
         float dy = -(m.y - hero.y) * px;
         if (Math.abs(dx) < mw / 2.0F && Math.abs(dy) < mh / 2.0F) {
            float sz = m.type == TerraSim.BOSS ? 4.0F : 2.0F;
            c.fill(mcx + dx - sz / 2.0F, mcy + dy - sz, mcx + dx + sz / 2.0F, mcy + dy, m.type == TerraSim.BOSS ? 0xFFFF40FF : 0xFFFF4040);
         }
      }

      // Tiefe und Uhrzeit
      int surf = this.world.surface((int)Math.floor(hero.x));
      int depth = Math.round((surf - hero.y) * 2.0F);
      String dtext = depth > 4 ? depth + " m unter der Oberfläche" : "Oberfläche";
      float clock = (this.sim.dayT / TerraSim.DAY_LEN * 24.0F + 4.5F) % 24.0F;
      String time = String.format(java.util.Locale.ROOT, "%02d:%02d", (int)clock, (int)(clock % 1.0F * 60.0F));
      c.text(time + "  ·  " + dtext, mx, my + mh + 5.0F, ts * 0.6F, 0xFFE8E8E8, true);
      // Bossleiste
      TerraSim.Mob boss = this.sim.boss;
      float bottom = h - this.inset - 6.0F;
      if (boss != null && !boss.dead) {
         float bw = Math.min(w * 0.46F, 300.0F);
         float bh = 9.0F * ts;
         float bx = (w - bw) / 2.0F;
         float by = bottom - bh - 4.0F;
         c.fill(bx - 2.0F, by - 2.0F, bx + bw + 2.0F, by + bh + 2.0F, 0xE0101010);
         c.fill(bx, by, bx + bw, by + bh, 0xFF3A0A0A);
         float f = Math.max(0.0F, boss.hp / boss.max);
         c.fill(bx, by, bx + bw * f, by + bh * 0.55F, 0xFFE8322E);
         c.fill(bx, by + bh * 0.55F, bx + bw * f, by + bh, 0xFFA01818);
         String hp = Math.max(0, Math.round(boss.hp)) + "/" + Math.round(boss.max);
         c.text(hp, bx + (bw - c.textWidth(hp) * ts * 0.6F) / 2.0F, by + bh * 0.2F, ts * 0.6F, 0xFFFFFFFF, true);
         String name = "Auge von Cthulhu";
         c.text(name, bx + (bw - c.textWidth(name) * ts * 0.8F) / 2.0F, by - 11.0F * ts, ts * 0.8F, 0xFFFFFFFF, true);
         int[] icon = this.atlas.get("p/boss1");
         float isz = bh * 2.0F;
         c.quad(TerraCanvas.ATLAS, bx - isz - 4.0F, by + bh / 2.0F - isz / 2.0F, bx - 4.0F, by + bh / 2.0F - isz / 2.0F, bx - isz - 4.0F, by + bh / 2.0F + isz / 2.0F, icon[0], icon[1], icon[2], icon[3], -1);
         bottom = by - 16.0F * ts;
      }

      // Meldungen unten links
      float my2 = bottom - 10.0F * ts;

      for (int i = this.sim.messages.size() - 1; i >= 0; i--) {
         TerraSim.Message m = this.sim.messages.get(i);
         float a = Math.min(1.0F, m.life);
         c.text(m.text, 6.0F, my2, ts * 0.75F, Math.round(a * 255) << 24 | m.color & 0xFFFFFF, true);
         my2 -= 10.0F * ts;
      }

      if (hero.death > 0.0F) {
         String t = "Du wurdest besiegt …";
         float sc = ts * 2.0F;
         c.text(t, (w - c.textWidth(t) * sc) / 2.0F, h * 0.42F, sc, 0xFFE83030, true);
      }
   }
}
