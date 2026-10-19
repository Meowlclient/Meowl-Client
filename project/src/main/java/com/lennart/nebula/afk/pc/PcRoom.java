package com.lennart.nebula.afk.pc;

import com.lennart.nebula.afk.space.Cam;
import com.lennart.nebula.afk.space.Tex;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Gaming-Zimmer aus echten Minecraft-Texturen, einmal per Raytracing gerendert (Minecraft-Flächenschattierung,
 * farbige Lichter mit Schatten, leuchtende LED-Leisten). Die drei Bildschirme bleiben als Masken frei: für jeden ihrer
 * Pixel wird gemerkt, welche Stelle des Bildschirms dort zu sehen ist – so lassen sich Discord, das Spiel und Spotify
 * perspektivisch richtig hineinsetzen.
 */
public final class PcRoom {
   public static final int VW = 960;
   public static final int VH = 540;
   public static final Cam CAM = new Cam(VW, VH, 0.0F, 1.72F, -1.2F, -15.0F, 60.0F);
   public static final int SCREEN_LEFT = 1;
   public static final int SCREEN_MID = 2;
   public static final int SCREEN_RIGHT = 3;

   public final Tex image = new Tex(VW, VH);
   /** Alpha = Anteil LED-Licht (wird live eingefärbt). */
   public final Tex ledLight = new Tex(VW, VH);
   /** Alpha = Tastatur-Beleuchtung (live RGB-Welle). */
   public final Tex keyLight = new Tex(VW, VH);
   /** Bildschirm-Nr. je Pixel (0 = keiner) und Lage darauf (0..1). */
   public final byte[] screen = new byte[VW * VH];
   public final float[] su = new float[VW * VH];
   public final float[] sv = new float[VW * VH];
   /** Umriss des mittleren Bildschirms im Bild: x0, y0, x1, y1. */
   public final int[] midRect = new int[4];
   /** Ecken der Tastatur und Maus im Bild (für die Hände): kx, ky, mx, my. */
   public final float[] handTargets = new float[4];

   private final Map<String, Tex> tex;
   private final PcTheme theme;
   private final List<Box> boxes = new ArrayList<>();
   private final List<float[]> lights = new ArrayList<>();

   public PcRoom(Map<String, Tex> textures, PcTheme theme) {
      this.tex = textures;
      this.theme = theme;
      this.build();
   }

   // ================================================================== Material

   /** Material: Textur, Farbfaktor, leuchtend, Masken. */
   record Mat(Tex tex, float r, float g, float b, boolean emissive, int screenId, boolean led, boolean keys) {
      Mat with(float r, float g, float b) {
         return new Mat(this.tex, r, g, b, this.emissive, this.screenId, this.led, this.keys);
      }
   }

   private Tex t(String path) {
      Tex t = this.tex.get(path);
      return t != null ? t.firstFrame() : Tex.solid(0xFF808080);
   }

   private Mat block(String name) {
      return new Mat(this.t("block/" + name), 1.0F, 1.0F, 1.0F, false, 0, false, false);
   }

   private Mat glow(String name, float r, float g, float b) {
      return new Mat(this.t("block/" + name), r, g, b, true, 0, false, false);
   }

   private Mat led(int argb, float power) {
      return new Mat(Tex.solid(0xFFFFFFFF), (argb >> 16 & 255) / 255.0F * power, (argb >> 8 & 255) / 255.0F * power, (argb & 255) / 255.0F * power, true, 0, true, false);
   }

   private static Mat screenMat(int id) {
      return new Mat(Tex.solid(0xFF000000), 1.0F, 1.0F, 1.0F, true, id, false, false);
   }

   // ================================================================== Aufbau

   private static final float DESK_Y = 1.0F;
   private static final float WALL_Z = 2.8F;
   private static final float SIDE_X = 3.3F;

