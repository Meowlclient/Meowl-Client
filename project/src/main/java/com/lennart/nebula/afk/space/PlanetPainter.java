package com.lennart.nebula.afk.space;

/**
 * Malt den Hintergrund hinter den Fenstern: Sternenhimmel mit Nebel und einen riesigen Planeten mit
 * Atmosphärenrand. Halbe Auflösung (2x2-Pixel) für einen Pixel-Look. Leuchtende Teile (Lava, Stadtlichter,
 * Eisrisse) landen zusätzlich in {@link #glow}, damit sie live pulsieren können.
 */
public final class PlanetPainter {
   public static final int SCALE = 2;
   public static final int W = BridgeTracer.VW / SCALE;
   public static final int H = BridgeTracer.VH / SCALE;
   public final Tex image = new Tex(W, H);
   public final Tex glow = new Tex(W, H);
   private final Planet planet;
   private final Noise noise;
   // Planet: riesige Kugel unter und vor der Brücke, Horizont links sichtbar
   private static final double CX = -5414.8;
   private static final double CY = -18046.0;
   private static final double CZ = 8118.8;
   private static final double R = 20000.0;

   public enum Planet {
      LAVA("Lavawelt", new float[]{1.0F, 0.66F, 0.55F}),
      CITY("Stadtwelt", new float[]{1.0F, 0.78F, 0.55F}),
      ICE("Eiswelt", new float[]{0.72F, 0.86F, 1.05F});

      public final String label;
      /** Farbe, mit der der Planet die Brücke beleuchtet. */
      public final float[] light;

      Planet(String label, float[] light) {
         this.label = label;
         this.light = light;
      }

      public static Planet byLabel(String label) {
         for (Planet p : values()) {
            if (p.label.equals(label)) {
               return p;
            }
         }

         return LAVA;
      }
   }

   public PlanetPainter(Planet planet, long seed) {
      this.planet = planet;
      this.noise = new Noise(seed);
   }

   public void render() {
      Cam cam = BridgeTracer.CAM;
      float[] d = new float[3];
      double lx = -0.55;
      double ly = 0.62;
      double lz = 0.56;
      double ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
      lx /= ll;
      ly /= ll;
      lz /= ll;

      for (int y = 0; y < H; y++) {
         for (int x = 0; x < W; x++) {
            cam.ray((x + 0.5F) * SCALE, (y + 0.5F) * SCALE, d);
            double ox = cam.x - CX;
            double oy = cam.y - CY;
            double oz = cam.z - CZ;
            double b = ox * d[0] + oy * d[1] + oz * d[2];
            double c = ox * ox + oy * oy + oz * oz - R * R;
            double disc = b * b - c;
            int color;
            int glowColor = 0;
            // Abstand des Strahls zur Kugel (für den Atmosphärenschein außerhalb)
            double tc = -b;
            double px0 = ox + d[0] * tc;
            double py0 = oy + d[1] * tc;
            double pz0 = oz + d[2] * tc;
            double miss = Math.sqrt(px0 * px0 + py0 * py0 + pz0 * pz0) - R;
            if (disc > 0.0) {
               double t = -b - Math.sqrt(disc);
               double nx = (ox + d[0] * t) / R;
               double ny = (oy + d[1] * t) / R;
               double nz = (oz + d[2] * t) / R;
               double sun = Math.max(0.0, nx * lx + ny * ly + nz * lz);
               double view = Math.max(0.0, -(nx * d[0] + ny * d[1] + nz * d[2]));
               int[] surf = this.surface((float)nx, (float)ny, (float)nz);
               float light = (float)(0.22 + 0.9 * sun);
               int base = surf[0];
               float r = (base >> 16 & 0xFF) * light;
               float g = (base >> 8 & 0xFF) * light;
               float bl = (base & 0xFF) * light;
               int em = surf[1];
               float ea = (em >>> 24) / 255.0F;
               r += (em >> 16 & 0xFF) * ea * 0.65F;
               g += (em >> 8 & 0xFF) * ea * 0.65F;
               bl += (em & 0xFF) * ea * 0.65F;
               // Atmosphäre am Rand
               float rim = (float)Math.pow(1.0 - view, 9.0);
               int atmo = this.atmosphere();
               r += ((atmo >> 16 & 0xFF) - r) * rim * 0.8F;
               g += ((atmo >> 8 & 0xFF) - g) * rim * 0.8F;
               bl += ((atmo & 0xFF) - bl) * rim * 0.8F;
               color = Tex.argb(255, r, g, bl);
               glowColor = em;
            } else {
               color = this.space(x, y, d);
               // dünner Schein über dem Horizont
               if (miss < 120.0) {
                  float a = (float)Math.pow(1.0 - miss / 120.0, 2.2) * 0.85F;
                  color = Tex.mix(color, this.atmosphere(), a);
               }
            }

            this.image.px[y * W + x] = color;
            this.glow.px[y * W + x] = glowColor;
         }
      }
   }

