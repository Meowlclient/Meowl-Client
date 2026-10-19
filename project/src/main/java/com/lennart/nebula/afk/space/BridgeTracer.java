package com.lennart.nebula.afk.space;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Baut die Kommandobrücke aus echten Minecraft-Blocktexturen (16 Texel pro Block) und rendert sie einmalig per
 * Raytracing in ein Bild: Minecraft-Flächenschattierung, Licht durch die Fenster mit Schatten, Deckenleisten,
 * spiegelnder schwarzer Boden. Fenster bleiben durchsichtig – dahinter zeichnet {@link SpaceView} live den Weltraum.
 * Zusätzlich entstehen Lichtmasken (Fensterlicht, Deckenlicht), mit denen Explosionen und Alarm den Raum live
 * einfärben, sowie die Positionen der blinkenden Konsolenlampen.
 */
public final class BridgeTracer {
   public static final int VW = 960;
   public static final int VH = 540;
   /** Kamera hinter dem Spieler, leicht nach unten geneigt. */
   public static final Cam CAM = new Cam(VW, VH, 0.0F, 3.35F, -3.4F, -9.0F, 62.0F);
   /** Füße des Spielers auf dem Mittelsteg. */
   public static final float PLAYER_X = 0.0F;
   public static final float PLAYER_Y = 0.0F;
   public static final float PLAYER_Z = 2.7F;
   static final float FRONT_Z = 13.0F;
   static final float WALL_X = 12.0F;
   static final float CEIL_Y = 7.5F;
   static final float PIT_Y = -3.0F;
   private static final float WIN_Y0 = 1.2F;
   private static final float WIN_Y1 = 6.0F;
   private static final Mat GLASS = new Mat(null, 1.0F, 1.0F, 1.0F, false, 0.0F);
   private final List<Box> boxes = new ArrayList<>();
   private final Function<String, Tex> textures;
   private final float[] light;
   private final Noise noise = new Noise(99L);
   // Ergebnisse
   public final Tex image = new Tex(VW, VH);
   /** Alpha = Anteil Fensterlicht (für Explosionsblitze). */
   public final Tex windowLight = new Tex(VW, VH);
   /** Alpha = Deckenlicht/Leuchtleisten (für Alarm). */
   public final Tex alarmLight = new Tex(VW, VH);
   /** Blinklampen: x, y, Größe, Farbindex, Phase. */
   public final List<float[]> blinkers = new ArrayList<>();
   private final Mat ceil;
   private final Mat panel;
   private final Mat iron;
   private final Mat lightGray;
   private final Mat gray;
   private final Mat black;
   private final Mat blackFloor;
   private final Mat deepslate;
   private final Mat deepslateFloor;
   private final Mat blackstone;
   private final Mat strip;
   private final Mat screenWhite;
   private final Mat screenRed;
   private final Mat screenBlue;
   private final Mat screenGreen;
   private final Mat seam;

   /**
    * @param textures liefert Blocktexturen nach Namen (z.B. "smooth_stone")
    * @param windowLight Farbe des Lichts durch die Fenster (abhängig vom Planeten), RGB 0..1+
    */
   public BridgeTracer(Function<String, Tex> textures, float[] windowLight) {
      this.textures = textures;
      this.light = windowLight;
      this.ceil = this.mat("smooth_stone", 0.92F, 0.93F, 0.96F);
      this.panel = this.mat("polished_andesite", 0.9F, 0.92F, 0.95F);
      this.iron = this.mat("iron_block", 0.85F, 0.86F, 0.9F);
      this.lightGray = this.mat("light_gray_concrete", 1.0F, 1.0F, 1.03F);
      this.gray = this.mat("gray_concrete", 1.0F, 1.0F, 1.05F);
      this.black = this.mat("black_concrete", 1.0F, 1.0F, 1.0F);
      this.blackFloor = new Mat(this.tex("black_concrete"), 1.25F, 1.25F, 1.35F, false, 0.6F);
      this.deepslate = this.mat("polished_deepslate", 0.95F, 0.95F, 1.0F);
      this.deepslateFloor = new Mat(this.tex("polished_deepslate"), 0.9F, 0.9F, 0.95F, false, 0.32F);
      this.blackstone = this.mat("polished_blackstone", 0.9F, 0.9F, 0.95F);
      this.strip = new Mat(this.tex("sea_lantern"), 1.05F, 1.08F, 1.12F, true, 0.0F);
      this.screenWhite = new Mat(this.tex("white_concrete"), 1.0F, 1.0F, 1.0F, true, 0.0F);
      this.screenRed = new Mat(this.tex("redstone_lamp_on"), 1.2F, 0.35F, 0.3F, true, 0.0F);
      this.screenBlue = new Mat(this.tex("light_blue_concrete"), 0.85F, 1.0F, 1.15F, true, 0.0F);
      this.screenGreen = new Mat(this.tex("lime_concrete"), 0.7F, 1.0F, 0.75F, true, 0.0F);
      this.seam = this.mat("gray_concrete", 0.55F, 0.55F, 0.6F);
      this.build();
   }