   private void build() {
      PcTheme th = this.theme;
      Mat wall = this.block(th.wall);
      Mat wall2 = this.block(th.wall2);
      Mat desk = this.block(th.desk);
      Mat dark = this.block("black_concrete").with(0.9F, 0.9F, 0.95F);
      Mat plastic = this.block("gray_concrete").with(0.5F, 0.5F, 0.55F);
      // Raum: Boden, Rückwand, Seitenwände, Decke
      this.add(-SIDE_X, -0.5F, -2.0F, SIDE_X, 0.0F, WALL_Z, 0.0F, (f, x, y, z) -> this.block(th.floor));
      this.add(-SIDE_X, 0.0F, WALL_Z, SIDE_X, 4.0F, WALL_Z + 0.5F, 0.0F, (f, x, y, z) -> this.backWall(x, y, wall, wall2));
      this.add(-SIDE_X - 0.5F, 0.0F, -2.0F, -SIDE_X, 4.0F, WALL_Z, 0.0F, (f, x, y, z) -> this.sideWall(z, y, wall, wall2));
      this.add(SIDE_X, 0.0F, -2.0F, SIDE_X + 0.5F, 4.0F, WALL_Z, 0.0F, (f, x, y, z) -> this.sideWall(z, y, wall, wall2));
      this.add(-SIDE_X, 3.2F, -2.0F, SIDE_X, 3.7F, WALL_Z, 0.0F, (f, x, y, z) -> wall2);
      // Schreibtisch mit LED-Leiste an der Vorderkante
      this.add(-3.0F, DESK_Y - 0.2F, -0.25F, 3.0F, DESK_Y, 2.2F, 0.0F, (f, x, y, z) -> desk);
      this.add(-3.0F, 0.0F, -0.15F, 3.0F, DESK_Y - 0.2F, 0.0F, 0.0F, (f, x, y, z) -> desk.with(0.7F, 0.7F, 0.7F));
      this.add(-2.95F, DESK_Y - 0.23F, -0.27F, 2.95F, DESK_Y - 0.2F, -0.24F, 0.0F, (f, x, y, z) -> this.led(th.accent, 1.5F));
      this.light(0.0F, DESK_Y - 0.4F, -0.5F, th.accent, 1.2F, false);
      // LED-Leiste an der Rückwand hinter den Bildschirmen
      this.add(-3.0F, DESK_Y + 0.02F, WALL_Z - 0.08F, 3.0F, DESK_Y + 0.08F, WALL_Z, 0.0F, (f, x, y, z) -> this.led(th.accent2, 2.4F));
      this.light(-1.6F, DESK_Y + 0.2F, WALL_Z - 0.3F, th.accent2, 1.1F, false);
      this.light(1.6F, DESK_Y + 0.2F, WALL_Z - 0.3F, th.accent2, 1.1F, false);
      // Bildschirme
      this.monitor(0.0F, 1.64F, 1.6F, 1.6F, 0.9F, 0.0F, SCREEN_MID, dark, plastic);
      this.monitor(-1.48F, 1.62F, 1.3F, 1.3F, 0.76F, -33.0F, SCREEN_LEFT, dark, plastic);
      this.monitor(1.48F, 1.62F, 1.3F, 1.3F, 0.76F, 33.0F, SCREEN_RIGHT, dark, plastic);
      this.light(0.0F, 1.6F, 1.1F, 0xFF9FB8FF, 0.9F, false);
      // Tastatur (Tasten leuchten) und Maus mit Mauspad
      this.add(-0.62F, DESK_Y, 0.08F, 0.62F, DESK_Y + 0.05F, 0.48F, -2.0F, (f, x, y, z) -> f == 3 ? this.keys(x, z, dark) : dark);
      this.add(0.82F, DESK_Y, 0.02F, 1.42F, DESK_Y + 0.01F, 0.55F, 0.0F, (f, x, y, z) -> dark.with(0.6F, 0.6F, 0.65F));
      this.add(1.05F, DESK_Y + 0.01F, 0.2F, 1.17F, DESK_Y + 0.06F, 0.38F, 0.0F, (f, x, y, z) -> f == 3 && z > 0.33F ? this.led(th.accent, 1.5F) : plastic.with(0.4F, 0.4F, 0.45F));
      this.light(0.0F, DESK_Y + 0.3F, 0.25F, th.accent, 0.35F, false);
      // PC rechts mit Glasseite und Lüftern
      this.pcCase(2.28F, 1.3F, dark);
      // Regale an der Rückwand mit Deko, Laternen
      this.shelf(-2.9F, -1.6F, 2.25F, dark);
      this.shelf(1.6F, 2.9F, 2.25F, dark);
      this.deco();
      this.lantern(-1.2F, 2.75F, WALL_Z - 0.4F);
      this.lantern(1.2F, 2.75F, WALL_Z - 0.4F);
      // farbiges Raumlicht links und rechts (wie RGB-Beleuchtung)
      this.light(-2.9F, 1.6F, 0.6F, th.accent, 0.9F, false);
      this.light(2.9F, 1.6F, 0.6F, th.accent2, 0.9F, false);
      // warmes Licht von hinten (wie eine Schreibtischlampe), Deko auf dem Tisch
      this.light(0.0F, 2.3F, -0.7F, 0xFFFFC890, 0.55F, false);
      this.cozy();
      // Grundlicht
      this.light(0.0F, 2.6F, -0.8F, th.ambient, 0.38F, false);
      // Kamera-Ziele der Hände (Tastatur links, Maus)
      float[] p = new float[3];
      CAM.project(-0.3F, DESK_Y + 0.05F, 0.3F, p);
      this.handTargets[0] = p[0];
      this.handTargets[1] = p[1];
      CAM.project(1.11F, DESK_Y + 0.06F, 0.3F, p);
      this.handTargets[2] = p[0];
      this.handTargets[3] = p[1];
   }

