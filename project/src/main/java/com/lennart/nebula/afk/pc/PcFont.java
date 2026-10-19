package com.lennart.nebula.afk.pc;

import com.lennart.nebula.afk.space.Tex;

/** Minecraft-Schrift (font/ascii.png) direkt in ein Bild zeichnen – für die gemalten Bildschirme. */
public final class PcFont {
   private final Tex glyphs;
   private final int cell;
   private final int[] widths = new int[256];

   public PcFont(Tex ascii) {
      this.glyphs = ascii == null ? null : ascii.firstFrame();
      this.cell = this.glyphs == null ? 8 : this.glyphs.w / 16;

      for (int c = 0; c < 256; c++) {
         this.widths[c] = c == ' ' ? this.cell / 2 : this.measure(c);
      }
   }

   private int measure(int c) {
      if (this.glyphs == null) {
         return this.cell * 3 / 4;
      }

      int gx = c % 16 * this.cell;
      int gy = c / 16 * this.cell;

      for (int x = this.cell - 1; x >= 0; x--) {
         for (int y = 0; y < this.cell; y++) {
            if ((this.glyphs.get(gx + x, gy + y) >>> 24) > 100) {
               return x + 1;
            }
         }
      }

      return this.cell / 2;
   }

   /** Umlaute und Sonderzeichen auf ASCII abbilden. */
   private static int code(char ch) {
      return switch (ch) {
         case 'ä' -> 'a';
         case 'ö' -> 'o';
         case 'ü' -> 'u';
         case 'Ä' -> 'A';
         case 'Ö' -> 'O';
         case 'Ü' -> 'U';
         case 'ß' -> 's';
         case '–', '—' -> '-';
         case '’', '‘' -> '\'';
         default -> ch < 256 ? ch : '?';
      };
   }

   /** Breite in Bildpixeln bei Skalierung {@code scale} (Pixel je Schriftpixel). */
   public int width(String s, int scale) {
      int w = 0;

      for (int i = 0; i < s.length(); i++) {
         w += (this.widths[code(s.charAt(i))] + 1) * scale;
      }

      return Math.max(0, w - scale);
   }

   /** Text so kürzen, dass er in {@code max} Pixel passt (mit „..“). */
   public String fit(String s, int scale, int max) {
      if (this.width(s, scale) <= max) {
         return s;
      }

      String t = s;

      while (t.length() > 1 && this.width(t + "..", scale) > max) {
         t = t.substring(0, t.length() - 1);
      }

      return t + "..";
   }

   public void draw(Tex dst, String s, int x, int y, int scale, int argb, boolean shadow) {
      if (shadow) {
         this.draw(dst, s, x + scale, y + scale, scale, Tex.mix(argb, 0xFF000000, 0.75F) | 0xFF000000, false);
      }

      if (this.glyphs == null) {
         return;
      }

      int cx = x;

      for (int i = 0; i < s.length(); i++) {
         int c = code(s.charAt(i));
         int gx = c % 16 * this.cell;
         int gy = c / 16 * this.cell;
         int cw = this.widths[c];

         for (int py = 0; py < this.cell; py++) {
            for (int px = 0; px < cw; px++) {
               if ((this.glyphs.get(gx + px, gy + py) >>> 24) > 100) {
                  for (int sy = 0; sy < scale; sy++) {
                     for (int sx = 0; sx < scale; sx++) {
                        dst.set(cx + px * scale * 8 / this.cell + sx, y + py * scale * 8 / this.cell + sy, argb);
                     }
                  }
               }
            }
         }

         cx += (cw * 8 / this.cell + 1) * scale;
      }
   }
}