   private int atmosphere() {
      switch (this.planet) {
         case CITY:
            return -6311681;
         case ICE:
            return -6820353;
         default:
            return -10446094;
      }
   }

   /** {Grundfarbe, Leuchten (mit Alpha)} eines Oberflächenpunkts. */
   private int[] surface(float x, float y, float z) {
      Noise n = this.noise;
      float land = n.fbm(x * 30.0F + 3.0F, y * 30.0F, z * 30.0F, 5);
      float cloud = n.fbm(x * 40.0F + 40.0F, y * 60.0F, z * 40.0F, 5);
      int base;
      int emit = 0;
      switch (this.planet) {
         case CITY: {
            base = Tex.mix(-14671836, -11841458, land);
            // Stadtlichter: Raster aus Straßen + Ballungen
            float dense = n.fbm(x * 25.0F, y * 25.0F + 9.0F, z * 25.0F, 3);
            float roads = n.ridge(x * 140.0F, y * 140.0F, z * 140.0F, 2);
            float spot = n.rand((int)(x * 4000.0F), (int)(y * 4000.0F), (int)(z * 4000.0F));
            float lights = dense > 0.52F ? (roads > 0.86F ? 0.85F : (spot > 0.94F ? 0.6F : 0.0F)) : (spot > 0.992F ? 0.45F : 0.0F);
            if (lights > 0.0F) {
               emit = Tex.clamp255(lights * 255.0F) << 24 | (spot > 0.97F ? 16772300 : 16754752);
            }

            if (cloud > 0.62F) {
               base = Tex.mix(base, -5658199, (cloud - 0.62F) * 3.0F);
            }
            break;
         }
         case ICE: {
            base = Tex.mix(0xFF7F97AE, 0xFFD2DEE8, land);
            float crack = n.ridge(x * 45.0F, y * 45.0F, z * 45.0F, 3);
            if (crack > 0.8F) {
               emit = Tex.clamp255((crack - 0.8F) * 5.0F * 200.0F) << 24 | 4772863;
               base = Tex.mix(base, -12347985, (crack - 0.8F) * 4.0F);
            }

            if (cloud > 0.58F) {
               base = Tex.mix(base, -1, (cloud - 0.58F) * 2.5F);
            }
            break;
         }
         default: {
            base = Tex.mix(-12172736, -7832236, land);
            float vein = n.ridge(x * 55.0F + 11.0F, y * 55.0F, z * 55.0F, 4);
            if (vein > 0.7F) {
               float v = Math.min(1.0F, (vein - 0.7F) / 0.16F);
               emit = Tex.clamp255(v * 255.0F) << 24 | (v > 0.8F ? 16764496 : (v > 0.45F ? 16737826 : 14168336));
               base = Tex.mix(base, -12709632, v);
            }

            if (cloud > 0.6F) {
               float a = Math.min(0.9F, (cloud - 0.6F) * 3.0F);
               base = Tex.mix(base, -2302756, a);
               emit = Tex.mix(emit, 0, a * 0.7F);
            }
         }
      }

      return new int[]{base, emit};
   }

   private int space(int x, int y, float[] d) {
      Noise n = this.noise;
      // Richtung → stabile Koordinaten, damit Sterne beim Neuzeichnen gleich bleiben
      float neb = n.fbm(d[0] * 3.0F + 5.0F, d[1] * 3.0F, d[2] * 3.0F, 4);
      float neb2 = n.fbm(d[0] * 6.0F, d[1] * 6.0F + 7.0F, d[2] * 6.0F, 3);
      float r = 4.0F + Math.max(0.0F, neb - 0.5F) * 90.0F;
      float g = 5.0F + Math.max(0.0F, neb2 - 0.55F) * 50.0F;
      float b = 12.0F + Math.max(0.0F, neb - 0.45F) * 110.0F;
      float s = n.rand(x, y, 77);
      if (s > 0.993F) {
         float br = s > 0.9985F ? 255.0F : 120.0F + (s - 0.993F) * 18000.0F;
         float tint = n.rand(x, y, 78);
         r = br * (tint > 0.8F ? 1.0F : 0.85F);
         g = br * 0.9F;
         b = br * (tint < 0.2F ? 0.75F : 1.0F);
      }

      return Tex.argb(255, r, g, b);
   }
}