   private Mat backWall(float x, float y, Mat wall, Mat wall2) {
      if (this.theme.window && y > 1.95F && y < 2.95F && Math.abs(x) < 2.7F) {
         // Fenster nach draußen (Rahmen aus Holz)
         float fx = frac(x / 0.9F);
         boolean frame = y < 2.02F || y > 2.88F || fx < 0.05F || Math.abs(x) > 2.62F;
         return frame ? this.block("stripped_spruce_log") : this.sky(x, y);
      } else {
         // Säulen alle 2 Blöcke aus dem zweiten Block
         return frac((x + 0.5F) / 2.0F) < 0.25F ? wall2 : wall;
      }
   }

   /** Blick nach draußen: Himmel, Berge, Bäume (als leuchtende Fläche, damit es hell bleibt). */
   private Mat sky(float x, float y) {
      PcTheme th = this.theme;
      float h = 2.2F + 0.18F * (float)Math.sin(x * 2.1F) + 0.1F * (float)Math.sin(x * 5.3F + 1.0F);
      if (y < h) {
         boolean tree = frac(x * 1.7F) < 0.35F && y < 2.15F + 0.12F * (float)Math.sin(x * 9.0F);
         if (tree) {
            Tex leaves = this.t(th.snow ? "block/spruce_leaves" : "block/oak_leaves");
            return th.snow ? new Mat(leaves, 0.35F, 0.55F, 0.45F, true, 0, false, false) : new Mat(leaves, 0.35F, 0.75F, 0.3F, true, 0, false, false);
         }

         return th.snow ? new Mat(this.t("block/snow"), 0.85F, 0.9F, 1.0F, true, 0, false, false) : new Mat(this.t("block/stone"), 0.55F, 0.65F, 0.8F, true, 0, false, false);
      } else {
         float k = (y - 2.0F) / 0.9F;
         int c = Tex.mix(th.sky2, th.sky1, Math.max(0.0F, Math.min(1.0F, k)));
         return new Mat(Tex.solid(c), 1.0F, 1.0F, 1.0F, true, 0, false, false);
      }
   }

   private Mat sideWall(float z, float y, Mat wall, Mat wall2) {
      return frac(z / 2.0F) < 0.2F ? wall2 : wall;
   }

   /** Tasten: dunkle Kappen, dazwischen leuchtet es (Maske für die RGB-Welle). */
   private Mat keys(float x, float z, Mat dark) {
      float kx = frac(x / 0.07F);
      float kz = frac(z / 0.075F);
      boolean gap = kx < 0.16F || kz < 0.18F || Math.abs(x) > 0.585F || z < 0.11F || z > 0.45F;
      return gap ? new Mat(Tex.solid(0xFFFFFFFF), 0.25F, 0.25F, 0.3F, true, 0, false, true) : dark.with(0.6F, 0.6F, 0.66F);
   }

   private void monitor(float cx, float cy, float cz, float w, float h, float yaw, int id, Mat dark, Mat plastic) {
      float b = 0.035F;
      // Rahmen und Bildschirm (Vorderseite = −z im lokalen Raum)
      this.addRot(cx, cy, cz, -w / 2 - b, -h / 2 - b, -0.03F, w / 2 + b, h / 2 + b, 0.04F, yaw, (f, x, y, z) -> {
         if (f == 4 && Math.abs(x) < w / 2 && Math.abs(y) < h / 2) {
            return screenMat(id);
         } else {
            return dark;
         }
      });
      // Fuß
      this.addRot(cx, cy, cz, -0.04F, DESK_Y - cy, 0.06F, 0.04F, -h / 2 - b, 0.12F, yaw, (f, x, y, z) -> plastic);
      this.addRot(cx, cy, cz, -0.25F, DESK_Y - cy, -0.05F, 0.25F, DESK_Y - cy + 0.03F, 0.25F, yaw, (f, x, y, z) -> plastic);
   }