   private Tex tex(String name) {
      Tex t = this.textures.apply(name);
      return t != null ? t.firstFrame() : Tex.solid(-8355712);
   }

   private Mat mat(String name, float r, float g, float b) {
      return new Mat(this.tex(name), r, g, b, false, 0.0F);
   }

   // ------------------------------------------------------------------ Aufbau

   private void build() {
      // Raumhülle
      this.add(-WALL_X - 1, PIT_Y - 1, -9.0F, WALL_X + 1, PIT_Y, FRONT_Z + 1, (f, x, y, z) -> this.pitFloor(x, z));
      this.add(-WALL_X - 1, PIT_Y, -9.0F, -WALL_X, CEIL_Y + 1, FRONT_Z + 1, (f, x, y, z) -> this.sideWall(-1, y, z));
      this.add(WALL_X, PIT_Y, -9.0F, WALL_X + 1, CEIL_Y + 1, FRONT_Z + 1, (f, x, y, z) -> this.sideWall(1, y, z));
      this.add(-WALL_X - 1, CEIL_Y, -9.0F, WALL_X + 1, CEIL_Y + 1, FRONT_Z + 1, (f, x, y, z) -> this.ceiling(x, z));
      this.add(-WALL_X - 1, PIT_Y, FRONT_Z, WALL_X + 1, CEIL_Y + 1, FRONT_Z + 1, (f, x, y, z) -> this.frontWall(x, y));
      this.add(-WALL_X - 1, PIT_Y, -10.0F, WALL_X + 1, CEIL_Y + 1, -9.0F, (f, x, y, z) -> this.gray);
      // Mittelsteg
      this.add(-2.0F, PIT_Y, -9.0F, 2.0F, 0.0F, 9.5F, (f, x, y, z) -> f == 3 ? this.walkway(x, z) : this.blackstone);
      // Konsolenbank unter den Fenstern
      this.add(-WALL_X, PIT_Y, 11.6F, WALL_X, 0.55F, FRONT_Z, (f, x, y, z) -> f == 3 ? this.blackFloor : (f == 4 ? this.consoleFront(x, y, 11.6F) : this.deepslate));
      // Deckenträger quer und Lüftungskanäle längs
      for (float bz : new float[]{-5.0F, -0.5F, 4.0F, 8.5F}) {
         this.add(-WALL_X, 6.55F, bz, WALL_X, CEIL_Y, bz + 0.9F, (f, x, y, z) -> f == 2 ? this.iron : this.lightGray);
      }

      // Längsträger mit Leuchtleiste darunter
      for (float lx : new float[]{-7.0F, 7.0F}) {
         this.add(lx - 0.8F, 6.9F, -9.0F, lx + 0.8F, CEIL_Y, FRONT_Z, (f, x, y, z) -> f == 2 ? (Math.abs(x - lx) < 0.3F ? this.strip : this.iron) : this.lightGray);
      }

      // Konsolen und Sitze in beiden Gruben
      for (int s = -1; s <= 1; s += 2) {
         for (float cz : new float[]{-1.5F, 2.2F, 5.9F, 9.4F}) {
            this.console(s, cz);
         }
      }
   }

