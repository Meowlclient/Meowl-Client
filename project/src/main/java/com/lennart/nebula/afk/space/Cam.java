package com.lennart.nebula.afk.space;

/** Lochkamera der Brücke; Innenraum (vorberechnet) und Weltraum (live) nutzen dieselbe Projektion. */
public final class Cam {
   public final int vw;
   public final int vh;
   public final float x;
   public final float y;
   public final float z;
   public final float focal;
   private final float fy;
   private final float fz;
   private final float uy;
   private final float uz;

   public Cam(int vw, int vh, float x, float y, float z, float pitchDeg, float fovYDeg) {
      this.vw = vw;
      this.vh = vh;
      this.x = x;
      this.y = y;
      this.z = z;
      double p = Math.toRadians(pitchDeg);
      this.fy = (float)Math.sin(p);
      this.fz = (float)Math.cos(p);
      this.uy = (float)Math.cos(p);
      this.uz = (float)-Math.sin(p);
      this.focal = (float)(vh / 2.0 / Math.tan(Math.toRadians(fovYDeg) / 2.0));
   }

   /** Blickrichtung für einen Pixel (nicht normiert). */
   public void ray(float px, float py, float[] out) {
      float a = (px - this.vw / 2.0F) / this.focal;
      float b = (py - this.vh / 2.0F) / this.focal;
      out[0] = a;
      out[1] = this.fy - b * this.uy;
      out[2] = this.fz - b * this.uz;
      float len = (float)Math.sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2]);
      out[0] /= len;
      out[1] /= len;
      out[2] /= len;
   }

   /** Weltpunkt → {x, y, Tiefe}; Tiefe <= 0 heißt hinter der Kamera. */
   public boolean project(float wx, float wy, float wz, float[] out) {
      float rx = wx - this.x;
      float ry = wy - this.y;
      float rz = wz - this.z;
      float depth = ry * this.fy + rz * this.fz;
      if (depth <= 0.05F) {
         out[2] = depth;
         return false;
      } else {
         float up = ry * this.uy + rz * this.uz;
         out[0] = this.vw / 2.0F + this.focal * rx / depth;
         out[1] = this.vh / 2.0F - this.focal * up / depth;
         out[2] = depth;
         return true;
      }
   }

   /** Pixel pro Block in einer bestimmten Tiefe. */
   public float scale(float depth) {
      return this.focal / depth;
   }
}
