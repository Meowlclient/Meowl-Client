package com.lennart.nebula.gui;

public final class MeowlSprite {
   public static final int SIZE = 16;
   public static final String[] ROWS = new String[]{
      "..K..........K..",
      ".KRK........KRK.",
      ".KRBK......KBRK.",
      ".KBBBKKKKKKBBBK.",
      "KBBBBBBBBBBBBBBK",
      "KBWWWWBBBBWWWWBK",
      "KWWPPWWBBWWPPWWK",
      "KWWPPWWBBWWPPWWK",
      "KBWWWWBYYBWWWWBK",
      "KBBBBBBYYBBBBBBK",
      "KBBBLLLLLLLLBBBK",
      ".KBLLLLLLLLLLBK.",
      ".KBLLLLLLLLLLBK.",
      "..KBLLLLLLLLBK..",
      "...KKKKKKKKKK...",
      "....FF....FF...."
   };

   private MeowlSprite() {
   }

   /** Zeichnet die Eule in Einheitsgröße (1 Pixel = 1 Einheit) ab (x, y); gleiche Farben in einer Reihe als ein Balken. */
   public static void draw(net.minecraft.client.gui.GuiGraphicsExtractor ctx, int x, int y) {
      for (int row = 0; row < SIZE; row++) {
         String line = ROWS[row];
         int col = 0;
         while (col < SIZE) {
            int c = color(line.charAt(col));
            int end = col + 1;
            while (end < SIZE && color(line.charAt(end)) == c) {
               end++;
            }

            if (c != 0) {
               ctx.fill(x + col, y + row, x + end, y + row + 1, c);
            }

            col = end;
         }
      }
   }
   public static int color(char c) {
      return switch (c) {
         case 'B' -> -7703880;
         default -> 0;
         case 'F' -> -1537748;
         case 'K' -> -14016707;
         case 'L' -> -1845262;
         case 'P' -> -15396833;
         case 'R' -> -877640;
         case 'W' -> -1;
         case 'Y' -> -673470;
      };
   }
}