   private void pcCase(float x0, float z0, Mat dark) {
      PcTheme th = this.theme;
      float x1 = x0 + 0.5F;
      float z1 = z0 + 0.9F;
      float y0 = DESK_Y;
      float y1 = DESK_Y + 1.25F;
      this.add(x0, y0, z0, x1, y1, z1, 0.0F, (f, x, y, z) -> {
         if (f == 0) {
            // Glasseite mit drei Lüftern
            float best = 9.0F;

            for (int i = 0; i < 3; i++) {
               float fy = y0 + 0.25F + i * 0.38F;
               float fz = (z0 + z1) / 2.0F;
               float d = (float)Math.hypot(y - fy, z - fz);
               best = Math.min(best, Math.abs(d - 0.14F));
            }

            if (best < 0.018F) {
               return this.led(th.accent, 2.4F);
            } else {
               return new Mat(this.t("block/tinted_glass"), 0.5F, 0.5F, 0.55F, false, 0, false, false);
            }
         } else if (f == 4) {
            // Vorderseite: Leuchtstreifen
            return Math.abs(x - (x0 + 0.05F)) < 0.015F ? this.led(th.accent2, 2.0F) : dark;
         } else {
            return dark;
         }
      });

      for (int i = 0; i < 3; i++) {
         this.light(x0 - 0.15F, y0 + 0.25F + i * 0.38F, (z0 + z1) / 2.0F, th.accent, 0.5F, false);
      }
   }

   private void shelf(float x0, float x1, float y, Mat dark) {
      this.add(x0, y - 0.06F, WALL_Z - 0.45F, x1, y, WALL_Z, 0.0F, (f, x, yy, z) -> this.block(this.theme.desk));
      // LED unter dem Regal
      this.add(x0 + 0.05F, y - 0.08F, WALL_Z - 0.1F, x1 - 0.05F, y - 0.06F, WALL_Z - 0.05F, 0.0F, (f, x, yy, z) -> this.led(this.theme.accent2, 1.6F));
   }

   /** Gemütliche Deko auf dem Tisch: Pflanze im Topf, kleine Leuchte, Becher. */
   private void cozy() {
      Mat pot = this.block("terracotta");
      Mat leaves = new Mat(this.t("block/azalea_leaves"), 0.8F, 1.0F, 0.75F, false, 0, false, false);
      this.add(-1.12F, DESK_Y, 0.28F, -0.94F, DESK_Y + 0.16F, 0.46F, 0.0F, (f, x, y, z) -> pot, 0.18F);
      this.add(-1.16F, DESK_Y + 0.16F, 0.24F, -0.9F, DESK_Y + 0.4F, 0.5F, 0.0F, (f, x, y, z) -> leaves, 0.26F);
      // kleine warme Leuchte
      Mat lamp = new Mat(this.t("block/ochre_froglight_side"), 1.3F, 1.15F, 0.95F, true, 0, false, false);
      Mat stand = this.block("iron_bars").with(0.4F, 0.4F, 0.4F);
      this.add(-1.62F, DESK_Y, 0.55F, -1.58F, DESK_Y + 0.26F, 0.59F, 0.0F, (f, x, y, z) -> stand);
      this.add(-1.7F, DESK_Y + 0.26F, 0.47F, -1.5F, DESK_Y + 0.42F, 0.67F, 0.0F, (f, x, y, z) -> lamp, 0.2F);
      this.light(-1.6F, DESK_Y + 0.35F, 0.4F, 0xFFFFB060, 1.1F, true);
      // Becher
      Mat mug = this.block("white_terracotta");
      this.add(1.55F, DESK_Y, 0.25F, 1.67F, DESK_Y + 0.14F, 0.37F, 0.0F, (f, x, y, z) -> f == 3 ? this.block("brown_terracotta").with(0.6F, 0.45F, 0.3F) : mug, 0.12F);
   }

   /** Würfel-Deko auf den Regalen (z.B. Bücherregal, Kürbis, Pflanze) – je Stil andere Blöcke. */
   private void deco() {
      String[] left = this.theme.decoLeft;
      String[] right = this.theme.decoRight;
      float s = 0.36F;

      for (int i = 0; i < left.length; i++) {
         this.cube(-2.8F + i * (s + 0.06F), 2.25F, WALL_Z - 0.4F, s, left[i]);
      }

      for (int i = 0; i < right.length; i++) {
         this.cube(1.7F + i * (s + 0.06F), 2.25F, WALL_Z - 0.4F, s, right[i]);
      }

      // Stil-Extras auf dem Boden/an der Wand
      if (this.theme.extra != null && this.theme.extraGlow) {
         Mat m = this.theme.extraGlow ? this.glow(this.theme.extra, 1.3F, 1.1F, 0.9F) : this.block(this.theme.extra);
         this.add(-3.25F, 0.0F, 0.4F, -2.6F, 1.6F, 1.4F, 0.0F, (f, x, y, z) -> m);
         if (this.theme.extraGlow) {
            this.light(-2.4F, 1.0F, 0.9F, 0xFFFF9030, 1.4F, false);
         }
      }
   }