   private void console(int s, float z0) {
      float wx0 = s * 9.3F;
      float wx1 = s * 10.9F;
      float x0 = Math.min(wx0, wx1);
      float x1 = Math.max(wx0, wx1);
      // Unterbau, schräges Pult in zwei Stufen
      this.add(x0, PIT_Y, z0, x1, -1.75F, z0 + 2.3F, (f, x, y, z) -> f == 3 ? this.black : this.gray);
      float px0 = s > 0 ? x0 + 0.6F : x0;
      float px1 = s > 0 ? x1 : x1 - 0.6F;
      this.add(px0, -1.75F, z0, px1, -1.15F, z0 + 2.3F, (f, x, y, z) -> f == 3 ? this.screen(x, z, 3) : (f == (s > 0 ? 0 : 1) ? this.screen(z, y, 7) : this.gray));
      // Sitz
      float sx = s * 8.0F;
      this.add(Math.min(sx, sx + s * 0.9F), PIT_Y, z0 + 0.6F, Math.max(sx, sx + s * 0.9F), -2.35F, z0 + 1.6F, (f, x, y, z) -> f == 3 ? this.blackstone : this.deepslate);
      float bx = s * 7.85F;
      this.add(Math.min(bx, bx + s * 0.25F), -2.35F, z0 + 0.6F, Math.max(bx, bx + s * 0.25F), -1.35F, z0 + 1.6F, (f, x, y, z) -> this.deepslate);
      // Lämpchen auf dem Pult merken
      for (int k = 0; k < 4; k++) {
         float lz = z0 + 0.35F + k * 0.5F;
         float lx = s > 0 ? x0 + 0.25F : x1 - 0.25F;
         this.blinker(lx, -1.7F, lz, k);
      }
   }

   private void blinker(float x, float y, float z, int color) {
      float[] p = new float[3];
      if (CAM.project(x, y, z, p)) {
         float size = Math.max(1.0F, CAM.scale(p[2]) * 0.14F);
         this.blinkers.add(new float[]{p[0], p[1], size, color % 4, this.noise.rand((int)(x * 10.0F), (int)(y * 10.0F), (int)(z * 10.0F)) * 6.28F});
      }
   }

   // ------------------------------------------------------------------ Flächenmuster

   private Mat pitFloor(float x, float z) {
      float fx = frac(x / 2.0F);
      float fz = frac(z / 2.0F);
      return !(fx < 0.03F) && !(fz < 0.03F) ? this.deepslateFloor : this.seam;
   }

   private Mat walkway(float x, float z) {
      if (Math.abs(x) > 1.86F) {
         return this.iron;
      } else {
         float fz = frac(z / 2.0F);
         return fz < 0.025F ? this.seam : this.blackFloor;
      }
   }

   private Mat ceiling(float x, float z) {
      float ax = Math.abs(x);
      if (ax > 4.55F && ax < 5.45F) {
         return this.strip;
      } else if (ax < 0.4F) {
         return this.strip;
      } else {
         float fz = frac(z / 3.0F);
         float fx = frac(x / 3.0F);
         return !(fz < 0.04F) && !(fx < 0.04F) ? (((int)Math.floor(x / 3.0F) + (int)Math.floor(z / 3.0F) & 1) == 0 ? this.ceil : this.panel) : this.gray;
      }
   }

   private Mat sideWall(int side, float y, float z) {
      if (y < 2.0F) {
         // beleuchtete Schaltwände wie auf echten Brücken
         if (y < -2.6F || y > 1.75F) {
            return this.blackstone;
         } else {
            int cz = (int)Math.floor(z * 2.0F);
            int cy = (int)Math.floor(y * 3.0F);
            float fz = frac(z * 2.0F);
            float fy = frac(y * 3.0F);
            float r = this.noise.rand(cz, cy, side);
            if (fz > 0.12F && fz < 0.88F && fy > 0.18F && fy < 0.82F && r > 0.38F) {
               return r > 0.95F ? this.screenRed : (r > 0.9F ? this.screenBlue : this.screenWhite);
            } else {
               return this.black;
            }
         }
      } else if (this.sideWindow(y, z)) {
         return GLASS;
      } else if (this.sideWindow(y + 0.3F, z) || this.sideWindow(y - 0.3F, z) || this.sideWindow(y, z + 0.3F) || this.sideWindow(y, z - 0.3F)) {
         return this.deepslate;
      } else if (y > 6.3F) {
         return this.iron;
      } else {
         return frac(z / 2.5F) < 0.04F ? this.seam : this.lightGray;
      }
   }

   private boolean sideWindow(float y, float z) {
      float yq = (float)Math.floor(y * 2.0F) / 2.0F + 0.25F;
      float zq = (float)Math.floor(z * 2.0F) / 2.0F + 0.25F;
      if (!(zq < 7.4F) && !(zq > 12.6F)) {
         float top = 5.9F - (12.6F - zq) * 0.22F;
         return yq > 2.6F && yq < top;
      } else {
         return false;
      }
   }

