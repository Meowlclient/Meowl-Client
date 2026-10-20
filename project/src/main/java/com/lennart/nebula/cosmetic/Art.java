package com.lennart.nebula.cosmetic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

/**
 * Gemeinsame Bausteine der Kosmetik-Kollektion im Low-Poly-Stil (Pixel-Einheiten, y nach unten wie {@link Shapes}).
 * Jede Fläche wird automatisch nach außen gedreht (die Geometrie wird mit Culling gezeichnet) und bekommt weiches
 * Richtungslicht von oben vorne links plus eine winzige Farbvariation je Facette – das gibt den handgemachten
 * Low-Poly-Look. Dazu facettierte Körper (abgeschrägte Quader, Kegelstümpfe, Ringe, Edelsteine, Kugeln, Äste) und
 * Sakura-Details (Blüten, Blütenblätter).
 */
public final class Art {
   // ================================================================== Palette der Kollektion

   /** Kirschblütenrosa (Hauptfarbe der Blüten). */
   public static final int SAKURA = 0xFFF4A6BE;
   /** Helles Blütenrosa (Blattspitzen, Glanz). */
   public static final int BLOSSOM = 0xFFFAD0DC;
   /** Tiefes Rosé (Blütenmitte, Akzente). */
   public static final int ROSE = 0xFFDA7B98;
   public static final int IVORY = 0xFFF3EBDD;
   public static final int WHITE = 0xFFFBF8F3;
   public static final int STONE = 0xFFB7B0B3;
   public static final int SILVER = 0xFFCACFD7;
   public static final int CHARCOAL = 0xFF3A363D;
   public static final int INK = 0xFF25222A;
   /** Gedämpftes Gold (Beschläge, Ornamente). */
   public static final int GOLD = 0xFFD8B46A;
   public static final int LEAF = 0xFF8CB687;
   public static final int WOOD = 0xFF6E534A;

   /** Lichtrichtung (zum Licht hin) im Kosmetik-Raum: oben (-y), vorne (-z), leicht links. */
   private static final float LX = 0.30F;
   private static final float LY = -0.86F;
   private static final float LZ = -0.41F;

   private Art() {
   }

   // ================================================================== Farbe und Licht

   public static int mix(int a, int b, float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      int ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
      int br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
      return a & 0xFF000000 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
   }

   /** Helligkeit skalieren (k darf > 1 sein, gekappt bei Weiß). */
   public static int bright(int argb, float k) {
      int r = Math.min(255, Math.round((argb >> 16 & 0xFF) * k));
      int g = Math.min(255, Math.round((argb >> 8 & 0xFF) * k));
      int b = Math.min(255, Math.round((argb & 0xFF) * k));
      return argb & 0xFF000000 | r << 16 | g << 8 | b;
   }

   /** Licht für eine Fläche mit Normale n (Länge egal): weiches Umgebungslicht + gerichtetes Licht. */
   static int light(int argb, float nx, float ny, float nz, float b, float jitter) {
      float len = (float)Math.sqrt(nx * nx + ny * ny + nz * nz);
      if (len < 1.0E-6F) {
         return bright(argb | 0xFF000000, b);
      }

      float d = (nx * LX + ny * LY + nz * LZ) / len;
      float k = (0.66F + 0.42F * Math.max(0.0F, d) - 0.1F * Math.max(0.0F, -d)) * b * (1.0F + jitter);
      return bright(argb | 0xFF000000, k);
   }

   private static float hash(float x, float y, float z) {
      float h = (float)Math.sin(x * 12.9898F + y * 78.233F + z * 37.719F) * 43758.547F;
      return h - (float)Math.floor(h);
   }

   // ================================================================== Flächen