   private void cube(float x, float y, float z, float s, String name) {
      boolean glow = name.contains("lantern") || name.contains("lamp_on") || name.equals("glowstone") || name.contains("froglight") || name.equals("jack_o_lantern")
         || name.equals("sea_lantern") || name.equals("shroomlight");
      String side = name;
      String top = name;
      String front = name;
      if (name.equals("bookshelf")) {
         top = "oak_planks";
      } else if (name.equals("jack_o_lantern") || name.equals("carved_pumpkin")) {
         side = "pumpkin_side";
         top = "pumpkin_top";
      } else if (name.equals("crafting_table")) {
         front = "crafting_table_front";
         side = "crafting_table_side";
         top = "crafting_table_top";
      }

      Mat ms = glow ? this.glow(side, 1.2F, 1.1F, 1.0F) : this.block(side);
      Mat mt = this.block(top);
      Mat mf = glow ? this.glow(front, 1.2F, 1.1F, 1.0F) : this.block(front);
      this.add(x, y, z, x + s, y + s, z + s, 0.0F, (f, xx, yy, zz) -> f == 3 || f == 2 ? mt : (f == 4 ? mf : ms), s);
      if (glow) {
         this.light(x + s / 2, y + s / 2, z - 0.2F, 0xFFFFB050, 0.8F, false);
      }
   }

   /** Hängende Leuchte: Kette und ein leuchtender Block (Froschlicht, Seelaterne …). */
   private void lantern(float x, float y, float z) {
      String name = this.theme.lantern;
      Mat chain = this.block("iron_bars").with(0.5F, 0.5F, 0.5F);
      this.add(x - 0.012F, y + 0.24F, z - 0.012F, x + 0.012F, 3.2F, z + 0.012F, 0.0F, (f, xx, yy, zz) -> chain);
      Mat m = new Mat(this.t("block/" + name), 1.25F, 1.2F, 1.15F, true, 0, false, false);
      this.add(x - 0.12F, y, z - 0.12F, x + 0.12F, y + 0.24F, z + 0.12F, 0.0F, (f, xx, yy, zz) -> m, 0.24F);
      this.light(x, y + 0.1F, z - 0.3F, this.t("block/" + name).average(), 1.4F, true);
   }
   private void light(float x, float y, float z, int argb, float power, boolean shadows) {
      this.lights.add(new float[]{x, y, z, (argb >> 16 & 255) / 255.0F * power, (argb >> 8 & 255) / 255.0F * power, (argb & 255) / 255.0F * power, shadows ? 1 : 0});
   }

   private static float frac(float v) {
      return v - (float)Math.floor(v);
   }

   private void add(float x0, float y0, float z0, float x1, float y1, float z1, float unused, Painter p) {
      this.add(x0, y0, z0, x1, y1, z1, unused, p, 0.5F);
   }

   /** Achsparalleler Quader; {@code texSize} = wie viele Blöcke eine Textur breit ist. */
   private void add(float x0, float y0, float z0, float x1, float y1, float z1, float unused, Painter p, float texSize) {
      Box b = new Box();
      b.cx = 0.0F;
      b.cy = 0.0F;
      b.cz = 0.0F;
      b.x0 = x0;
      b.y0 = y0;
      b.z0 = z0;
      b.x1 = x1;
      b.y1 = y1;
      b.z1 = z1;
      b.painter = p;
      b.texSize = unused < 0.0F ? 0.5F : texSize;
      this.boxes.add(b);
   }

   /** Um die Hochachse gedrehter Quader (lokale Maße um den Mittelpunkt cx, cy, cz). */
   private void addRot(float cx, float cy, float cz, float x0, float y0, float z0, float x1, float y1, float z1, float yawDeg, Painter p) {
      Box b = new Box();
      b.cx = cx;
      b.cy = cy;
      b.cz = cz;
      b.x0 = x0;
      b.y0 = y0;
      b.z0 = z0;
      b.x1 = x1;
      b.y1 = y1;
      b.z1 = z1;
      double a = Math.toRadians(yawDeg);
      b.cos = (float)Math.cos(a);
      b.sin = (float)Math.sin(a);
      b.rotated = yawDeg != 0.0F;
      b.local = true;
      b.painter = p;
      b.texSize = 1.0F;
      this.boxes.add(b);
   }

   // ================================================================== Raytracing

   interface Painter {
      Mat paint(int face, float x, float y, float z);
   }

   private static final class Box {
      float cx;
      float cy;
      float cz;
      float x0;
      float y0;
      float z0;
      float x1;
      float y1;
      float z1;
      float cos = 1.0F;
      float sin = 0.0F;
      boolean rotated;
      boolean local;
      float texSize = 1.0F;
      Painter painter;
   }

   private static final class Hit {
      float t;
      int face;
      Box box;
      float lx;
      float ly;
      float lz;
   }