   /** Fensterform: fächerförmige Streben, die zu einem Punkt unter der Brücke zeigen – in halben Blöcken gestuft. */
   static boolean frontWindow(float x, float y) {
      float xq = (float)Math.floor(x * 2.0F) / 2.0F + 0.25F;
      float yq = (float)Math.floor(y * 2.0F) / 2.0F + 0.25F;
      if (!(yq < WIN_Y0) && !(yq > WIN_Y1)) {
         float k = Math.abs(xq) / (yq + 9.0F);
         return k < 0.17F || k > 0.25F && k < 0.56F || k > 0.64F && Math.abs(xq) < WALL_X - 0.9F;
      } else {
         return false;
      }
   }

   private Mat frontWall(float x, float y) {
      if (frontWindow(x, y)) {
         return GLASS;
      } else if (frontWindow(x + 0.35F, y) || frontWindow(x - 0.35F, y) || frontWindow(x, y + 0.35F) || frontWindow(x, y - 0.35F)) {
         return this.deepslate;
      } else if (y > WIN_Y1 + 0.5F) {
         return frac(x / 3.0F) < 0.04F ? this.seam : this.lightGray;
      } else {
         return y < WIN_Y0 ? this.blackstone : this.gray;
      }
   }

   private Mat consoleFront(float x, float y, float z) {
      int cx = (int)Math.floor(x * 2.5F);
      int cy = (int)Math.floor(y * 3.0F);
      float fx = frac(x * 2.5F);
      float fy = frac(y * 3.0F);
      float r = this.noise.rand(cx, cy, 7);
      if (y > -0.9F && y < 0.3F && fx > 0.15F && fx < 0.85F && fy > 0.2F && fy < 0.8F && r > 0.55F) {
         return r > 0.93F ? this.screenRed : (r > 0.86F ? this.screenGreen : this.screenWhite);
      } else {
         return y > 0.38F ? this.iron : this.deepslate;
      }
   }

   /** Kleine Bildschirme/Tasten auf Pulten. */
   private Mat screen(float a, float b, int salt) {
      int ca = (int)Math.floor(a * 4.0F);
      int cb = (int)Math.floor(b * 4.0F);
      float fa = frac(a * 4.0F);
      float fb = frac(b * 4.0F);
      float r = this.noise.rand(ca, cb, salt);
      if (fa > 0.15F && fa < 0.85F && fb > 0.15F && fb < 0.85F && r > 0.5F) {
         return r > 0.9F ? this.screenRed : (r > 0.75F ? this.screenGreen : this.screenBlue);
      } else {
         return this.black;
      }
   }

   private static float frac(float v) {
      return v - (float)Math.floor(v);
   }

   private void add(float x0, float y0, float z0, float x1, float y1, float z1, Painter painter) {
      this.boxes.add(new Box(x0, y0, z0, x1, y1, z1, painter));
   }

   // ------------------------------------------------------------------ Raytracing

   private static final class Hit {
      float t = Float.MAX_VALUE;
      int face;
      Box box;
   }

   /** Flächen: 0=-x 1=+x 2=-y 3=+y 4=-z 5=+z (Normale zeigt nach außen). */
   private boolean trace(float ox, float oy, float oz, float dx, float dy, float dz, Hit hit, float maxT) {
      hit.t = maxT;
      hit.box = null;

      for (Box b : this.boxes) {
         float tx0 = (b.x0 - ox) / dx;
         float tx1 = (b.x1 - ox) / dx;
         int fx = dx > 0.0F ? 0 : 1;
         if (tx0 > tx1) {
            float tmp = tx0;
            tx0 = tx1;
            tx1 = tmp;
         }

         float ty0 = (b.y0 - oy) / dy;
         float ty1 = (b.y1 - oy) / dy;
         int fy = dy > 0.0F ? 2 : 3;
         if (ty0 > ty1) {
            float tmp = ty0;
            ty0 = ty1;
            ty1 = tmp;
         }

         float tz0 = (b.z0 - oz) / dz;
         float tz1 = (b.z1 - oz) / dz;
         int fz = dz > 0.0F ? 4 : 5;
         if (tz0 > tz1) {
            float tmp = tz0;
            tz0 = tz1;
            tz1 = tmp;
         }

         float tNear = tx0;
         int face = fx;
         if (ty0 > tNear) {
            tNear = ty0;
            face = fy;
         }

         if (tz0 > tNear) {
            tNear = tz0;
            face = fz;
         }

         float tFar = Math.min(tx1, Math.min(ty1, tz1));
         if (tNear <= tFar && tNear > 1.0E-4F && tNear < hit.t) {
            hit.t = tNear;
            hit.face = face;
            hit.box = b;
         }
      }

      return hit.box != null;
   }