   private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, int c) {
      vc.addVertex(m, x, y, z).setColor(c);
   }

   /**
    * Vieleck (3 oder 4 Punkte, x,y,z hintereinander), das vom Innenpunkt (ix, iy, iz) weg zeigt. Dreiecke werden als
    * Viereck mit doppeltem letzten Punkt gezeichnet.
    */
   public static void face(PoseStack ms, VertexConsumer vc, int argb, float b, float ix, float iy, float iz, float... p) {
      int n = p.length / 3;
      float ax = p[0], ay = p[1], az = p[2];
      float bx = p[3], by = p[4], bz = p[5];
      float cx = p[6], cy = p[7], cz = p[8];
      float ux = bx - ax, uy = by - ay, uz = bz - az;
      float vx = cx - ax, vy = cy - ay, vz = cz - az;
      float nx = uy * vz - uz * vy;
      float ny = uz * vx - ux * vz;
      float nz = ux * vy - uy * vx;
      float mx = 0.0F, my = 0.0F, mz = 0.0F;
      for (int i = 0; i < n; i++) {
         mx += p[i * 3];
         my += p[i * 3 + 1];
         mz += p[i * 3 + 2];
      }

      mx /= n;
      my /= n;
      mz /= n;
      boolean flip = nx * (mx - ix) + ny * (my - iy) + nz * (mz - iz) < 0.0F;
      if (flip) {
         nx = -nx;
         ny = -ny;
         nz = -nz;
      }

      int c = light(argb, nx, ny, nz, b, (hash(mx, my, mz) - 0.5F) * 0.06F);
      Matrix4f m = ms.last().pose();
      int[] order = n == 3 ? (flip ? new int[]{0, 2, 1, 1} : new int[]{0, 1, 2, 2}) : (flip ? new int[]{0, 3, 2, 1} : new int[]{0, 1, 2, 3});
      for (int i : order) {
         vertex(vc, m, p[i * 3], p[i * 3 + 1], p[i * 3 + 2], c);
      }
   }

   /** Dünne Fläche, von beiden Seiten sichtbar (Blütenblätter, Bänder, Federn). */
   public static void sheet(PoseStack ms, VertexConsumer vc, int argb, float b, float... p) {
      int n = p.length / 3;
      float ux = p[3] - p[0], uy = p[4] - p[1], uz = p[5] - p[2];
      float vx = p[6] - p[0], vy = p[7] - p[1], vz = p[8] - p[2];
      float nx = uy * vz - uz * vy;
      float ny = uz * vx - ux * vz;
      float nz = ux * vy - uy * vx;
      float mx = 0.0F, my = 0.0F, mz = 0.0F;
      for (int i = 0; i < n; i++) {
         mx += p[i * 3];
         my += p[i * 3 + 1];
         mz += p[i * 3 + 2];
      }

      mx /= n;
      my /= n;
      mz /= n;
      float j = (hash(mx, my, mz) - 0.5F) * 0.06F;
      Matrix4f m = ms.last().pose();
      int front = light(argb, nx, ny, nz, b, j);
      int back = light(argb, -nx, -ny, -nz, b, j);
      int[] fw = n == 3 ? new int[]{0, 1, 2, 2} : new int[]{0, 1, 2, 3};
      int[] bw = n == 3 ? new int[]{0, 2, 1, 1} : new int[]{0, 3, 2, 1};
      for (int i : fw) {
         vertex(vc, m, p[i * 3], p[i * 3 + 1], p[i * 3 + 2], front);
      }

      for (int i : bw) {
         vertex(vc, m, p[i * 3], p[i * 3 + 1], p[i * 3 + 2], back);
      }
   }

   // ================================================================== Körper

   /** Quader mit abgeschrägten Kanten (Fase {@code c}): 6 Flächen, 12 Kantenfasen, 8 Eckdreiecke. */
   public static void bevel(PoseStack m, VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2, float c, int argb, float b) {
      float ax = Math.min(x1, x2), bx = Math.max(x1, x2);
      float ay = Math.min(y1, y2), by = Math.max(y1, y2);
      float az = Math.min(z1, z2), bz = Math.max(z1, z2);
      c = Math.max(0.0F, Math.min(c, Math.min(bx - ax, Math.min(by - ay, bz - az)) * 0.45F));
      float ix = (ax + bx) / 2.0F, iy = (ay + by) / 2.0F, iz = (az + bz) / 2.0F;
      if (c < 0.01F) {
         box(m, vc, ax, ay, az, bx, by, bz, argb, b);
         return;
      }

      // Hauptflächen (eingerückt)
      face(m, vc, argb, b, ix, iy, iz, ax, ay + c, az + c, ax, by - c, az + c, ax, by - c, bz - c, ax, ay + c, bz - c);
      face(m, vc, argb, b, ix, iy, iz, bx, ay + c, az + c, bx, by - c, az + c, bx, by - c, bz - c, bx, ay + c, bz - c);
      face(m, vc, argb, b, ix, iy, iz, ax + c, ay, az + c, bx - c, ay, az + c, bx - c, ay, bz - c, ax + c, ay, bz - c);
      face(m, vc, argb, b, ix, iy, iz, ax + c, by, az + c, bx - c, by, az + c, bx - c, by, bz - c, ax + c, by, bz - c);
      face(m, vc, argb, b, ix, iy, iz, ax + c, ay + c, az, bx - c, ay + c, az, bx - c, by - c, az, ax + c, by - c, az);
      face(m, vc, argb, b, ix, iy, iz, ax + c, ay + c, bz, bx - c, ay + c, bz, bx - c, by - c, bz, ax + c, by - c, bz);
      float[] xs = {ax, bx};
      float[] ys = {ay, by};
      float[] zs = {az, bz};
      for (int i = 0; i < 2; i++) {
         float sx = i == 0 ? 1 : -1;
         for (int j = 0; j < 2; j++) {
            float sy = j == 0 ? 1 : -1;
            // Kante entlang z
            face(m, vc, argb, b, ix, iy, iz, xs[i], ys[j] + sy * c, az + c, xs[i] + sx * c, ys[j], az + c, xs[i] + sx * c, ys[j], bz - c, xs[i], ys[j] + sy * c, bz - c);
            for (int k = 0; k < 2; k++) {
               float sz = k == 0 ? 1 : -1;
               // Ecke
               face(m, vc, argb, b, ix, iy, iz, xs[i], ys[j] + sy * c, zs[k] + sz * c, xs[i] + sx * c, ys[j], zs[k] + sz * c, xs[i] + sx * c, ys[j] + sy * c, zs[k]);
            }
         }

         for (int k = 0; k < 2; k++) {
            float sz = k == 0 ? 1 : -1;
            // Kante entlang y
            face(m, vc, argb, b, ix, iy, iz, xs[i], ay + c, zs[k] + sz * c, xs[i] + sx * c, ay + c, zs[k], xs[i] + sx * c, by - c, zs[k], xs[i], by - c, zs[k] + sz * c);
         }
      }

      for (int j = 0; j < 2; j++) {
         float sy = j == 0 ? 1 : -1;
         for (int k = 0; k < 2; k++) {
            float sz = k == 0 ? 1 : -1;
            // Kante entlang x
            face(m, vc, argb, b, ix, iy, iz, ax + c, ys[j], zs[k] + sz * c, ax + c, ys[j] + sy * c, zs[k], bx - c, ys[j] + sy * c, zs[k], bx - c, ys[j], zs[k] + sz * c);
         }
      }
   }

   /** Einfacher Quader mit dem Licht der Kollektion. */
   public static void box(PoseStack m, VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2, int argb, float b) {
      float ax = Math.min(x1, x2), bx = Math.max(x1, x2);
      float ay = Math.min(y1, y2), by = Math.max(y1, y2);
      float az = Math.min(z1, z2), bz = Math.max(z1, z2);
      float ix = (ax + bx) / 2.0F, iy = (ay + by) / 2.0F, iz = (az + bz) / 2.0F;
      face(m, vc, argb, b, ix, iy, iz, ax, ay, az, ax, by, az, ax, by, bz, ax, ay, bz);
      face(m, vc, argb, b, ix, iy, iz, bx, ay, az, bx, by, az, bx, by, bz, bx, ay, bz);
      face(m, vc, argb, b, ix, iy, iz, ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz);
      face(m, vc, argb, b, ix, iy, iz, ax, by, az, bx, by, az, bx, by, bz, ax, by, bz);
      face(m, vc, argb, b, ix, iy, iz, ax, ay, az, bx, ay, az, bx, by, az, ax, by, az);
      face(m, vc, argb, b, ix, iy, iz, ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz);
   }

   /**
    * Facettierter Kegelstumpf / Zylinder / Kegel um die senkrechte Achse durch (cx, cz): Ring A auf Höhe yA mit Radien
    * (rxA, rzA), Ring B auf yB. {@code n} Seiten, gedreht um {@code rot} Grad. Deckel optional.
    */
   public static void frustum(
      PoseStack m, VertexConsumer vc, float cx, float cz, float yA, float yB, float rxA, float rzA, float rxB, float rzB, int n, float rot,
      int argb, float b, boolean capA, boolean capB
   ) {
      float iy = (yA + yB) / 2.0F;
      float[] ax = new float[n], az = new float[n], bx = new float[n], bz = new float[n];
      for (int i = 0; i < n; i++) {
         double a = Math.toRadians(rot) + Math.PI * 2.0 * i / n;
         float co = (float)Math.cos(a), si = (float)Math.sin(a);
         ax[i] = cx + co * rxA;
         az[i] = cz + si * rzA;
         bx[i] = cx + co * rxB;
         bz[i] = cz + si * rzB;
      }

      boolean pointA = rxA < 0.01F && rzA < 0.01F;
      boolean pointB = rxB < 0.01F && rzB < 0.01F;
      for (int i = 0; i < n; i++) {
         int j = (i + 1) % n;
         if (pointA) {
            face(m, vc, argb, b, cx, iy, cz, cx, yA, cz, bx[i], yB, bz[i], bx[j], yB, bz[j]);
         } else if (pointB) {
            face(m, vc, argb, b, cx, iy, cz, ax[i], yA, az[i], ax[j], yA, az[j], cx, yB, cz);
         } else {
            face(m, vc, argb, b, cx, iy, cz, ax[i], yA, az[i], ax[j], yA, az[j], bx[j], yB, bz[j], bx[i], yB, bz[i]);
         }

         if (capA && !pointA) {
            face(m, vc, argb, b, cx, iy, cz, cx, yA, cz, ax[i], yA, az[i], ax[j], yA, az[j]);
         }

         if (capB && !pointB) {
            face(m, vc, argb, b, cx, iy, cz, cx, yB, cz, bx[i], yB, bz[i], bx[j], yB, bz[j]);
         }
      }
   }

   /**
    * Kegelstück mit versetzten Mittelpunkten (für gebogene Spitzen): Ring A um (xA, yA, zA) Radius rA, Ring B um
    * (xB, yB, zB) Radius rB, beide waagerecht. rB = 0 ergibt eine Spitze.
    */
   public static void taper(
      PoseStack m, VertexConsumer vc, float xA, float yA, float zA, float rA, float xB, float yB, float zB, float rB, int n, float rot, int argb, float b,
      boolean capA, boolean capB
   ) {
      float ix = (xA + xB) / 2.0F, iy = (yA + yB) / 2.0F, iz = (zA + zB) / 2.0F;
      for (int i = 0; i < n; i++) {
         double a0 = Math.toRadians(rot) + Math.PI * 2.0 * i / n;
         double a1 = Math.toRadians(rot) + Math.PI * 2.0 * (i + 1) / n;
         float c0 = (float)Math.cos(a0), s0 = (float)Math.sin(a0), c1 = (float)Math.cos(a1), s1 = (float)Math.sin(a1);
         if (rB < 0.01F) {
            face(m, vc, argb, b, ix, iy, iz, xA + c0 * rA, yA, zA + s0 * rA, xA + c1 * rA, yA, zA + s1 * rA, xB, yB, zB);
         } else {
            face(m, vc, argb, b, ix, iy, iz, xA + c0 * rA, yA, zA + s0 * rA, xA + c1 * rA, yA, zA + s1 * rA, xB + c1 * rB, yB, zB + s1 * rB, xB + c0 * rB, yB, zB + s0 * rB);
            if (capB) {
               face(m, vc, argb, b, ix, iy, iz, xB, yB, zB, xB + c0 * rB, yB, zB + s0 * rB, xB + c1 * rB, yB, zB + s1 * rB);
            }
         }

         if (capA) {
            face(m, vc, argb, b, ix, iy, iz, xA, yA, zA, xA + c0 * rA, yA, zA + s0 * rA, xA + c1 * rA, yA, zA + s1 * rA);
         }
      }
   }

   /** Runder Kegelstumpf (gleiche Radien in x und z). */
   public static void frustum(PoseStack m, VertexConsumer vc, float cx, float cz, float yA, float yB, float rA, float rB, int n, float rot, int argb, float b) {
      frustum(m, vc, cx, cz, yA, yB, rA, rA, rB, rB, n, rot, argb, b, true, true);
   }

   /** Flacher Ring / Krempe: Kreisring zwischen rIn und rOut, Dicke y1..y2 (Ellipse über rx/rz-Faktor). */
   public static void ring(
      PoseStack m, VertexConsumer vc, float cx, float cz, float y1, float y2, float rIn, float rOut, float zk, int n, float rot, int argb, float b
   ) {
      float rm = (rIn + rOut) / 2.0F;
      float ym = (y1 + y2) / 2.0F;
      for (int i = 0; i < n; i++) {
         double a0 = Math.toRadians(rot) + Math.PI * 2.0 * i / n;
         double a1 = Math.toRadians(rot) + Math.PI * 2.0 * (i + 1) / n;
         float c0 = (float)Math.cos(a0), s0 = (float)Math.sin(a0) * zk, c1 = (float)Math.cos(a1), s1 = (float)Math.sin(a1) * zk;
         double am = (a0 + a1) / 2.0;
         float px = cx + (float)Math.cos(am) * rm, pz = cz + (float)Math.sin(am) * rm * zk;
         // oben / unten
         face(m, vc, argb, b, px, ym, pz, cx + c0 * rIn, y1, cz + s0 * rIn, cx + c0 * rOut, y1, cz + s0 * rOut, cx + c1 * rOut, y1, cz + s1 * rOut, cx + c1 * rIn, y1, cz + s1 * rIn);
         face(m, vc, argb, b, px, ym, pz, cx + c0 * rIn, y2, cz + s0 * rIn, cx + c0 * rOut, y2, cz + s0 * rOut, cx + c1 * rOut, y2, cz + s1 * rOut, cx + c1 * rIn, y2, cz + s1 * rIn);
         // außen / innen
         face(m, vc, argb, b, px, ym, pz, cx + c0 * rOut, y1, cz + s0 * rOut, cx + c1 * rOut, y1, cz + s1 * rOut, cx + c1 * rOut, y2, cz + s1 * rOut, cx + c0 * rOut, y2, cz + s0 * rOut);
         if (rIn > 0.05F) {
            face(m, vc, argb, b, px, ym, pz, cx + c0 * rIn, y1, cz + s0 * rIn, cx + c1 * rIn, y1, cz + s1 * rIn, cx + c1 * rIn, y2, cz + s1 * rIn, cx + c0 * rIn, y2, cz + s0 * rIn);
         }
      }
   }

   /** Geschliffener Edelstein (Doppelpyramide mit n Facetten), Spitze nach oben {@code up}, nach unten {@code down}. */
   public static void gem(PoseStack m, VertexConsumer vc, float x, float y, float z, float r, float up, float down, int n, float rot, int argb, float b) {
      for (int i = 0; i < n; i++) {
         double a0 = Math.toRadians(rot) + Math.PI * 2.0 * i / n;
         double a1 = Math.toRadians(rot) + Math.PI * 2.0 * (i + 1) / n;
         float x0 = x + (float)Math.cos(a0) * r, z0 = z + (float)Math.sin(a0) * r;
         float x1 = x + (float)Math.cos(a1) * r, z1 = z + (float)Math.sin(a1) * r;
         // obere Facetten etwas heller (Lichtbrechung)
         face(m, vc, i % 2 == 0 ? bright(argb, 1.08F) : argb, b, x, y, z, x0, y, z0, x1, y, z1, x, y - up, z);
         face(m, vc, i % 2 == 0 ? argb : bright(argb, 0.9F), b, x, y, z, x0, y, z0, x1, y, z1, x, y + down, z);
      }
   }

   /** Low-Poly-Kugel bzw. -Ellipsoid (lat Breitengrade, lon Längengrade). */
   public static void sphere(PoseStack m, VertexConsumer vc, float x, float y, float z, float rx, float ry, float rz, int lat, int lon, int argb, float b) {
      for (int i = 0; i < lat; i++) {
         double t0 = Math.PI * i / lat, t1 = Math.PI * (i + 1) / lat;
         float y0 = y - (float)Math.cos(t0) * ry, y1 = y - (float)Math.cos(t1) * ry;
         float s0 = (float)Math.sin(t0), s1 = (float)Math.sin(t1);
         for (int j = 0; j < lon; j++) {
            double p0 = Math.PI * 2.0 * j / lon + (i % 2) * Math.PI / lon, p1 = Math.PI * 2.0 * (j + 1) / lon + (i % 2) * Math.PI / lon;
            float c0 = (float)Math.cos(p0), n0 = (float)Math.sin(p0), c1 = (float)Math.cos(p1), n1 = (float)Math.sin(p1);
            if (i == 0) {
               face(m, vc, argb, b, x, y, z, x, y0, z, x + c0 * s1 * rx, y1, z + n0 * s1 * rz, x + c1 * s1 * rx, y1, z + n1 * s1 * rz);
            } else if (i == lat - 1) {
               face(m, vc, argb, b, x, y, z, x + c0 * s0 * rx, y0, z + n0 * s0 * rz, x + c1 * s0 * rx, y0, z + n1 * s0 * rz, x, y1, z);
            } else {
               face(
                  m, vc, argb, b, x, y, z, x + c0 * s0 * rx, y0, z + n0 * s0 * rz, x + c1 * s0 * rx, y0, z + n1 * s0 * rz, x + c1 * s1 * rx, y1, z + n1 * s1 * rz,
                  x + c0 * s1 * rx, y1, z + n0 * s1 * rz
               );
            }
         }
      }
   }

   /** Obere Halbkugel (Kuppel) mit flachem Boden auf Höhe y – für Hüte und Helme, die nicht ins Gesicht ragen dürfen. */
   public static void dome(PoseStack m, VertexConsumer vc, float x, float y, float z, float rx, float ry, float rz, int lat, int lon, int argb, float b) {
      float iy = y - ry * 0.4F;
      for (int i = 0; i < lat; i++) {
         double t0 = Math.PI / 2.0 * i / lat, t1 = Math.PI / 2.0 * (i + 1) / lat;
         float y0 = y - (float)Math.cos(t0) * ry, y1 = y - (float)Math.cos(t1) * ry;
         float s0 = (float)Math.sin(t0), s1 = (float)Math.sin(t1);
         for (int j = 0; j < lon; j++) {
            double p0 = Math.PI * 2.0 * j / lon + (i % 2) * Math.PI / lon, p1 = Math.PI * 2.0 * (j + 1) / lon + (i % 2) * Math.PI / lon;
            float c0 = (float)Math.cos(p0), n0 = (float)Math.sin(p0), c1 = (float)Math.cos(p1), n1 = (float)Math.sin(p1);
            if (i == 0) {
               face(m, vc, argb, b, x, iy, z, x, y0, z, x + c0 * s1 * rx, y1, z + n0 * s1 * rz, x + c1 * s1 * rx, y1, z + n1 * s1 * rz);
            } else {
               face(
                  m, vc, argb, b, x, iy, z, x + c0 * s0 * rx, y0, z + n0 * s0 * rz, x + c1 * s0 * rx, y0, z + n1 * s0 * rz, x + c1 * s1 * rx, y1, z + n1 * s1 * rz,
                  x + c0 * s1 * rx, y1, z + n0 * s1 * rz
               );
            }

            if (i == lat - 1) {
               face(m, vc, argb, b, x, iy, z, x, y, z, x + c0 * rx, y, z + n0 * rz, x + c1 * rx, y, z + n1 * rz);
            }
         }
      }
   }

   /** Ast / Stab von a nach b, Radius r1 → r2, n Seiten (facettiert). */
   public static void limb(PoseStack m, VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2, float r1, float r2, int n, int argb, float b) {
      float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
      float len = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (len < 1.0E-4F) {
         return;
      }

      dx /= len;
      dy /= len;
      dz /= len;
      // zwei Achsen senkrecht zur Richtung
      float ux, uy, uz;
      if (Math.abs(dy) < 0.9F) {
         ux = dz;
         uy = 0.0F;
         uz = -dx;
      } else {
         ux = 0.0F;
         uy = -dz;
         uz = dy;
      }

      float ul = (float)Math.sqrt(ux * ux + uy * uy + uz * uz);
      ux /= ul;
      uy /= ul;
      uz /= ul;
      float wx = dy * uz - dz * uy, wy = dz * ux - dx * uz, wz = dx * uy - dy * ux;
      float mx = (x1 + x2) / 2.0F, my = (y1 + y2) / 2.0F, mz = (z1 + z2) / 2.0F;
      float[][] a = new float[n][], c = new float[n][];
      for (int i = 0; i < n; i++) {
         double t = Math.PI * 2.0 * i / n;
         float co = (float)Math.cos(t), si = (float)Math.sin(t);
         float ox = ux * co + wx * si, oy = uy * co + wy * si, oz = uz * co + wz * si;
         a[i] = new float[]{x1 + ox * r1, y1 + oy * r1, z1 + oz * r1};
         c[i] = new float[]{x2 + ox * r2, y2 + oy * r2, z2 + oz * r2};
      }

      for (int i = 0; i < n; i++) {
         int j = (i + 1) % n;
         if (r2 < 0.01F) {
            face(m, vc, argb, b, mx, my, mz, a[i][0], a[i][1], a[i][2], a[j][0], a[j][1], a[j][2], x2, y2, z2);
         } else {
            face(m, vc, argb, b, mx, my, mz, a[i][0], a[i][1], a[i][2], a[j][0], a[j][1], a[j][2], c[j][0], c[j][1], c[j][2], c[i][0], c[i][1], c[i][2]);
            face(m, vc, argb, b, mx, my, mz, x2, y2, z2, c[i][0], c[i][1], c[i][2], c[j][0], c[j][1], c[j][2]);
         }

         face(m, vc, argb, b, mx, my, mz, x1, y1, z1, a[i][0], a[i][1], a[i][2], a[j][0], a[j][1], a[j][2]);
      }
   }

   // ================================================================== Leuchten

   /** Leuchtring in der y-z-Ebene (Achse x) aus kleinen Leuchtwürfeln – z.B. Ränder von Kopfhörern. */
   public static void glowRing(PoseStack m, VertexConsumer vc, float x, float y, float z, float r, int rgb, float alpha) {
      for (int i = 0; i < 14; i++) {
         double a = Math.PI * 2.0 * i / 14.0;
         Shapes.glowCube(m, vc, x, y + (float)Math.cos(a) * r, z + (float)Math.sin(a) * r, 0.32F, rgb, alpha);
      }
   }

   /** Weich leuchtender Edelstein-Kern (drei verschachtelte Leuchtwürfel). */
   public static void glowSpark(PoseStack m, VertexConsumer vc, float x, float y, float z, float r, int rgb, float alpha) {
      Shapes.glowCube(m, vc, x, y, z, r, rgb, alpha * 0.35F);
      Shapes.glowCube(m, vc, x, y, z, r * 0.6F, rgb, alpha * 0.6F);
      Shapes.glowCube(m, vc, x, y, z, r * 0.3F, 0xFFFFFFFF, alpha);
   }

   // ================================================================== Sakura

   /**
    * Kirschblüte mit fünf gekerbten, leicht gewölbten Blütenblättern in der x-z-Ebene (öffnet nach oben, -y), Mitte bei
    * (x, y, z). Drehen über die Pose. {@code petal} = Blattfarbe, Mitte wird dunkler.
    */
   public static void sakura(PoseStack m, VertexConsumer vc, float x, float y, float z, float s, float rot, int petal, float b) {
      int inner = mix(petal, ROSE, 0.45F);
      int tip = mix(petal, WHITE, 0.35F);
      for (int i = 0; i < 5; i++) {
         double a = Math.toRadians(rot) + Math.PI * 2.0 * i / 5.0;
         float dx = (float)Math.cos(a), dz = (float)Math.sin(a);
         float px = -dz, pz = dx;
         float b0 = 0.18F * s, mid = 0.55F * s, w = 0.34F * s, end = 1.0F * s, notch = 0.84F * s;
         float lift = -0.22F * s;
         float[] base = {x + dx * b0, y, z + dz * b0};
         float[] l = {x + dx * mid + px * w, y + lift * 0.5F, z + dz * mid + pz * w};
         float[] r = {x + dx * mid - px * w, y + lift * 0.5F, z + dz * mid - pz * w};
         float[] tl = {x + dx * end + px * w * 0.55F, y + lift, z + dz * end + pz * w * 0.55F};
         float[] tr = {x + dx * end - px * w * 0.55F, y + lift, z + dz * end - pz * w * 0.55F};
         float[] nt = {x + dx * notch, y + lift * 0.9F, z + dz * notch};
         sheet(m, vc, inner, b, base[0], base[1], base[2], l[0], l[1], l[2], nt[0], nt[1], nt[2], r[0], r[1], r[2]);
         sheet(m, vc, petal, b, l[0], l[1], l[2], tl[0], tl[1], tl[2], nt[0], nt[1], nt[2]);
         sheet(m, vc, tip, b, nt[0], nt[1], nt[2], tr[0], tr[1], tr[2], r[0], r[1], r[2]);
      }

      // Blütenmitte mit Staubgefäßen
      gem(m, vc, x, y - 0.05F * s, z, 0.2F * s, 0.18F * s, 0.1F * s, 5, rot, ROSE, b);
      for (int i = 0; i < 5; i++) {
         double a = Math.toRadians(rot + 36.0) + Math.PI * 2.0 * i / 5.0;
         float sx = x + (float)Math.cos(a) * 0.3F * s, sz = z + (float)Math.sin(a) * 0.3F * s;
         box(m, vc, sx - 0.05F * s, y - 0.32F * s, sz - 0.05F * s, sx + 0.05F * s, y - 0.12F * s, sz + 0.05F * s, GOLD, b);
      }
   }

   /** Einzelnes Blütenblatt (gekerbt, beidseitig), Spitze in Richtung (dx, dz) in der Ebene y, leicht gekippt. */
   public static void petal(PoseStack m, VertexConsumer vc, float x, float y, float z, float s, float angle, float tilt, int col, float b) {
      float dx = (float)Math.cos(Math.toRadians(angle)), dz = (float)Math.sin(Math.toRadians(angle));
      float px = -dz, pz = dx;
      float ty = (float)Math.sin(Math.toRadians(tilt)) * s;
      sheet(
         m, vc, col, b, x, y, z, x + dx * 0.5F * s + px * 0.32F * s, y + ty * 0.5F, z + dz * 0.5F * s + pz * 0.32F * s, x + dx * 0.85F * s, y + ty * 0.85F,
         z + dz * 0.85F * s, x + dx * 0.5F * s - px * 0.32F * s, y + ty * 0.5F, z + dz * 0.5F * s - pz * 0.32F * s
      );
      sheet(
         m, vc, mix(col, WHITE, 0.25F), b, x + dx * 0.5F * s + px * 0.32F * s, y + ty * 0.5F, z + dz * 0.5F * s + pz * 0.32F * s, x + dx * s + px * 0.16F * s,
         y + ty, z + dz * s + pz * 0.16F * s, x + dx * 0.85F * s, y + ty * 0.85F, z + dz * 0.85F * s
      );
      sheet(
         m, vc, mix(col, WHITE, 0.25F), b, x + dx * 0.85F * s, y + ty * 0.85F, z + dz * 0.85F * s, x + dx * s - px * 0.16F * s, y + ty,
         z + dz * s - pz * 0.16F * s, x + dx * 0.5F * s - px * 0.32F * s, y + ty * 0.5F, z + dz * 0.5F * s - pz * 0.32F * s
      );
   }

   /** Kleiner Blütenzweig: Ast von a nach b mit Blüten und Knospen entlang. */
   public static void sprig(PoseStack m, VertexConsumer vc, float x1, float y1, float z1, float x2, float y2, float z2, float s, int petal, float b) {
      limb(m, vc, x1, y1, z1, x2, y2, z2, 0.32F * s, 0.14F * s, 5, WOOD, b);
      sakura(m, vc, x2, y2, z2, 0.9F * s, 12.0F, petal, b);
      float mx = x1 + (x2 - x1) * 0.55F, my = y1 + (y2 - y1) * 0.55F, mz = z1 + (z2 - z1) * 0.55F;
      sakura(m, vc, mx, my - 0.3F * s, mz, 0.65F * s, 47.0F, petal, b);
      gem(m, vc, x1 + (x2 - x1) * 0.25F, y1 + (y2 - y1) * 0.25F - 0.25F * s, z1 + (z2 - z1) * 0.25F, 0.22F * s, 0.32F * s, 0.12F * s, 5, 0.0F, ROSE, b);
   }

   // ================================================================== Sakura-Geist (Voxel-Hirsch-Stil)

   /** Fester Pseudo-Zufall 0..1 für Variation, die sich nicht bewegt. */
   public static float hash(int i, int k) {
      double v = Math.sin(i * 12.9898 + k * 78.233) * 43758.5453;
      return (float)(v - Math.floor(v));
   }

   /** Flache rosa Raute (Markierung) auf einer Fläche: Normale entlang x ({@code axis} 0), y (1) oder z (2). */
   public static void diamond(PoseStack m, VertexConsumer vc, float x, float y, float z, float s, int axis, int argb, float b) {
      m.pushPose();
      m.translate(x, y, z);
      if (axis == 0) {
         m.mulPose(Axis.XP.rotationDegrees(45.0F));
         box(m, vc, -0.06F, -s, -s, 0.06F, s, s, argb, Math.max(b, 0.8F));
      } else if (axis == 1) {
         m.mulPose(Axis.YP.rotationDegrees(45.0F));
         box(m, vc, -s, -0.06F, -s, s, 0.06F, s, argb, Math.max(b, 0.8F));
      } else {
         m.mulPose(Axis.ZP.rotationDegrees(45.0F));
         box(m, vc, -s, -s, -0.06F, s, s, 0.06F, argb, Math.max(b, 0.8F));
      }

      m.popPose();
   }

   /**
    * Blütenwolke wie an einem Kirschbaum: gestaffelte Würfel in Rosatönen (Voxel-Look) mit einzelnen offenen Blüten
    * obendrauf. Mitte (x, y, z), Radius s, {@code seed} variiert die Form.
    */
   public static void bloom(PoseStack m, VertexConsumer vc, float x, float y, float z, float s, int seed, float b) {
      int[] cols = {SAKURA, BLOSSOM, mix(SAKURA, ROSE, 0.35F), WHITE, mix(BLOSSOM, SAKURA, 0.5F)};
      for (int i = 0; i < 12; i++) {
         float a = hash(seed, i) * 6.2832F, h = hash(seed + 7, i), r = (0.25F + hash(seed + 3, i) * 0.6F) * s;
         float cx = x + (float)Math.cos(a) * r, cy = y - (h - 0.35F) * s * 0.9F, cz = z + (float)Math.sin(a) * r;
         float q = (0.3F + hash(seed + 11, i) * 0.22F) * s;
         m.pushPose();
         m.translate(cx, cy, cz);
         m.mulPose(Axis.YP.rotationDegrees(hash(seed + 5, i) * 90.0F));
         m.mulPose(Axis.XP.rotationDegrees((hash(seed + 9, i) - 0.5F) * 40.0F));
         bevel(m, vc, -q, -q, -q, q, q, q, q * 0.22F, cols[(seed + i) % cols.length], Math.max(b, 0.7F));
         m.popPose();
      }

      for (int i = 0; i < 3; i++) {
         float a = hash(seed + 13, i) * 6.2832F, r = hash(seed + 17, i) * 0.5F * s;
         sakura(m, vc, x + (float)Math.cos(a) * r, y - 0.55F * s - hash(seed + 19, i) * 0.3F * s, z + (float)Math.sin(a) * r, 0.42F * s, a * 57.3F, BLOSSOM, Math.max(b, 0.7F));
      }
   }

   /**
    * Kantiges Voxel-Geweih (elfenbein) einer Seite, das in Blütenwolken ausläuft. Basis im Ursprung, oben = -y, {@code side}
    * = +1/-1 spiegelt nach außen (x), Größe s.
    */
   public static void antler(PoseStack m, VertexConsumer vc, int side, float s, int bone, float b) {
      float k = side;
      float[] p0 = {0.0F, 0.0F, 0.0F};
      float[] p1 = {1.1F * s * k, -2.0F * s, 0.35F * s};
      float[] p2 = {1.9F * s * k, -4.1F * s, 0.75F * s};
      float[] p3 = {2.2F * s * k, -6.0F * s, 0.35F * s};
      float[] t1 = {2.5F * s * k, -2.7F * s, -0.7F * s};
      float[] t2 = {0.9F * s * k, -5.5F * s, -0.4F * s};
      float[] t3 = {3.5F * s * k, -4.9F * s, 1.1F * s};
      int ring = bright(bone, 0.92F);
      limb(m, vc, p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], 0.5F * s, 0.4F * s, 4, bone, b);
      limb(m, vc, p1[0], p1[1], p1[2], p2[0], p2[1], p2[2], 0.4F * s, 0.32F * s, 4, ring, b);
      limb(m, vc, p2[0], p2[1], p2[2], p3[0], p3[1], p3[2], 0.32F * s, 0.2F * s, 4, bone, b);
      limb(m, vc, p1[0], p1[1], p1[2], t1[0], t1[1], t1[2], 0.3F * s, 0.16F * s, 4, bone, b);
      limb(m, vc, p2[0], p2[1], p2[2], t2[0], t2[1], t2[2], 0.26F * s, 0.14F * s, 4, bone, b);
      limb(m, vc, p2[0], p2[1], p2[2], t3[0], t3[1], t3[2], 0.26F * s, 0.14F * s, 4, bone, b);
      // Gelenkknoten
      bevel(m, vc, p1[0] - 0.45F * s, p1[1] - 0.45F * s, p1[2] - 0.45F * s, p1[0] + 0.45F * s, p1[1] + 0.45F * s, p1[2] + 0.45F * s, 0.12F * s, WHITE, b);
      bevel(m, vc, p2[0] - 0.36F * s, p2[1] - 0.36F * s, p2[2] - 0.36F * s, p2[0] + 0.36F * s, p2[1] + 0.36F * s, p2[2] + 0.36F * s, 0.1F * s, WHITE, b);
      int seed = side > 0 ? 3 : 29;
      bloom(m, vc, p3[0], p3[1] - 0.4F * s, p3[2], 1.5F * s, seed, b);
      bloom(m, vc, t3[0], t3[1] - 0.3F * s, t3[2], 1.15F * s, seed + 5, b);
      bloom(m, vc, t2[0], t2[1] - 0.3F * s, t2[2], 1.0F * s, seed + 9, b);
      bloom(m, vc, t1[0], t1[1] - 0.2F * s, t1[2], 0.85F * s, seed + 14, b);
   }

   /**
    * Langsam fallende, taumelnde Blütenblätter in einem Zylinder um (x, z): von y0 aus h tief nach unten, Radius r,
    * n Stück. Läuft endlos durch.
    */
   public static void drift(PoseStack m, VertexConsumer vc, float x, float y0, float z, float r, float h, int n, float t, float s, float b) {
      for (int i = 0; i < n; i++) {
         float ph = (t * (0.07F + hash(i, 1) * 0.05F) + hash(i, 2)) % 1.0F;
         float a = hash(i, 3) * 6.2832F + t * 0.25F + ph * 2.0F;
         float rr = r * (0.45F + hash(i, 4) * 0.55F);
         float sway = (float)Math.sin(t * 1.6F + i) * 0.6F;
         m.pushPose();
         m.translate(x + (float)Math.cos(a) * rr + sway, y0 + ph * h, z + (float)Math.sin(a) * rr);
         m.mulPose(Axis.YP.rotationDegrees(t * 60.0F + i * 47.0F));
         m.mulPose(Axis.XP.rotationDegrees((float)Math.sin(t * 2.0F + i * 1.3F) * 50.0F));
         petal(m, vc, 0.0F, 0.0F, 0.0F, s * (0.7F + hash(i, 5) * 0.5F), 0.0F, 18.0F, i % 4 == 0 ? BLOSSOM : SAKURA, Math.max(b, 0.75F));
         m.popPose();
      }
   }

   /** Flache Form (Umriss {@code p} in x/y um (cx, cy)) zwischen z0 und z1 hochgezogen, Seiten etwas dunkler. */
   public static void extrude(PoseStack m, VertexConsumer vc, float cx, float cy, float[][] p, float z0, float z1, int col, float b) {
      int n = p.length;
      float iz = (z0 + z1) / 2.0F;
      for (int i = 0; i < n; i++) {
         int j = (i + 1) % n;
         face(m, vc, col, b, cx, cy, iz, cx, cy, z0, cx + p[i][0], cy + p[i][1], z0, cx + p[j][0], cy + p[j][1], z0);
         face(m, vc, col, b, cx, cy, iz, cx, cy, z1, cx + p[i][0], cy + p[i][1], z1, cx + p[j][0], cy + p[j][1], z1);
         face(
            m, vc, bright(col, 0.88F), b, cx, cy, iz, cx + p[i][0], cy + p[i][1], z0, cx + p[j][0], cy + p[j][1], z0, cx + p[j][0], cy + p[j][1], z1,
            cx + p[i][0], cy + p[i][1], z1
         );
      }
   }

   /** Herzumriss mit n Punkten, Größe s, Spitze nach unten (+y). */
   public static float[][] heart(float s, int n) {
      float[][] p = new float[n][];
      for (int i = 0; i < n; i++) {
         double a = Math.PI * 2.0 * i / n;
         float x = (float)(16.0 * Math.pow(Math.sin(a), 3.0));
         float y = (float)(13.0 * Math.cos(a) - 5.0 * Math.cos(2.0 * a) - 2.0 * Math.cos(3.0 * a) - Math.cos(4.0 * a));
         p[i] = new float[]{x / 16.0F * s, -y / 16.0F * s};
      }

      return p;
   }
}