   private static final float[][] NORMAL = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};

   private boolean trace(float ox, float oy, float oz, float dx, float dy, float dz, float maxT, Hit hit) {
      hit.t = maxT;
      hit.box = null;

      for (Box b : this.boxes) {
         float px = ox - b.cx;
         float py = oy - b.cy;
         float pz = oz - b.cz;
         float qx = dx;
         float qz = dz;
         if (b.rotated) {
            // in den lokalen Raum drehen (−yaw)
            float rx = px * b.cos - pz * b.sin;
            float rz = px * b.sin + pz * b.cos;
            px = rx;
            pz = rz;
            rx = dx * b.cos - dz * b.sin;
            rz = dx * b.sin + dz * b.cos;
            qx = rx;
            qz = rz;
         }

         float tx0 = (b.x0 - px) / qx;
         float tx1 = (b.x1 - px) / qx;
         int fx = qx > 0.0F ? 0 : 1;
         if (tx0 > tx1) {
            float s = tx0;
            tx0 = tx1;
            tx1 = s;
         }

         float ty0 = (b.y0 - py) / dy;
         float ty1 = (b.y1 - py) / dy;
         int fy = dy > 0.0F ? 2 : 3;
         if (ty0 > ty1) {
            float s = ty0;
            ty0 = ty1;
            ty1 = s;
         }

         float tz0 = (b.z0 - pz) / qz;
         float tz1 = (b.z1 - pz) / qz;
         int fz = qz > 0.0F ? 4 : 5;
         if (tz0 > tz1) {
            float s = tz0;
            tz0 = tz1;
            tz1 = s;
         }

         float near = tx0;
         int face = fx;
         if (ty0 > near) {
            near = ty0;
            face = fy;
         }

         if (tz0 > near) {
            near = tz0;
            face = fz;
         }

         float far = Math.min(tx1, Math.min(ty1, tz1));
         if (near <= far && near > 1.0E-4F && near < hit.t) {
            hit.t = near;
            hit.face = face;
            hit.box = b;
            hit.lx = px + qx * near;
            hit.ly = py + dy * near;
            hit.lz = pz + qz * near;
         }
      }

      return hit.box != null;
   }

   /** Ergebnis je Pixel: Farbe (r, g, b), LED-Anteil, Tastatur-Anteil, Bildschirm-Nr., u, v. */
   private void shade(float ox, float oy, float oz, float dx, float dy, float dz, float[] out) {
      Hit hit = new Hit();
      out[3] = 0.0F;
      out[4] = 0.0F;
      out[5] = 0.0F;
      if (!this.trace(ox, oy, oz, dx, dy, dz, 60.0F, hit)) {
         out[0] = out[1] = out[2] = 0.02F;
         return;
      }

      Box b = hit.box;
      Mat m = b.painter.paint(hit.face, hit.lx, hit.ly, hit.lz);
      if (m.screenId() != 0) {
         out[5] = m.screenId();
         // lokale Lage auf dem Bildschirm (Vorderseite, −z): u nach rechts (Betrachter), v nach unten
         out[6] = 0.5F + hit.lx / (b.x1 - b.x0 - 0.07F);
         out[7] = 0.5F - hit.ly / (b.y1 - b.y0 - 0.07F);
         out[0] = out[1] = out[2] = 0.0F;
         return;
      }

      // Texel: 16 je Textur-Einheit, je Fläche ausgerichtet
      float ts = 16.0F / b.texSize;
      float u;
      float v;
      switch (hit.face) {
         case 0:
         case 1:
            u = hit.lz;
            v = -hit.ly;
            break;
         case 2:
         case 3:
            u = hit.lx;
            v = hit.lz;
            break;
         default:
            u = hit.lx;
            v = -hit.ly;
      }

      int texel = m.tex().at((int)Math.floor(u * ts), (int)Math.floor(v * ts));
      if ((texel >>> 24) < 40 && !m.emissive()) {
         // durchsichtige Texel (Glas, Laub): dahinter weiter suchen
         float tt = hit.t + 0.002F;
         this.shade(ox + dx * tt, oy + dy * tt, oz + dz * tt, dx, dy, dz, out);
         return;
      }

      float r = (texel >> 16 & 255) / 255.0F * m.r();
      float g = (texel >> 8 & 255) / 255.0F * m.g();
      float bl = (texel & 255) / 255.0F * m.b();
      if (m.emissive()) {
         out[0] = r;
         out[1] = g;
         out[2] = bl;
         out[6] = -1.0F;
         out[3] = m.led() ? 1.0F : 0.0F;
         out[4] = m.keys() ? 1.0F : 0.0F;
         return;
      }

      // Normale in Weltrichtung
      float[] n0 = NORMAL[hit.face];
      float nx = n0[0];
      float ny = n0[1];
      float nz = n0[2];
      if (b.rotated) {
         float rx = nx * b.cos + nz * b.sin;
         float rz = -nx * b.sin + nz * b.cos;
         nx = rx;
         nz = rz;
      }

      float hx = ox + dx * hit.t;
      float hy = oy + dy * hit.t;
      float hz = oz + dz * hit.t;
      // Minecraft-Flächenschattierung als Grundlicht, dazu Umgebungsverdeckung (dunkle Ecken)
      float shadeMc = hit.face == 3 ? 1.0F : (hit.face == 2 ? 0.5F : (hit.face < 2 ? 0.6F : 0.8F));
      float ao = this.occlusion(hx + nx * 0.004F, hy + ny * 0.004F, hz + nz * 0.004F, nx, ny, nz);
      float amb = this.theme.ambientLevel * shadeMc * ao;
      float lr = amb;
      float lg = amb;
      float lb = amb * 1.05F;
      float led = 0.0F;

      for (float[] L : this.lights) {
         float lx = L[0] - hx;
         float ly = L[1] - hy;
         float lz = L[2] - hz;
         float d2 = lx * lx + ly * ly + lz * lz;
         float d = (float)Math.sqrt(d2);
         float lam = (nx * lx + ny * ly + nz * lz) / d;
         if (lam > 0.0F) {
            if (L[6] > 0.5F) {
               Hit sh = new Hit();
               if (this.trace(hx + nx * 0.01F, hy + ny * 0.01F, hz + nz * 0.01F, lx / d, ly / d, lz / d, d - 0.2F, sh)) {
                  continue;
               }
            }

            float k = lam * (0.4F + 0.6F * shadeMc) * (0.45F + 0.55F * ao) / (1.0F + d2 * 0.35F);
            lr += L[3] * k;
            lg += L[4] * k;
            lb += L[5] * k;
         }
      }

      out[0] = r * lr;
      out[1] = g * lg;
      out[2] = bl * lb;
      out[3] = led;
   }

   private static final int AO_SAMPLES = 10;

   /** Anteil freier Richtungen um einen Punkt (1 = frei, kleiner in Ecken und unter Gegenständen). */
   private float occlusion(float x, float y, float z, float nx, float ny, float nz) {
      // Tangenten zur Normalen
      float ax = Math.abs(ny) < 0.9F ? 0.0F : 1.0F;
      float ay = Math.abs(ny) < 0.9F ? 1.0F : 0.0F;
      float t1x = ny * 0.0F - nz * ay;
      float t1y = nz * ax - nx * 0.0F;
      float t1z = nx * ay - ny * ax;
      float l1 = (float)Math.sqrt(t1x * t1x + t1y * t1y + t1z * t1z);
      t1x /= l1;
      t1y /= l1;
      t1z /= l1;
      float t2x = ny * t1z - nz * t1y;
      float t2y = nz * t1x - nx * t1z;
      float t2z = nx * t1y - ny * t1x;
      int hits = 0;
      Hit h = new Hit();

      for (int i = 0; i < AO_SAMPLES; i++) {
         double a = i * 2.399963;
         float r = (float)Math.sqrt((i + 0.5F) / AO_SAMPLES);
         float ca = (float)Math.cos(a) * r;
         float sa = (float)Math.sin(a) * r;
         float up = (float)Math.sqrt(Math.max(0.0F, 1.0F - r * r));
         float dx = nx * up + t1x * ca + t2x * sa;
         float dy = ny * up + t1y * ca + t2y * sa;
         float dz = nz * up + t1z * ca + t2z * sa;
         if (this.trace(x, y, z, dx, dy, dz, 0.42F, h)) {
            hits++;
         }
      }

      return 1.0F - 0.7F * hits / AO_SAMPLES;
   }

   /** Rendert das Zimmer (parallel, im Hintergrund aufrufen). */
   public void render() {
      float[] lin = new float[VW * VH * 3];
      float[] glow = new float[VW * VH * 3];
      IntStream.range(0, VH).parallel().forEach(y -> {
         float[] dir = new float[3];
         float[] out = new float[8];

         for (int x = 0; x < VW; x++) {
            CAM.ray(x + 0.5F, y + 0.5F, dir);
            out[5] = 0.0F;
            out[6] = 0.0F;
            this.shade(CAM.x, CAM.y, CAM.z, dir[0], dir[1], dir[2], out);
            int i = y * VW + x;
            lin[i * 3] = out[0];
            lin[i * 3 + 1] = out[1];
            lin[i * 3 + 2] = out[2];
            // Leuchtendes für den Glanz drumherum (Bildschirme leuchten bläulich)
            float e = out[5] != 0.0F ? 0.45F : (out[3] > 0.0F || emissive(out) ? 1.0F : 0.0F);
            glow[i * 3] = out[5] != 0.0F ? 0.25F : out[0] * e;
            glow[i * 3 + 1] = out[5] != 0.0F ? 0.3F : out[1] * e;
            glow[i * 3 + 2] = out[5] != 0.0F ? 0.42F : out[2] * e;
            this.ledLight.px[i] = Tex.clamp255(out[3] * 255.0F) << 24 | 16777215;
            this.keyLight.px[i] = Tex.clamp255(out[4] * 255.0F) << 24 | 16777215;
            int s = Math.round(out[5]);
            this.screen[i] = (byte)s;
            if (s != 0) {
               this.su[i] = Math.max(0.0F, Math.min(0.999F, out[6]));
               this.sv[i] = Math.max(0.0F, Math.min(0.999F, out[7]));
            }
         }
      });
      // Bloom: Leuchtendes weichzeichnen und dazugeben
      blur(glow, 10);
      blur(glow, 10);

      for (int i = 0; i < VW * VH; i++) {
         // etwas wärmer und weicher (gemütlich)
         float r = (lin[i * 3] + glow[i * 3] * 1.1F) * 1.05F + 0.012F;
         float g = (lin[i * 3 + 1] + glow[i * 3 + 1] * 1.1F) * 1.0F + 0.008F;
         float b = (lin[i * 3 + 2] + glow[i * 3 + 2] * 1.1F) * 0.92F + 0.004F;
         // leichte Vignette
         int x = i % VW;
         int y = i / VW;
         float vx = (x - VW / 2.0F) / (VW / 2.0F);
         float vy = (y - VH / 2.0F) / (VH / 2.0F);
         float vig = 1.0F - 0.18F * (vx * vx * 0.6F + vy * vy);
         this.image.px[i] = Tex.argb(255, tone(r * vig) * 255.0F, tone(g * vig) * 255.0F, tone(b * vig) * 255.0F);
      }

      // Umriss des mittleren Bildschirms
      int x0 = VW;
      int y0 = VH;
      int x1 = 0;
      int y1 = 0;

      for (int i = 0; i < this.screen.length; i++) {
         if (this.screen[i] == SCREEN_MID) {
            int x = i % VW;
            int y = i / VW;
            x0 = Math.min(x0, x);
            y0 = Math.min(y0, y);
            x1 = Math.max(x1, x + 1);
            y1 = Math.max(y1, y + 1);
         }
      }

      this.midRect[0] = x0;
      this.midRect[1] = y0;
      this.midRect[2] = x1;
      this.midRect[3] = y1;
   }

   private static boolean emissive(float[] out) {
      return out[6] < 0.0F;
   }

   /** Weichzeichnen (waagerecht und senkrecht, Kastenfilter). */
   private static void blur(float[] c, int r) {
      float[] tmp = new float[c.length];

      for (int y = 0; y < VH; y++) {
         for (int ch = 0; ch < 3; ch++) {
            float sum = 0.0F;

            for (int x = -r; x <= r; x++) {
               sum += c[(y * VW + Math.max(0, Math.min(VW - 1, x))) * 3 + ch];
            }

            for (int x = 0; x < VW; x++) {
               tmp[(y * VW + x) * 3 + ch] = sum / (2 * r + 1);
               sum += c[(y * VW + Math.min(VW - 1, x + r + 1)) * 3 + ch] - c[(y * VW + Math.max(0, x - r)) * 3 + ch];
            }
         }
      }

      for (int x = 0; x < VW; x++) {
         for (int ch = 0; ch < 3; ch++) {
            float sum = 0.0F;

            for (int y = -r; y <= r; y++) {
               sum += tmp[(Math.max(0, Math.min(VH - 1, y)) * VW + x) * 3 + ch];
            }

            for (int y = 0; y < VH; y++) {
               c[(y * VW + x) * 3 + ch] = sum / (2 * r + 1);
               sum += tmp[(Math.min(VH - 1, y + r + 1) * VW + x) * 3 + ch] - tmp[(Math.max(0, y - r) * VW + x) * 3 + ch];
            }
         }
      }
   }

   private static float tone(float v) {
      v = Math.max(0.0F, v);
      return Math.min(1.0F, v / (1.0F + v * 0.55F) * 1.2F);
   }

   /** Inhalt eines Bildschirms (Bild {@code content}) perspektivisch in das Zimmerbild setzen. */
   public void paintScreen(Tex target, int id, Tex content, float bright) {
      for (int i = 0; i < this.screen.length; i++) {
         if (this.screen[i] == id) {
            int c = content.get((int)(this.su[i] * content.w), (int)(this.sv[i] * content.h));
            int r = Math.min(255, Math.round((c >> 16 & 255) * bright));
            int g = Math.min(255, Math.round((c >> 8 & 255) * bright));
            int b = Math.min(255, Math.round((c & 255) * bright));
            target.px[i] = 0xFF000000 | r << 16 | g << 8 | b;
         }
      }
   }
}
