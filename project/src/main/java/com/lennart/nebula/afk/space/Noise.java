package com.lennart.nebula.afk.space;

/** Deterministisches Wert-Rauschen (2D/3D) für Planeten, Nebel und Materialvariation. */
public final class Noise {
   private final int seed;

   public Noise(long seed) {
      this.seed = (int)(seed ^ seed >>> 32);
   }

   public static int hash(int x, int y, int z, int seed) {
      int h = seed ^ x * 374761393 ^ y * 668265263 ^ z * 2147483587;
      h = (h ^ h >>> 13) * 1274126177;
      return h ^ h >>> 16;
   }

   public float rand(int x, int y, int z) {
      return (hash(x, y, z, this.seed) & 16777215) / 1.6777216E7F;
   }

   private static float smooth(float t) {
      return t * t * (3.0F - 2.0F * t);
   }

   public float noise(float x, float y, float z) {
      int x0 = (int)Math.floor(x);
      int y0 = (int)Math.floor(y);
      int z0 = (int)Math.floor(z);
      float fx = smooth(x - x0);
      float fy = smooth(y - y0);
      float fz = smooth(z - z0);
      float c000 = this.rand(x0, y0, z0);
      float c100 = this.rand(x0 + 1, y0, z0);
      float c010 = this.rand(x0, y0 + 1, z0);
      float c110 = this.rand(x0 + 1, y0 + 1, z0);
      float c001 = this.rand(x0, y0, z0 + 1);
      float c101 = this.rand(x0 + 1, y0, z0 + 1);
      float c011 = this.rand(x0, y0 + 1, z0 + 1);
      float c111 = this.rand(x0 + 1, y0 + 1, z0 + 1);
      float a = c000 + (c100 - c000) * fx;
      float b = c010 + (c110 - c010) * fx;
      float c = c001 + (c101 - c001) * fx;
      float d = c011 + (c111 - c011) * fx;
      float e = a + (b - a) * fy;
      float f = c + (d - c) * fy;
      return e + (f - e) * fz;
   }

   public float fbm(float x, float y, float z, int octaves) {
      float sum = 0.0F;
      float amp = 0.5F;
      float norm = 0.0F;

      for (int i = 0; i < octaves; i++) {
         sum += this.noise(x, y, z) * amp;
         norm += amp;
         amp *= 0.5F;
         x *= 2.03F;
         y *= 2.03F;
         z *= 2.03F;
      }

      return sum / norm;
   }

   /** Grat-Rauschen: hohe Werte entlang dünner Linien (Lava-Adern, Risse, Straßen). */
   public float ridge(float x, float y, float z, int octaves) {
      float sum = 0.0F;
      float amp = 0.5F;
      float norm = 0.0F;

      for (int i = 0; i < octaves; i++) {
         float n = 1.0F - Math.abs(this.noise(x, y, z) * 2.0F - 1.0F);
         sum += n * n * amp;
         norm += amp;
         amp *= 0.5F;
         x *= 2.1F;
         y *= 2.1F;
         z *= 2.1F;
      }

      return sum / norm;
   }
}
