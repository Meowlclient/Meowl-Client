package com.lennart.nebula.afk.pc;

import com.lennart.nebula.afk.space.Tex;
import java.util.Map;

/** Zeichnet HUD und Waffe direkt ins Spielbild (Minecraft-Schrift, Blocktexturen). */
public final class PcHud {
   private final PcFont font;
   private final Tex iron;
   private final Tex black;
   private final Tex gray;
   private final Tex stone;

   public PcHud(PcFont font, Map<String, Tex> tex) {
      this.font = font;
      this.iron = get(tex, "block/iron_block");
      this.black = get(tex, "block/black_concrete");
      this.gray = get(tex, "block/gray_concrete");
      this.stone = get(tex, "block/stone_bricks");
   }

   private static Tex get(Map<String, Tex> tex, String k) {
      Tex t = tex.get(k);
      return t != null ? t.firstFrame() : Tex.solid(0xFF303030);
   }

   private static void blend(Tex t, int x, int y, int argb) {
      if (x >= 0 && y >= 0 && x < t.w && y < t.h) {
         int a = argb >>> 24;
         if (a >= 255) {
            t.px[y * t.w + x] = argb;
         } else if (a > 0) {
            t.px[y * t.w + x] = Tex.mix(t.px[y * t.w + x], argb | 0xFF000000, a / 255.0F) | 0xFF000000;
         }
      }
   }

   private static void rect(Tex t, int x0, int y0, int x1, int y1, int argb) {
      for (int y = y0; y < y1; y++) {
         for (int x = x0; x < x1; x++) {
            blend(t, x, y, argb);
         }
      }
   }

   /** Gedrehtes Rechteck mit Textur (lokal: Breite w, Höhe h um den Ursprung (ox, oy), Drehung a). */
   private static void texRect(Tex dst, Tex src, float ox, float oy, float a, float lx0, float ly0, float lx1, float ly1, float texScale, float shade) {
      float ca = (float)Math.cos(a);
      float sa = (float)Math.sin(a);
      // Ecken für den Suchbereich
      float minX = Float.MAX_VALUE;
      float minY = Float.MAX_VALUE;
      float maxX = -Float.MAX_VALUE;
      float maxY = -Float.MAX_VALUE;

      for (float[] c : new float[][]{{lx0, ly0}, {lx1, ly0}, {lx0, ly1}, {lx1, ly1}}) {
         float wx = ox + c[0] * ca - c[1] * sa;
         float wy = oy + c[0] * sa + c[1] * ca;
         minX = Math.min(minX, wx);
         minY = Math.min(minY, wy);
         maxX = Math.max(maxX, wx);
         maxY = Math.max(maxY, wy);
      }

      for (int y = (int)Math.floor(minY); y <= (int)Math.ceil(maxY); y++) {
         for (int x = (int)Math.floor(minX); x <= (int)Math.ceil(maxX); x++) {
            float dx = x + 0.5F - ox;
            float dy = y + 0.5F - oy;
            float lx = dx * ca + dy * sa;
            float ly = -dx * sa + dy * ca;
            if (lx >= lx0 && lx < lx1 && ly >= ly0 && ly < ly1) {
               int c = src.at((int)Math.floor(lx * texScale), (int)Math.floor(ly * texScale));
               int r = Math.min(255, Math.round((c >> 16 & 255) * shade));
               int g = Math.min(255, Math.round((c >> 8 & 255) * shade));
               int b = Math.min(255, Math.round((c & 255) * shade));
               blend(dst, x, y, 0xFF000000 | r << 16 | g << 8 | b);
            }
         }
      }
   }

   private static void glow(Tex t, float cx, float cy, float r, int rgb) {
      for (int y = (int)(cy - r); y <= (int)(cy + r); y++) {
         for (int x = (int)(cx - r); x <= (int)(cx + r); x++) {
            float d = (float)Math.hypot(x + 0.5F - cx, y + 0.5F - cy) / r;
            if (d < 1.0F) {
               blend(t, x, y, Math.round(230 * (1.0F - d) * (1.0F - d)) << 24 | rgb & 16777215);
            }
         }
      }
   }

