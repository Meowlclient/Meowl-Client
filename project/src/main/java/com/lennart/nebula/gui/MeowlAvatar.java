package com.lennart.nebula.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Der Meowl-Avatar (die Eule aus dem Logo) als lebendige Pixel-Figur: wippt, blinzelt ab und zu und schaut mit den
 * Pupillen dorthin, wo die Maus ist.
 */
public final class MeowlAvatar {
   private static final String[] MAP = {
      "..a..........a..",
      ".aba........aba.",
      ".abca......acba.",
      ".acccaaaaaaccca.",
      "acccccccccccccca",
      "acddddccccddddca",
      "addeeddccddeedda",
      "addeeddccddeedda",
      "acddddcffcddddca",
      "accccccffcccccca",
      "acccggggggggccca",
      ".acggggggggggca.",
      ".acggggggggggca.",
      "..acggggggggca..",
      "...aaaaaaaaaa...",
      "....hh....hh...."
   };

   private MeowlAvatar() {
   }

   private static int color(char c) {
      return switch (c) {
         case 'a' -> 0xFF2A1F3D;
         case 'b' -> 0xFFF29BB8;
         case 'c' -> 0xFF8A72B8;
         case 'd' -> 0xFFFFFFFF;
         case 'e' -> 0xFF15101F;
         case 'f' -> 0xFFF5B942;
         case 'g' -> 0xFFE3D7F2;
         case 'h' -> 0xFFE8892C;
         default -> 0;
      };
   }

   /** Farbe einer Zelle: Pupillen werden getrennt gezeichnet (hier Augenweiß), beim Blinzeln sind die Augen zu. */
   private static char cell(int row, int col, boolean blink) {
      char c = MAP[row].charAt(col);
      if (c == 'e') {
         c = 'd';
      }

      if (blink && row >= 5 && row <= 8 && c == 'd') {
         c = row == 7 ? 'a' : 'c';
      }

      return c;
   }
   /**
    * @param x linke obere Ecke @param px Pixelgröße @param lookX/lookY Blickrichtung -1..1 @param t Zeit in Sekunden
    */
   public static void draw(GuiGraphics ctx, int x, int y, int px, float lookX, float lookY, float t) {
      boolean blink = t % 4.3F < 0.14F || t % 11.0F > 10.82F;
      int ox = Math.round(Math.max(-1.0F, Math.min(1.0F, lookX)));
      int oy = Math.round(Math.max(-1.0F, Math.min(1.0F, lookY)) * 0.6F);
      // Ohren zucken ab und zu
      int ear = t % 6.0F < 0.18F ? -1 : 0;

      for (int row = 0; row < 16; row++) {
         int dy = row <= 2 ? ear : 0;
         // gleiche Farben einer Reihe als ein Balken zeichnen (deutlich weniger Zeichenaufrufe)
         int col = 0;
         while (col < 16) {
            int c = color(cell(row, col, blink));
            int end = col + 1;
            while (end < 16 && color(cell(row, end, blink)) == c) {
               end++;
            }

            if (c != 0) {
               ctx.fill(x + col * px, y + (row + dy) * px, x + end * px, y + (row + dy + 1) * px, c);
            }

            col = end;
         }
      }
      if (!blink) {
         // Pupillen (2x2) in beiden Augen, der Maus folgend
         for (int eye = 0; eye < 2; eye++) {
            int ex = (eye == 0 ? 3 : 11) + ox;
            int ey = 6 + oy;
            ctx.fill(x + ex * px, y + ey * px, x + (ex + 2) * px, y + (ey + 2) * px, color('e'));
            // kleiner Glanzpunkt
            ctx.fill(x + ex * px, y + ey * px, x + ex * px + Math.max(1, px / 2), y + ey * px + Math.max(1, px / 2), 0xC0FFFFFF);
         }
      }
   }
}
