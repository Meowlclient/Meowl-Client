package com.lennart.meowlserver;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

final class MeowlLogo {
   private static final String[] ROWS = new String[]{
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
   private static byte[] png;

   private MeowlLogo() {
   }

   private static int color(char c) {
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

   static synchronized byte[] png() throws IOException {
      if (png == null) {
         int scale = 32;
         int pad = 2 * scale;
         int size = 16 * scale + pad * 2;
         BufferedImage img = new BufferedImage(size, size, 2);

         for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
               img.setRGB(x, y, -15067856);
            }
         }

         for (int row = 0; row < 16; row++) {
            for (int col = 0; col < 16; col++) {
               int c = color(ROWS[row].charAt(col));
               if (c != 0) {
                  for (int yy = 0; yy < scale; yy++) {
                     for (int xx = 0; xx < scale; xx++) {
                        img.setRGB(pad + col * scale + xx, pad + row * scale + yy, c);
                     }
                  }
               }
            }
         }

         ByteArrayOutputStream out = new ByteArrayOutputStream();
         ImageIO.write(img, "png", out);
         png = out.toByteArray();
      }

      return png;
   }
}