   private static final float[] SHADE = {0.68F, 0.68F, 0.55F, 1.0F, 0.82F, 0.82F};
   private static final float[][] NORMAL = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};

   /** Liefert Farbe in [0]..[2], Alpha in [3]; [4] Fensterlicht-Anteil, [5] Deckenlicht-Anteil. */
   private void shade(float ox, float oy, float oz, float dx, float dy, float dz, boolean allowGloss, float[] out) {
      Hit hit = new Hit();
      if (!this.trace(ox, oy, oz, dx, dy, dz, hit, 200.0F)) {
         out[0] = out[1] = out[2] = 0.0F;
         out[3] = 0.0F;
         out[4] = out[5] = 0.0F;
         return;
      }

      float px = ox + dx * hit.t;
      float py = oy + dy * hit.t;
      float pz = oz + dz * hit.t;
      Mat m = hit.box.painter.paint(hit.face, px, py, pz);
      if (m == GLASS) {
         out[0] = 0.6F;
         out[1] = 0.68F;
         out[2] = 0.78F;
         out[3] = 0.09F;
         out[4] = 0.0F;
         out[5] = 0.0F;
         return;
      }

      // Texel: 16 pro Block, Ausrichtung je Fläche
      float u;
      float v;
      switch (hit.face) {
         case 0:
         case 1:
            u = pz;
            v = -py;
            break;
         case 2:
         case 3:
            u = px;
            v = pz;
            break;
         default:
            u = px;
            v = -py;
      }

      int texel = m.tex.at((int)Math.floor(u * 16.0F), (int)Math.floor(v * 16.0F));
      float r = (texel >> 16 & 0xFF) / 255.0F * m.r;
      float g = (texel >> 8 & 0xFF) / 255.0F * m.g;
      float b = (texel & 0xFF) / 255.0F * m.b;
      if (m.emissive) {
         out[0] = r * 1.05F;
         out[1] = g * 1.05F;
         out[2] = b * 1.05F;
         out[3] = 1.0F;
         out[4] = 0.0F;
         out[5] = m == this.strip ? 1.0F : 0.35F;
         return;
      }

      float[] n = NORMAL[hit.face];
      float nx = px + n[0] * 0.01F;
      float ny = py + n[1] * 0.01F;
      float nz = pz + n[2] * 0.01F;
      // Grundlicht (Minecraft-Flächenschattierung)
      float amb = (hit.face == 2 ? 1.05F : (hit.face == 4 ? 0.62F : (hit.face < 2 ? 0.3F : 0.2F))) * SHADE[hit.face];
      float lr = amb * 0.95F;
      float lg = amb * 0.98F;
      float lb = amb * 1.12F;
      // Licht durch die Fenster: zum nächstgelegenen Fensterpunkt, mit Schatten
      float wx = Math.max(-WALL_X + 1.0F, Math.min(WALL_X - 1.0F, px * 0.9F));
      float wy = 3.4F;
      float wz = FRONT_Z;
      float lx = wx - nx;
      float ly = wy - ny;
      float lz = wz - nz;
      float dist = (float)Math.sqrt(lx * lx + ly * ly + lz * lz);
      lx /= dist;
      ly /= dist;
      lz /= dist;
      float lambert = n[0] * lx + n[1] * ly + n[2] * lz;
      float win = 0.0F;
      if (lambert > 0.0F) {
         Hit shadow = new Hit();
         boolean blocked = this.trace(nx, ny, nz, lx, ly, lz, shadow, dist - 0.3F);
         if (!blocked) {
            win = lambert * 2.2F / (1.0F + dist * dist / 70.0F);
         }
      }

      lr += win * this.light[0];
      lg += win * this.light[1];
      lb += win * this.light[2];
      // Leuchtleisten an der Decke (x = 0 und ±5)
      float ceilLight = 0.0F;

      for (float sx : new float[]{-7.0F, -5.0F, 0.0F, 5.0F, 7.0F}) {
         float cx = sx - nx;
         float cy = CEIL_Y - 0.05F - ny;
         float cz = 0.0F;
         float d2 = cx * cx + cy * cy;
         float d = (float)Math.sqrt(d2);
         float lam = (n[0] * cx + n[1] * cy + n[2] * cz) / Math.max(0.001F, d);
         if (lam > 0.0F) {
            ceilLight += lam * 1.1F / (1.0F + d2 / 14.0F);
         }
      }

      lr += ceilLight * 0.8F;
      lg += ceilLight * 0.86F;
      lb += ceilLight * 0.95F;
      float cr = r * lr;
      float cg = g * lg;
      float cb = b * lb;
      // spiegelnder Boden
      if (allowGloss && m.gloss > 0.0F) {
         float dot = dx * n[0] + dy * n[1] + dz * n[2];
         float rx = dx - 2.0F * dot * n[0];
         float ry = dy - 2.0F * dot * n[1];
         float rz = dz - 2.0F * dot * n[2];
         float[] refl = new float[6];
         this.shade(nx, ny, nz, rx, ry, rz, false, refl);
         if (refl[3] < 0.5F) {
            // Fenster in der Spiegelung: Licht des Planeten
            refl[0] = 0.1F + this.light[0] * 0.25F;
            refl[1] = 0.08F + this.light[1] * 0.2F;
            refl[2] = 0.12F + this.light[2] * 0.2F;
         }

         float fres = m.gloss * (0.18F + 0.82F * (float)Math.pow(1.0F - Math.abs(dot), 4.0));
         cr += (refl[0] - cr) * fres;
         cg += (refl[1] - cg) * fres;
         cb += (refl[2] - cb) * fres;
         win += refl[4] * fres;
      }

      out[0] = cr;
      out[1] = cg;
      out[2] = cb;
      out[3] = 1.0F;
      out[4] = win;
      out[5] = ceilLight;
   }

   /** Rendert alles (einige hundert Millisekunden – im Hintergrund aufrufen). */
   public void render() {
      float[] dir = new float[3];
      float[] out = new float[6];

      for (int y = 0; y < VH; y++) {
         for (int x = 0; x < VW; x++) {
            CAM.ray(x + 0.5F, y + 0.5F, dir);
            this.shade(CAM.x, CAM.y, CAM.z, dir[0], dir[1], dir[2], true, out);
            int i = y * VW + x;
            // sanfte Tonkurve, leicht kühler Look
            float r = tone(out[0]);
            float g = tone(out[1]);
            float b = tone(out[2]);
            this.image.px[i] = Tex.argb(Tex.clamp255(out[3] * 255.0F), r * 255.0F, g * 255.0F, b * 255.0F);
            this.windowLight.px[i] = Tex.clamp255(Math.min(1.0F, out[4] * 0.9F) * 255.0F * out[3]) << 24 | 16777215;
            this.alarmLight.px[i] = Tex.clamp255(Math.min(1.0F, out[5] * 0.7F) * 255.0F * out[3]) << 24 | 16777215;
         }
      }
   }

   private static float tone(float v) {
      v = Math.max(0.0F, v);
      return v / (1.0F + v * 0.35F) * 1.12F;
   }

   // ------------------------------------------------------------------ Hilfstypen

   interface Painter {
      Mat paint(int face, float x, float y, float z);
   }

   static final class Mat {
      final Tex tex;
      final float r;
      final float g;
      final float b;
      final boolean emissive;
      final float gloss;

      Mat(Tex tex, float r, float g, float b, boolean emissive, float gloss) {
         this.tex = tex;
         this.r = r;
         this.g = g;
         this.b = b;
         this.emissive = emissive;
         this.gloss = gloss;
      }
   }

   private static final class Box {
      final float x0;
      final float y0;
      final float z0;
      final float x1;
      final float y1;
      final float z1;
      final Painter painter;

      Box(float x0, float y0, float z0, float x1, float y1, float z1, Painter painter) {
         this.x0 = x0;
         this.y0 = y0;
         this.z0 = z0;
         this.x1 = x1;
         this.y1 = y1;
         this.z1 = z1;
         this.painter = painter;
      }
   }
}