   public void draw(Tex f, PcGame game, PcGame.Snapshot s) {
      int w = f.w;
      int h = f.h;
      float t = s.time;
      // Mündungsfeuer der Gegner
      for (float[] fl : s.flashes) {
         float[] p = game.project(s, fl[0], fl[1], fl[2]);
         if (p != null) {
            glow(f, p[0], p[1], 4.0F, 0xFFFFD27A);
         }
      }

      this.gun(f, s, t);
      // Fadenkreuz
      int cx = w / 2;
      int cy = h / 2;
      int cc = 0xFF40FFF0;
      rect(f, cx - 4, cy, cx - 1, cy + 1, cc);
      rect(f, cx + 2, cy, cx + 5, cy + 1, cc);
      rect(f, cx, cy - 4, cx + 1, cy - 1, cc);
      rect(f, cx, cy + 2, cx + 1, cy + 5, cc);
      if (t - s.lastHit < 0.15F) {
         for (int i = 2; i < 5; i++) {
            blend(f, cx - i - 1, cy - i - 1, 0xFFFFFFFF);
            blend(f, cx + i + 1, cy - i - 1, 0xFFFFFFFF);
            blend(f, cx - i - 1, cy + i + 1, 0xFFFFFFFF);
            blend(f, cx + i + 1, cy + i + 1, 0xFFFFFFFF);
         }
      }

      // Schaden: rote Ränder
      float hurt = Math.max(0.0F, 1.0F - (t - s.lastHurt) * 2.0F);
      if (hurt > 0.0F) {
         for (int y = 0; y < h; y++) {
            float k = Math.max(Math.abs(y - h / 2.0F) / (h / 2.0F) - 0.55F, 0.0F) / 0.45F;
            if (k > 0.0F) {
               rect(f, 0, y, w, y + 1, Math.round(150 * hurt * k) << 24 | 0xC01010);
            }
         }
      }

      // oben: Rundenzeit und Punkte
      int sec = Math.max(0, (int)s.roundTime);
      String clock = sec / 60 + ":" + (sec % 60 < 10 ? "0" : "") + sec % 60;
      int clw = this.font.width(clock, 1);
      rect(f, cx - clw / 2 - 4, 2, cx + clw / 2 + 4, 13, 0xB0101418);
      this.font.draw(f, clock, cx - clw / 2, 4, 1, 0xFFFFFFFF, false);
      String us = String.valueOf(s.scoreUs);
      String them = String.valueOf(s.scoreThem);
      rect(f, cx - clw / 2 - 18, 2, cx - clw / 2 - 5, 13, 0xC0106860);
      this.font.draw(f, us, cx - clw / 2 - 12 - this.font.width(us, 1) / 2, 4, 1, 0xFFFFFFFF, false);
      rect(f, cx + clw / 2 + 5, 2, cx + clw / 2 + 18, 13, 0xC0802030);
      this.font.draw(f, them, cx + clw / 2 + 12 - this.font.width(them, 1) / 2, 4, 1, 0xFFFFFFFF, false);
      // Leben unten links
      String hp = String.valueOf(s.health);
      this.font.draw(f, hp, 5, h - 15, 1, 0xFFFFFFFF, true);
      rect(f, 5, h - 5, 45, h - 3, 0x60FFFFFF);
      rect(f, 5, h - 5, 5 + Math.round(40 * s.health / 100.0F), h - 3, 0xFF40E0C0);
      // Munition unten rechts
      String am = s.reloading ? "Laedt..." : s.ammo + " / 75";
      this.font.draw(f, am, w - 5 - this.font.width(am, 1), h - 15, 1, 0xFFFFFFFF, true);
      // Fähigkeiten
      for (int i = 0; i < 4; i++) {
         int ax = cx - 22 + i * 11;
         rect(f, ax, h - 10, ax + 9, h - 3, i == 3 ? 0xC0FFD040 : 0x90303840);
      }

      // Minikarte
      int n = PcGame.mapSize();
      int ms = 1;
      int mx = 3;
      int my = 3;
      rect(f, mx, my, mx + n * ms, my + n * ms, 0x80101418);

      for (int z = 0; z < n; z++) {
         for (int x = 0; x < n; x++) {
            if (game.wall(x, z)) {
               blend(f, mx + x, my + z, 0x90D8C090);
            }
         }
      }

      float cs = PcGame.cellSize();
      rect(f, mx + (int)(s.px / cs) - 1, my + (int)(s.pz / cs) - 1, mx + (int)(s.px / cs) + 1, my + (int)(s.pz / cs) + 1, 0xFF40E0C0);

      for (float[] d : s.dots) {
         if (d[2] > 0.5F) {
            rect(f, mx + (int)(d[0] / cs), my + (int)(d[1] / cs), mx + (int)(d[0] / cs) + 1, my + (int)(d[1] / cs) + 1, 0xFFFF5060);
         }
      }

      // Abschuss-Meldung
      if (t - s.lastKill < 3.5F && !s.feed.isEmpty()) {
         String fe = s.feed.replace("➜", ">") + (s.headshot ? " (Kopf)" : "");
         int fw = this.font.width(fe, 1);
         boolean mine = s.feed.startsWith("Du");
         rect(f, w - fw - 9, 16, w - 3, 27, 0xB0101418);
         rect(f, w - fw - 9, 16, w - fw - 8, 27, mine ? 0xFF40E0C0 : 0xFFFF5060);
         this.font.draw(f, fe, w - fw - 5, 18, 1, 0xFFFFFFFF, false);
      }

      if (s.dead) {
         rect(f, 0, 0, w, h, 0x90202020);
         String d = "Ausgeschaltet";
         this.font.draw(f, d, cx - this.font.width(d, 2) / 2, cy - 14, 2, 0xFFFF5060, true);
         String fe = s.feed.replace("➜", ">");
         this.font.draw(f, fe, cx - this.font.width(fe, 1) / 2, cy + 6, 1, 0xFFFFFFFF, true);
      }
   }

