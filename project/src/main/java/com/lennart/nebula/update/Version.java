package com.lennart.nebula.update;

import java.util.Arrays;

public record Version(int[] parts, String suffix, String raw) implements Comparable<Version> {
   public static Version parse(String text) {
      if (text == null) {
         return null;
      } else {
         String t = text.trim();
         if (t.startsWith("v") || t.startsWith("V")) {
            t = t.substring(1);
         }

         String core = t;
         String suffix = "";
         int dash = t.indexOf(45);
         if (dash >= 0) {
            core = t.substring(0, dash);
            suffix = t.substring(dash + 1);
         }

         int plus = core.indexOf(43);
         if (plus >= 0) {
            core = core.substring(0, plus);
         }

         if (!suffix.matches("[A-Za-z0-9.]{0,32}")) {
            return null;
         } else {
            String[] pieces = core.split("\\.");
            if (pieces.length != 0 && pieces.length <= 4) {
               int[] parts = new int[pieces.length];

               try {
                  for (int i = 0; i < pieces.length; i++) {
                     parts[i] = Integer.parseInt(pieces[i]);
                     if (parts[i] < 0) {
                        return null;
                     }
                  }
               } catch (NumberFormatException var10) {
                  return null;
               }

               StringBuilder canonical = new StringBuilder();

               for (int ix = 0; ix < parts.length; ix++) {
                  canonical.append(ix > 0 ? "." : "").append(parts[ix]);
               }

               if (!suffix.isEmpty()) {
                  canonical.append('-').append(suffix);
               }

               return new Version(parts, suffix, canonical.toString());
            } else {
               return null;
            }
         }
      }
   }

   public int compareTo(Version o) {
      int n = Math.max(this.parts.length, o.parts.length);

      for (int i = 0; i < n; i++) {
         int a = i < this.parts.length ? this.parts[i] : 0;
         int b = i < o.parts.length ? o.parts[i] : 0;
         if (a != b) {
            return Integer.compare(a, b);
         }
      }

      if (this.suffix.isEmpty() != o.suffix.isEmpty()) {
         return this.suffix.isEmpty() ? 1 : -1;
      } else {
         return this.suffix.compareTo(o.suffix);
      }
   }

   public boolean isNewerThan(Version o) {
      return o == null || this.compareTo(o) > 0;
   }

   @Override
   public boolean equals(Object o) {
      return o instanceof Version v && this.compareTo(v) == 0;
   }

   @Override
   public int hashCode() {
      return Arrays.hashCode(this.parts) * 31 + this.suffix.hashCode();
   }

   @Override
   public String toString() {
      return this.raw;
   }
}
