package com.lennart.nebula.net;

import java.nio.charset.StandardCharsets;

/**
 * Adresse des offiziellen Meowl Servers. Sie steht nicht im Klartext im Code, sondern wird erst beim Start
 * zusammengesetzt – wer die Jar nur durchsucht oder entpackt, findet sie so nicht direkt.
 */
public final class ServerAddress {
   private static final byte[] KEY = {(byte)0x5A, (byte)0xC3, (byte)0x17, (byte)0x9E, (byte)0x2B, (byte)0x71, (byte)0xE4, (byte)0x08};
   private static final byte[] DATA = {
      (byte)0x32, (byte)0x92, (byte)0x29, (byte)0x81, (byte)0xCC, (byte)0xF2, (byte)0x15, (byte)0x24, (byte)0x4A, (byte)0xB7, (byte)0x48, (byte)0x3C,
      (byte)0xAF, (byte)0xBD, (byte)0xD0, (byte)0x1A, (byte)0x27, (byte)0x85, (byte)0xB5, (byte)0x0F, (byte)0xBC, (byte)0x0B, (byte)0xA6, (byte)0x32,
      (byte)0x52, (byte)0x70, (byte)0xBC, (byte)0x16
   };
   private static String cached;

   private ServerAddress() {
   }

   public static String official() {
      String s = cached;
      if (s == null) {
         byte[] out = new byte[DATA.length];

         for (int i = 0; i < DATA.length; i++) {
            out[i] = (byte)(DATA[i] ^ KEY[i % KEY.length] ^ (i * 37 & 0xFF));
         }

         s = new String(out, StandardCharsets.US_ASCII);
         cached = s;
      }

      return s;
   }
}
