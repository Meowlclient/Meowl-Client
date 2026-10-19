package com.lennart.nebula.afk.space;

/** Einfaches ARGB-Bild (Zeilen von oben nach unten). Reines Java, damit die Szene auch ohne Spiel getestet werden kann. */
public final class Tex {
   public final int w;
   public final int h;
   public final int[] px;

   public Tex(int w, int h) {
      this(w, h, new int[w * h]);
   }

   public Tex(int w, int h, int[] px) {
      this.w = w;
      this.h = h;
      this.px = px;
   }

   /** Nächster Texel, wiederholend (wie Minecraft-Blöcke). */
   public int at(int x, int y) {
      x %= this.w;
      y %= this.h;
      if (x < 0) {
         x += this.w;
      }

      if (y < 0) {
         y += this.h;
      }

      return this.px[y * this.w + x];
   }

   public void set(int x, int y, int argb) {
      if (x >= 0 && y >= 0 && x < this.w && y < this.h) {
         this.px[y * this.w + x] = argb;
      }
   }

   public int get(int x, int y) {
      return x >= 0 && y >= 0 && x < this.w && y < this.h ? this.px[y * this.w + x] : 0;
   }

   /** Erstes 16x16-Bild einer (evtl. animierten) Blocktextur; Graustufen werden zu RGB. */
   public Tex firstFrame() {
      if (this.h <= this.w) {
         return this;
      }

      Tex t = new Tex(this.w, this.w);
      System.arraycopy(this.px, 0, t.px, 0, this.w * this.w);
      return t;
   }

   /** Durchschnittsfarbe (für weit entfernte Flächen). */
   public int average() {
      long r = 0L;
      long g = 0L;
      long b = 0L;
      int n = 0;

      for (int c : this.px) {
         if ((c >>> 24) > 16) {
            r += c >> 16 & 255;
            g += c >> 8 & 255;
            b += c & 255;
            n++;
         }
      }

      return n == 0 ? -8355712 : 0xFF000000 | (int)(r / n) << 16 | (int)(g / n) << 8 | (int)(b / n);
   }

   /** Einfarbiger Ersatz, falls eine Textur fehlt. */
   public static Tex solid(int argb) {
      Tex t = new Tex(16, 16);
      java.util.Arrays.fill(t.px, argb);
      return t;
   }

   public static int argb(int a, float r, float g, float b) {
      return a << 24 | clamp255(r) << 16 | clamp255(g) << 8 | clamp255(b);
   }

   public static int clamp255(float v) {
      return v <= 0.0F ? 0 : (v >= 255.0F ? 255 : Math.round(v));
   }

   public static int mix(int c1, int c2, float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      int a = Math.round((c1 >>> 24) + ((c2 >>> 24) - (c1 >>> 24)) * t);
      int r = Math.round((c1 >> 16 & 0xFF) + ((c2 >> 16 & 0xFF) - (c1 >> 16 & 0xFF)) * t);
      int g = Math.round((c1 >> 8 & 0xFF) + ((c2 >> 8 & 0xFF) - (c1 >> 8 & 0xFF)) * t);
      int b = Math.round((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
      return a << 24 | r << 16 | g << 8 | b;
   }
}