   /** Teil an einer gedrehten Stelle: Ursprung (lx, ly) im Waffenraum, eigene Zusatzdrehung. */
   private static void piece(Tex f, Tex src, float ox, float oy, float a, float px, float py, float extra, float x0, float y0, float x1, float y1, float shade) {
      float ca = (float)Math.cos(a);
      float sa = (float)Math.sin(a);
      texRect(f, src, ox + px * ca - py * sa, oy + px * sa + py * ca, a + extra, x0, y0, x1, y1, 1.0F, shade);
   }

   /** Vandal-artiges Gewehr unten rechts (nur die Waffe) mit Rückstoß, Laufen und Nachladen. */
   private void gun(Tex f, PcGame.Snapshot s, float t) {
      int w = f.w;
      int h = f.h;
      if (s.dead) {
         return;
      }

      float bob = s.moving ? (float)Math.sin(s.walk) * 2.0F : (float)Math.sin(t * 1.5F) * 0.6F;
      float kick = Math.max(0.0F, 1.0F - (t - s.lastShot) / 0.1F);
      float reload = s.reloading ? (float)Math.sin(Math.max(0.0F, Math.min(1.0F, s.reloadLeft / 2.0F)) * Math.PI) : 0.0F;
      float ox = w * 0.80F + kick * 3.0F;
      float oy = h * 0.90F + bob + kick * 3.0F + reload * 30.0F;
      float a = 0.36F + kick * 0.05F - reload * 0.4F;
      Tex dark = this.black;
      Tex mid = this.gray;
      // Mündung und Lauf
      piece(f, this.iron, ox, oy, a, 0, 0, 0, -66, -1.5F, -58, 1.5F, 0.45F);
      piece(f, this.iron, ox, oy, a, 0, 0, 0, -58, -1.0F, -40, 1.0F, 0.6F);
      // Handschutz mit Schiene
      piece(f, dark, ox, oy, a, 0, 0, 0, -42, -4.5F, -10, 4.0F, 0.95F);
      piece(f, mid, ox, oy, a, 0, 0, 0, -40, -5.5F, -12, -4.5F, 0.7F);
      piece(f, mid, ox, oy, a, 0, 0, 0, -36, -1.0F, -16, 0.0F, 0.45F);
      // Gehäuse
      piece(f, mid, ox, oy, a, 0, 0, 0, -10, -6.0F, 20, 6.0F, 0.5F);
      piece(f, dark, ox, oy, a, 0, 0, 0, -6, -3.0F, 8, -1.5F, 1.0F);
      // Visier-Schiene mit rotem Punkt
      piece(f, dark, ox, oy, a, 0, 0, 0, -8, -9.0F, 16, -6.0F, 1.0F);
      piece(f, Tex.solid(0xFFFF3040), ox, oy, a, 0, 0, 0, 2, -10.5F, 5, -9.0F, 1.0F);
      // gebogenes Magazin (zwei Stücke) und Griff
      piece(f, dark, ox, oy, a, -6, 6, 0.18F, 0, 0, 7, 9, 0.9F);
      piece(f, dark, ox, oy, a, -4, 14, 0.42F, 0, 0, 7, 8, 0.8F);
      piece(f, dark, ox, oy, a, 12, 6, 0.38F, 0, 0, 6, 13, 0.75F);
      // Skelett-Schaft
      piece(f, dark, ox, oy, a, 0, 0, 0, 20, -5.0F, 50, -1.5F, 0.9F);
      piece(f, dark, ox, oy, a, 22, 4, -0.22F, 0, -1.5F, 28, 1.5F, 0.8F);
      piece(f, mid, ox, oy, a, 0, 0, 0, 48, -6.0F, 54, 9.0F, 0.6F);
      // Akzentstreifen
      piece(f, Tex.solid(0xFF40E0C0), ox, oy, a, 0, 0, 0, -10, 5.0F, 20, 6.0F, 1.0F);
      if (kick > 0.4F) {
         float ca = (float)Math.cos(a);
         float sa = (float)Math.sin(a);
         glow(f, ox - 68.0F * ca, oy - 68.0F * sa, 7.0F, 0xFFFFE080);
      }
   }}
