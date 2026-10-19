package com.lennart.nebula.cosmetic;

/**
 * Geteilter Wert einer Kosmetik: Stil, Farbton (Grad), Größe, eine freie Zusatzangabe, eine kleine Verschiebung
 * (in Pixeln, 1/16 Block) und ob die Kosmetik über der zweiten Skin-Ebene sitzt.
 */
/** color = eigene Farbe (0xFFRRGGBB) statt Farbton, 0 = keine. */
public record CosmeticValue(String style, float hue, float size, String extra, float ox, float oy, float oz, boolean outer, int color) {
   /** Kosmetik bleibt am Körper: kleine Verschiebungen und Größen, auch bei Werten von anderen Spielern. */
   public static final float MAX_OFFSET = 1.25F;
   public static final float MIN_SIZE = 0.8F;
   public static final float MAX_SIZE = 1.3F;

   public static CosmeticValue of(String style, float hue, float size, String extra) {
      return new CosmeticValue(style, hue, size, extra == null ? "" : extra, 0.0F, 0.0F, 0.0F, false, 0);
   }

   public CosmeticValue withPlacement(float ox, float oy, float oz, boolean outer) {
      return new CosmeticValue(this.style, this.hue, this.size, this.extra, ox, oy, oz, outer, this.color);
   }

   public CosmeticValue withColor(int color) {
      return new CosmeticValue(this.style, this.hue, this.size, this.extra, this.ox, this.oy, this.oz, this.outer, color == 0 ? 0 : color | 0xFF000000);
   }

   public boolean shifted() {
      return this.ox != 0.0F || this.oy != 0.0F || this.oz != 0.0F;
   }

   public String encode() {
      String s = this.style + "|" + Math.round(this.hue) + "|" + Math.round(this.size * 100.0F) + "|" + this.extra;
      if (this.shifted() || this.outer || this.color != 0) {
         // 5. Feld: Verschiebung in Viertel-Pixeln und Skin-Ebene (ältere Versionen ignorieren es)
         s = s + "|" + Math.round(this.ox * 4.0F) + "," + Math.round(this.oy * 4.0F) + "," + Math.round(this.oz * 4.0F) + "," + (this.outer ? 1 : 0) + (this.color != 0 ? "," + String.format("%06X", this.color & 16777215) : "");
      }

      return s.length() > 64 ? s.substring(0, 64) : s;
   }

   public static CosmeticValue decode(String text) {
      if (text != null && !text.isEmpty()) {
         String[] p = text.split("\\|", -1);
         float hue = 0.0F;
         float size = 1.0F;
         float ox = 0.0F;
         float oy = 0.0F;
         float oz = 0.0F;
         boolean outer = false;
         int color = 0;

         try {
            if (p.length > 1) {
               hue = Math.max(-180.0F, Math.min(180.0F, Float.parseFloat(p[1])));
            }

            if (p.length > 2) {
               size = Math.max(MIN_SIZE, Math.min(MAX_SIZE, Float.parseFloat(p[2]) / 100.0F));
            }

            if (p.length > 4) {
               String[] q = p[4].split(",");
               if (q.length >= 3) {
                  ox = offset(q[0]);
                  oy = offset(q[1]);
                  oz = offset(q[2]);
               }

               outer = q.length >= 4 && q[3].equals("1");
               if (q.length >= 5 && q[4].matches("[0-9A-Fa-f]{6}")) {
                  color = 0xFF000000 | Integer.parseInt(q[4], 16);
               }
            }
         } catch (NumberFormatException var5) {
         }

         return new CosmeticValue(p[0], hue, size, p.length > 3 ? p[3] : "", ox, oy, oz, outer, color);
      } else {
         return null;
      }
   }

   private static float offset(String s) {
      return Math.max(-MAX_OFFSET, Math.min(MAX_OFFSET, Integer.parseInt(s.trim()) / 4.0F));
   }

   /** Farbe mit diesem Farbton verschieben bzw. in die eigene Farbe umfärben. */
   public int tint(int argb) {
      if (this.color != 0) {
         return recolor(argb, this.color);
      }

      return this.hue == 0.0F ? argb : com.lennart.nebula.wings.WingsModel.shiftHue(argb, this.hue);
   }

   /**
    * Umfärben: farbige Teile bekommen die Zielfarbe, Helligkeit und Schattierung bleiben; Schwarz, Weiß und Grau
    * (Augen, Konturen, Glanzpunkte) bleiben fast unverändert.
    */
   public static int recolor(int argb, int target) {
      int r = argb >> 16 & 0xFF;
      int g = argb >> 8 & 0xFF;
      int b = argb & 0xFF;
      float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
      float lum = (0.299F * r + 0.587F * g + 0.114F * b) / 255.0F;
      int tr = target >> 16 & 0xFF;
      int tg = target >> 8 & 0xFF;
      int tb = target & 0xFF;
      float tLum = (0.299F * tr + 0.587F * tg + 0.114F * tb) / 255.0F;
      // Helligkeit des Originals auf die Zielfarbe übertragen (dunkle Zielfarben behalten etwas Zeichnung)
      float k = 0.35F + 0.9F * lum;
      float lift = 0.28F * lum * (1.0F - tLum);
      float w = Math.min(1.0F, hsb[1] * 2.2F);
      int nr = mixc(r, Math.round(255.0F * clamp(tr / 255.0F * k + lift)), w);
      int ng = mixc(g, Math.round(255.0F * clamp(tg / 255.0F * k + lift)), w);
      int nb = mixc(b, Math.round(255.0F * clamp(tb / 255.0F * k + lift)), w);
      return argb & 0xFF000000 | nr << 16 | ng << 8 | nb;
   }

   private static float clamp(float v) {
      return Math.max(0.0F, Math.min(1.0F, v));
   }

   private static int mixc(int a, int b, float t) {
      return Math.round(a + (b - a) * t);
   }
}
