package com.lennart.nebula.wings;

import com.lennart.nebula.util.WorldRender;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Flügel im Modellraum des Spieler-Körpers (1 = 1 Pixel, y nach unten, Rücken bei +z). Große Flügel hoch am Rücken,
 * die sich über den Kopf wölben, mit dunkler Pixel-Kontur: Federflügel (Schichten aus Federn), Hautflügel (Knochen
 * mit gewellter Flughaut), Kristall- und Steinflügel. Jeder Stil wird zweimal gezeichnet: fest und leuchtend (additiv).
 */
public final class WingsModel {
   public static final String SERAPH = "Seraph";
   public static final String BUTTERFLY = "Schmetterling";
   public static final String DRAGONFLY = "Libelle";
   public static final String HELL_LORD = "Höllenfürst";
   public static final String BLACK = "Schwarz";
   public static final String PHOENIX = "Phönix";
   public static final String FROST = "Frost";
   public static final String DEMON = "Dämon";
   public static final String DEMON_BLACK = "Dämon Schwarz";
   public static final String DEVIL = "Teufel";
   public static final String DRAGON = "Drache";
   public static final String DRAGON_BLACK = "Drache Schwarz";
   public static final String BABY_DRAGON = "Babydrache";
   public static final String GALAXY = "Galaxie";
   public static final String WEB = "Netz";
   public static final String CRYSTAL = "Kristall";
   public static final String EARTH = "Erd-Elementar";
   public static final String NEON = "Neon";
   public static final String[] STYLES = new String[]{
      SERAPH, BUTTERFLY, DRAGONFLY, HELL_LORD, BLACK, PHOENIX, FROST, NEON, DEMON, DEMON_BLACK, DEVIL, DRAGON, DRAGON_BLACK, BABY_DRAGON, GALAXY, WEB, CRYSTAL, EARTH
   };

   private WingsModel() {
   }

   /** Bekannter Stilname (alte Namen werden umgeleitet). */
   public static String known(String style) {
      for (String s : STYLES) {
         if (s.equals(style)) {
            return s;
         }
      }

      return switch (style == null ? "" : style) {
         case "Seelenfeuer", "Prisma" -> FROST;
         case "Tod", "Leere", "Gotisch", "Ranken" -> BLACK;
         case "Engel", "Engel Rosa", "Engel Gold", "Uhrwerk", "Origami" -> SERAPH;
         case "Blitz", "Hologramm", "Mech", "Klingen" -> NEON;
         case "Pfau" -> BUTTERFLY;
         case "Sculk", "Warden" -> GALAXY;
         default -> PHOENIX;
      };
   }

   /** Einstellungen eines Flügelpaars: Stil, Größe, Farbton-Verschiebung (Grad), Effektstärke 0..1. */
   /**
    * height = höher/tiefer (px), depth = Abstand vom Rücken (px), tilt = Neigung vor/zurück (Grad), spread = weiter
    * geöffnet (+) oder mehr angelegt (-) in Grad, angle = Spitzen nach oben (+) oder unten (-) in Grad.
    */
   public record Spec(
      String style, float scale, float hue, float fx, boolean replaceElytra, boolean onlyElytra, float height, float depth, float tilt, float spread, float angle
   ) {
      public Spec(String style, float scale, float hue, float fx, boolean replaceElytra, boolean onlyElytra, float height) {
         this(style, scale, hue, fx, replaceElytra, onlyElytra, height, 0.0F, 0.0F, 0.0F, 0.0F);
      }

      public Spec(String style, float scale, float hue, float fx) {
         this(style, scale, hue, fx, true, false, 0.0F);
      }

      public Spec(String style, float scale, float hue, float fx, boolean replaceElytra, boolean onlyElytra) {
         this(style, scale, hue, fx, replaceElytra, onlyElytra, 0.0F);
      }

      /** „Stil|Farbton|Effekt|Elytra-Modus|Höhe“ – so wird es über das Meowl-Netzwerk geteilt. */
      public String encode() {
         return this.style
            + "|"
            + Math.round(this.hue)
            + "|"
            + Math.round(this.fx * 100.0F)
            + "|"
            + (this.replaceElytra ? "r" : "")
            + (this.onlyElytra ? "o" : "")
            + "|"
            + Math.round(this.height * 4.0F)
            + "|"
            + Math.round(this.depth * 4.0F)
            + "|"
            + Math.round(this.tilt)
            + "|"
            + Math.round(this.spread)
            + "|"
            + Math.round(this.angle);
      }

      public static WingsModel.Spec decode(String text, float scale) {
         String[] p = text == null ? new String[0] : text.split("\\|");
         String style = known(p.length > 0 ? p[0] : PHOENIX);
         float hue = 0.0F;
         float fx = 1.0F;
         float height = 0.0F;
         float depth = 0.0F;
         float tilt = 0.0F;
         float spread = 0.0F;
         float angle = 0.0F;
         String flags = p.length > 3 ? p[3] : "r";

         try {
            if (p.length > 5) {
               depth = Mth.clamp(Integer.parseInt(p[5]) / 4.0F, -MAX_DEPTH, MAX_DEPTH);
            }

            if (p.length > 6) {
               tilt = Mth.clamp(Integer.parseInt(p[6]), -MAX_TILT, MAX_TILT);
            }

            if (p.length > 8) {
               angle = Mth.clamp(Integer.parseInt(p[8]), -MAX_ANGLE, MAX_ANGLE);
            }

            if (p.length > 7) {
               spread = Mth.clamp(Integer.parseInt(p[7]), -MAX_SPREAD, MAX_SPREAD);
            }

            if (p.length > 4) {
               height = Mth.clamp(Integer.parseInt(p[4]) / 4.0F, -MAX_HEIGHT, MAX_HEIGHT);
            }

            if (p.length > 1) {
               hue = Mth.clamp(Float.parseFloat(p[1]), -180.0F, 180.0F);
            }

            if (p.length > 2) {
               fx = Mth.clamp(Float.parseFloat(p[2]) / 100.0F, 0.0F, 1.0F);
            }
         } catch (NumberFormatException var6) {
         }

         return new WingsModel.Spec(style, scale, hue, fx, flags.contains("r"), flags.contains("o"), height, depth, tilt, spread, angle);
      }
   }

   /** Höhenverstellung in Pixeln (1/16 Block). */
   public static final float MAX_HEIGHT = 6.0F;
   /** Abstand vom Rücken in Pixeln, Neigung und Öffnung in Grad. */
   public static final float MAX_DEPTH = 6.0F;
   public static final float MAX_TILT = 40.0F;
   public static final float MAX_SPREAD = 30.0F;
   public static final float MAX_ANGLE = 45.0F;

   /**
    * Bewegung: Zeit (s), Schlagphase (rad), Schlagstärke 0..1.5, Öffnung 0..1, Nachziehen beim Drehen (Grad),
    * Elytra-Modus 0..1 (Flügel ersetzen die Elytra) und Gleiten 0..1.
    */
   public record Anim(float time, float phase, float flap, float open, float lag, float elytra, float glide) {
      public Anim(float time, float phase, float flap, float open, float lag) {
         this(time, phase, flap, open, lag, 0.0F, 0.0F);
      }
   }

   private enum Layout {
      FEATHER,
      MEMBRANE,
      CRYSTAL,
      EARTH,
      BUTTERFLY,
      DRAGONFLY
   }

   /** Form-Variante innerhalb eines Layouts. */
   private enum Shape {
      NORMAL,
      RAGGED,
      SLIM,
      DEMON,
      DEVIL,
      DRAGON,
      BABY,
      WEB,
      /** zwei Flügel je Seite */
      DOUBLE
   }

   private enum Glow {
      NONE,
      EDGE,
      FLAME,
      FROST,
      NEON,
      NEBULA,
      EMBERS,
      GEMS,
      SHIMMER
   }

   /** root/tip = Farbverlauf, line = Kontur und Knochen, glowA/glowB = Leuchtfarben. */
   private record Look(Layout layout, Shape shape, Glow glow, int root, int tip, int line, int glowA, int glowB) {
   }

   private static WingsModel.Look look(String style) {
      return switch (style) {
         case BUTTERFLY -> new WingsModel.Look(Layout.BUTTERFLY, Shape.NORMAL, Glow.SHIMMER, 0xFF0B1E6E, 0xFF39B8FF, 0xFF05050C, 0xFF50C8FF, 0xFFFFFFFF);
         case DRAGONFLY -> new WingsModel.Look(Layout.DRAGONFLY, Shape.NORMAL, Glow.SHIMMER, 0xFF0E3A4A, 0xFF7FE8FF, 0xFF0A1A22, 0xFF40E0FF, 0xFFB070FF);
         case HELL_LORD -> new WingsModel.Look(Layout.MEMBRANE, Shape.DOUBLE, Glow.EMBERS, 0xFF2A0608, 0xFF8A1410, 0xFF0A0203, 0xFFFF4A10, 0xFFFFC040);
         case BLACK -> new WingsModel.Look(Layout.FEATHER, Shape.SLIM, Glow.NONE, 0xFF0C0C10, 0xFF2C2C38, 0xFF000000, 0xFF6A6AFF, 0xFFFFFFFF);
         case PHOENIX -> new WingsModel.Look(Layout.FEATHER, Shape.NORMAL, Glow.FLAME, 0xFF9A160A, 0xFFFFC23A, 0xFF4A0804, 0xFFFF6A10, 0xFFFFD040);
         case FROST -> new WingsModel.Look(Layout.FEATHER, Shape.NORMAL, Glow.FROST, 0xFF5AA8E0, 0xFFE8FAFF, 0xFF1E4A78, 0xFF7FE0FF, 0xFFFFFFFF);
         case NEON -> new WingsModel.Look(Layout.FEATHER, Shape.SLIM, Glow.NEON, 0xFF101018, 0xFF1C1C2A, 0xFF05050A, 0xFF20F0FF, 0xFFFF30D0);
         case DEMON -> new WingsModel.Look(Layout.MEMBRANE, Shape.DEMON, Glow.EMBERS, 0xFF7A0E12, 0xFFB8241C, 0xFF240406, 0xFFFF5A14, 0xFFFFC040);
         case DEMON_BLACK -> new WingsModel.Look(Layout.MEMBRANE, Shape.DEMON, Glow.EMBERS, 0xFF16141A, 0xFF34303C, 0xFF050406, 0xFFB01818, 0xFFFF4020);
         case DEVIL -> new WingsModel.Look(Layout.MEMBRANE, Shape.DEVIL, Glow.EMBERS, 0xFFC0161A, 0xFFFF4A2A, 0xFF1A0204, 0xFFFF7A1A, 0xFFFFD040);
         case DRAGON -> new WingsModel.Look(Layout.MEMBRANE, Shape.DRAGON, Glow.NONE, 0xFF1C5A2A, 0xFF5AB04A, 0xFF0A2410, 0xFFFFC040, 0xFFFFE080);
         case DRAGON_BLACK -> new WingsModel.Look(Layout.MEMBRANE, Shape.DRAGON, Glow.EMBERS, 0xFF141218, 0xFF3A2A4A, 0xFF040306, 0xFFC040FF, 0xFFFF80FF);
         case BABY_DRAGON -> new WingsModel.Look(Layout.MEMBRANE, Shape.BABY, Glow.NONE, 0xFF48A858, 0xFFB8E88A, 0xFF1E4A24, 0xFFFFE080, 0xFFFFFFFF);
         case GALAXY -> new WingsModel.Look(Layout.MEMBRANE, Shape.DEMON, Glow.NEBULA, 0xFF120A30, 0xFF3A1A70, 0xFF06041A, 0xFF8A4DFF, 0xFFFFFFFF);
         case WEB -> new WingsModel.Look(Layout.MEMBRANE, Shape.WEB, Glow.EDGE, 0xFF1A1A1E, 0xFFF0F0F4, 0xFF08080A, 0xFFB0FFB0, 0xFFFFFFFF);
         case CRYSTAL -> new WingsModel.Look(Layout.CRYSTAL, Shape.NORMAL, Glow.GEMS, 0xFF8A3FC8, 0xFFFFB0F0, 0xFF3A1460, 0xFFE070FF, 0xFFFFFFFF);
         case EARTH -> new WingsModel.Look(Layout.EARTH, Shape.NORMAL, Glow.GEMS, 0xFF6A4A2E, 0xFF8A8A8E, 0xFF2A1C10, 0xFF40FF70, 0xFFC0FFD0);
         default -> new WingsModel.Look(Layout.FEATHER, Shape.DOUBLE, Glow.EDGE, 0xFFE8E2D0, 0xFFFFFFFF, 0xFFB08A3A, 0xFFFFD060, 0xFFFFF4C0);
      };
   }

   /** Hauptfarben eines Stils (für die Farbton-Vorschau). */
   public static int[] palette(String style) {
      WingsModel.Look k = look(style);
      return new int[]{k.root(), k.tip(), k.line(), k.glowA(), k.glowB()};
   }

   /** Zeichenzustand für eine Flügelseite. */
   private static final class Ctx {
      PoseStack m;
      VertexConsumer vc;
      boolean glow;
      int side;
      float light;
      float hue;
      float fx;
      float time;
      float flap;
      WingsModel.Look k;

      int col(int argb) {
         return this.hue == 0.0F ? argb : shiftHue(argb, this.hue);
      }

      int glowCol(int argb, float alpha) {
         float al = Mth.clamp(alpha * this.fx, 0.0F, 1.0F);
         return al <= 0.01F ? 0 : Math.round(al * 255.0F) << 24 | this.col(argb) & 16777215;
      }

      /** Quader entlang der lokalen x-Achse (a..b), gespiegelt je Seite. */
      void lbox(float a, float b, float y1, float y2, float z1, float z2, int argb, boolean glowBox, float alpha) {
         if (glowBox == this.glow) {
            float x1 = this.side < 0 ? -b : a;
            float x2 = this.side < 0 ? -a : b;
            if (glowBox) {
               int c = this.glowCol(argb, alpha);
               if (c != 0) {
                  WorldRender.box(this.m, this.vc, x1, y1, z1, x2, y2, z2, c, 1.0F);
               }
            } else {
               WorldRender.box(this.m, this.vc, x1, y1, z1, x2, y2, z2, this.col(argb) | 0xFF000000, this.light);
            }
         }
      }

      /** In ein lokales Bezugssystem an (x, y, z) mit Winkel (Grad, 0 = nach außen, positiv = nach unten) wechseln. */
      void enter(float x, float y, float z, float angle) {
         this.m.pushPose();
         this.m.translate(this.side * x, y, z);
         this.m.rotate(Axis.ZP.rotationDegrees(this.side * angle));
      }

      void leave() {
         this.m.popPose();
      }

      /** Gerader Balken von (x1,y1) nach (x2,y2) in der Flügelebene. */
      void seg(float x1, float y1, float x2, float y2, float z, float w, float t, int argb, boolean glowBox, float alpha) {
         float dx = x2 - x1;
         float dy = y2 - y1;
         float len = (float)Math.sqrt(dx * dx + dy * dy);
         if (len > 0.01F) {
            this.enter(x1, y1, z, (float)Math.toDegrees(Math.atan2(dy, dx)));
            this.lbox(-w / 2.0F, len + w / 2.0F, -w / 2.0F, w / 2.0F, -t / 2.0F, t / 2.0F, argb, glowBox, alpha);
            this.leave();
         }
      }

      /** Flaches Dreieck in der Flügelebene (beidseitig sichtbar), Farbe je Ecke. */
      void tri(float ax, float ay, float bx, float by, float cx, float cy, float z, int ca, int cb, int cc, boolean glowPoly, float alpha) {
         if (glowPoly == this.glow) {
            int a;
            int b;
            int c;
            if (glowPoly) {
               a = this.glowCol(ca, alpha);
               b = this.glowCol(cb, alpha);
               c = this.glowCol(cc, alpha);
               if (a == 0 && b == 0 && c == 0) {
                  return;
               }
            } else {
               a = WorldRender.shade(this.col(ca) | 0xFF000000, this.light * 0.92F);
               b = WorldRender.shade(this.col(cb) | 0xFF000000, this.light * 0.92F);
               c = WorldRender.shade(this.col(cc) | 0xFF000000, this.light * 0.92F);
            }

            Matrix4f mat = this.m.last().pose();
            float s = this.side;
            this.vc.addVertex(mat, s * ax, ay, z).setColor(a);
            this.vc.addVertex(mat, s * bx, by, z).setColor(b);
            this.vc.addVertex(mat, s * cx, cy, z).setColor(c);
            this.vc.addVertex(mat, s * cx, cy, z).setColor(c);
            this.vc.addVertex(mat, s * ax, ay, z).setColor(a);
            this.vc.addVertex(mat, s * cx, cy, z).setColor(c);
            this.vc.addVertex(mat, s * bx, by, z).setColor(b);
            this.vc.addVertex(mat, s * bx, by, z).setColor(b);
         }
      }
   }

   /**
    * @param glow true = Leucht-Durchgang (additive Ebene), false = fester Durchgang
    */
   public static void draw(PoseStack m, VertexConsumer vc, boolean glow, WingsModel.Spec spec, WingsModel.Anim a, float light) {
      WingsModel.Look k = look(spec.style());
      if (glow && (spec.fx() <= 0.01F || k.glow() == Glow.NONE)) {
         return;
      }

      float flap = a.flap();
      // Als Elytra angelegt: kaum Flügelschlag, die Flügel liegen nach hinten geklappt
      float closed = a.elytra() * (1.0F - a.glide());
      float beat = Mth.sin(a.phase()) * (1.0F - closed * 0.85F);
      // sanftes Atmen im Stand, damit die Flügel nie ganz starr sind
      float idle = Mth.sin(a.time() * 1.3F) * (1.0F - closed);
      m.pushPose();
      m.translate(0.0F, 1.0F - spec.height(), 2.3F + spec.depth());
      m.scale(spec.scale(), spec.scale(), spec.scale());
      // beim Drehen ziehen beide Flügel leicht nach
      m.rotate(Axis.YP.rotationDegrees(a.lag()));
      // ganz leicht nach hinten geneigt, wie bei Lunar
      m.rotate(Axis.XP.rotationDegrees(-8.0F - spec.tilt()));

      for (int side = -1; side <= 1; side += 2) {
         m.pushPose();
         m.translate(side * 1.4F, 0.0F, 0.0F);
         float back = 34.0F - spec.spread() - a.open() * 24.0F - beat * (5.0F + flap * 18.0F) - idle * 2.5F;
         // Winkel: + = Spitzen nach oben, - = nach unten
         float roll = beat * (2.0F + flap * 9.0F) - 3.0F + idle * 1.0F - spec.angle();
         if (a.elytra() > 0.001F) {
            // Elytra-Modus: am Boden flach an den Rücken gelegt, Spitzen nach unten; beim Gleiten nach hinten gepfeilt
            float glide = a.elytra() * a.glide();
            m.translate(0.0F, closed * 1.5F, closed * 0.4F);
            m.rotate(Axis.XP.rotationDegrees(closed * 6.0F + glide * 8.0F));
            back = lerp(back, 14.0F, closed) + glide * 18.0F;
            roll = lerp(roll, 72.0F, closed) + glide * 10.0F;
            float shrink = 1.0F - 0.28F * closed;
            m.scale(shrink, shrink, shrink);
         }

         m.rotate(Axis.YP.rotationDegrees(side * -back));
         m.rotate(Axis.ZP.rotationDegrees(side * roll));
         WingsModel.Ctx c = new WingsModel.Ctx();
         c.m = m;
         c.vc = vc;
         c.glow = glow;
         c.side = side;
         c.light = light;
         c.hue = spec.hue();
         c.fx = spec.fx();
         c.time = a.time();
         c.flap = flap;
         c.k = k;
         java.util.function.Consumer<WingsModel.Anim> wing = switch (k.layout()) {
            case MEMBRANE -> w -> membrane(c, w);
            case CRYSTAL -> w -> crystal(c, w);
            case EARTH -> w -> earth(c, w);
            case BUTTERFLY -> w -> butterfly(c, w);
            case DRAGONFLY -> w -> dragonfly(c, w);
            default -> w -> feathered(c, w);
         };
         if (k.shape() == Shape.DOUBLE) {
            twin(c, a, wing);
         } else {
            wing.accept(a);
         }

         m.popPose();
      }

      m.popPose();
   }

   // ================================================================== gemeinsame Form

   /** Vorderkante (Arm) als Bogen: Schulter → hoch über den Kopf → Spitze außen. */
   private static float armX(float t) {
      float u = 1.0F - t;
      return u * u * 0.5F + 2.0F * u * t * 4.0F + t * t * 17.0F;
   }

   private static float armY(float t) {
      float u = 1.0F - t;
      return u * u * 0.0F + 2.0F * u * t * -19.0F + t * t * -11.0F;
   }

   /** Welle entlang des Flügels: äußere Teile folgen dem Schlag zeitversetzt. */
   private static float wave(WingsModel.Anim a, float t) {
      return Mth.sin(a.phase() - 0.5F - t * 1.6F) * (1.2F + a.flap() * 5.0F) * (0.4F + 0.6F * t) + Mth.sin(a.time() * 1.3F - t * 1.2F) * 0.8F;
   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   private static float rad(float deg) {
      return deg * ((float)Math.PI / 180.0F);
   }

   // ================================================================== Federflügel

   /** Richtung einer Feder (Grad) an Stelle t des Arms: innen hängen sie nach unten, außen zeigen sie nach außen. */
   private static float featherDir(float t) {
      return lerp(96.0F, 26.0F, (float)Math.pow(t, 0.9));
   }

   /** Länge der langen Federn an Stelle t. */
   private static float featherLen(float t) {
      return lerp(15.0F, 11.0F, t) + 5.0F * Mth.sin((float)Math.PI * Math.min(1.0F, t * 1.15F));
   }

   private static void feathered(WingsModel.Ctx c, WingsModel.Anim a) {
      WingsModel.Look k = c.k;
      boolean ragged = k.shape() == Shape.RAGGED;
      boolean slim = k.shape() == Shape.SLIM;
      int rowMid = mix(k.root(), k.tip(), 0.5F);
      // drei Reihen: lange Schwungfedern hinten, mittlere, kurze Deckfedern vorn
      int[] counts = slim ? new int[]{11, 8, 6} : new int[]{14, 11, 9};
      float[] lens = new float[]{1.0F, 0.64F, 0.34F};
      float[] widths = slim ? new float[]{2.0F, 2.1F, 2.2F} : new float[]{3.3F, 3.0F, 2.7F};
      int[] colors = new int[]{k.root(), rowMid, k.tip()};
      float z = 0.0F;

      for (int row = 0; row < 3; row++) {
         int n = counts[row];

         for (int i = 0; i < n; i++) {
            float t = row == 0 ? i / (n - 1.0F) : 0.04F + 0.96F * i / (n - 1.0F);
            float len = featherLen(t) * lens[row] * (slim ? 1.08F : 1.0F);
            float w = widths[row];
            int idx = row * 20 + i;
            if (ragged) {
               if (row < 2 && rand(idx * 7 + 3) < 0.14F && i > 0 && i < n - 1) {
                  z += 0.08F;
                  continue;
               }

               len *= 0.72F + 0.42F * rand(idx * 13 + 1);
               w *= 0.8F + 0.35F * rand(idx * 5 + 2);
            }

            float dir = featherDir(t) + wave(a, t) * (row == 0 ? 1.0F : 0.6F) + (ragged ? (rand(idx) - 0.5F) * 10.0F : 0.0F);
            float x = armX(t);
            float y = armY(t) + (row == 0 ? 0.6F : 0.3F);
            feather(c, x, y, z, dir, len, w, colors[row], idx, row);
            z += 0.08F;
         }

         z += 0.1F;
      }

      // Arm: Vorderkante mit Kontur
      int armCol = mix(k.tip(), 0xFFFFFFFF, k.root() == 0xFF0C0C10 || k.shape() == Shape.RAGGED ? 0.0F : 0.15F);

      for (int i = 0; i < 8; i++) {
         float t1 = i / 8.0F;
         float t2 = (i + 1) / 8.0F;
         float w = lerp(2.6F, 1.6F, t1);
         c.seg(armX(t1), armY(t1), armX(t2), armY(t2), z - 0.05F, w + 0.8F, 0.5F, k.line(), false, 1.0F);
         c.seg(armX(t1), armY(t1), armX(t2), armY(t2), z + 0.1F, w, 0.7F, armCol, false, 1.0F);
         if (k.glow() == Glow.NEON) {
            c.seg(armX(t1), armY(t1), armX(t2), armY(t2), z + 0.1F, w * 0.5F, 0.8F, mix(k.glowA(), k.glowB(), t1), true, 0.9F);
         } else if (k.glow() == Glow.EDGE) {
            c.seg(armX(t1), armY(t1), armX(t2), armY(t2), z + 0.1F, w + 0.4F, 0.9F, k.glowA(), true, 0.14F);
         }
      }
   }

   /** Eine Feder mit dunkler Pixel-Kontur, Verlauf zur Spitze und spitzem Ende. */
   private static void feather(WingsModel.Ctx c, float x, float y, float z, float angle, float len, float w, int col, int idx, int row) {
      WingsModel.Look k = c.k;
      float t = c.time;
      float h = w / 2.0F;
      int tipCol = mix(col, k.tip(), 0.55F);
      c.enter(x, y, z, angle);
      // Kontur (etwas dünner in z, damit sie nur am Rand hervorschaut)
      c.lbox(-0.3F, len * 0.76F + 0.3F, -h - 0.32F, h + 0.32F, -0.14F, 0.14F, k.line(), false, 1.0F);
      c.lbox(len * 0.74F, len + 0.4F, -h * 0.55F - 0.32F, h * 0.55F + 0.32F, -0.14F, 0.14F, k.line(), false, 1.0F);
      // Fahne in drei Stufen bis zur Spitze
      c.lbox(0.0F, len * 0.46F, -h, h, -0.2F, 0.2F, col, false, 1.0F);
      c.lbox(len * 0.46F, len * 0.76F, -h, h, -0.2F, 0.2F, mix(col, tipCol, 0.5F), false, 1.0F);
      c.lbox(len * 0.76F, len, -h * 0.55F, h * 0.55F, -0.2F, 0.2F, tipCol, false, 1.0F);
      // Kiel: heller Strich in der Mitte
      if (k.glow() != Glow.NEON) {
         c.lbox(0.0F, len * 0.84F, -0.14F, 0.14F, -0.24F, 0.24F, mix(col, 0xFFFFFFFF, 0.3F), false, 1.0F);
      }

      if (c.glow) {
         switch (k.glow()) {
            case FLAME: {
               float flick = 0.5F + 0.5F * Mth.sin(t * 17.0F + idx * 1.7F) * Mth.sin(t * 11.0F + idx * 0.9F);
               float tongue = len * (0.16F + 0.24F * flick) * (row == 2 ? 0.3F : 1.0F);
               c.lbox(len * 0.6F, len + tongue, -h * 0.7F, h * 0.7F, -0.26F, 0.26F, k.glowA(), true, 0.42F);
               c.lbox(len * 0.82F, len + tongue * 0.6F, -h * 0.35F, h * 0.35F, -0.3F, 0.3F, k.glowB(), true, 0.6F);
               c.lbox(0.0F, len, -h, h, -0.22F, 0.22F, k.glowA(), true, 0.1F + 0.08F * flick);
               break;
            }
            case FROST: {
               float tw = Mth.sin(t * 2.4F + idx * 1.3F);
               tw = tw > 0.0F ? tw * tw * tw : 0.0F;
               c.lbox(len * 0.7F, len + 0.3F, -h * 0.6F, h * 0.6F, -0.26F, 0.26F, k.glowA(), true, 0.22F);
               float px = len * (0.3F + 0.6F * rand(idx * 3 + 1));
               c.lbox(px - 0.3F, px + 0.3F, -0.3F, 0.3F, -0.32F, 0.32F, k.glowB(), true, 0.15F + 0.85F * tw);
               break;
            }
            case NEON: {
               int cc = mix(k.glowA(), k.glowB(), Mth.clamp(row * 0.4F + 0.3F * Mth.sin(t * 1.5F + idx * 0.4F), 0.0F, 1.0F));
               float pulse = 0.65F + 0.35F * Mth.sin(t * 3.0F - idx * 0.5F);
               c.lbox(0.0F, len * 0.76F, h - 0.25F, h + 0.1F, -0.26F, 0.26F, cc, true, pulse);
               c.lbox(0.0F, len * 0.76F, -h - 0.1F, -h + 0.25F, -0.26F, 0.26F, cc, true, pulse);
               c.lbox(len * 0.76F, len + 0.2F, -h * 0.55F - 0.1F, h * 0.55F + 0.1F, -0.26F, 0.26F, cc, true, pulse);
               break;
            }
            case EDGE:
               if (row == 0) {
                  c.lbox(len * 0.72F, len + 0.2F, -h * 0.6F, h * 0.6F, -0.24F, 0.24F, k.glowA(), true, 0.16F);
               }
               break;
            default:
         }
      }

      c.leave();
   }

   // ================================================================== Hautflügel

   private static void membrane(WingsModel.Ctx c, WingsModel.Anim a) {
      WingsModel.Look k = c.k;
      Shape s = k.shape() == Shape.DOUBLE ? Shape.DEMON : k.shape();
      float size = s == Shape.BABY ? 0.62F : 1.0F;
      float scallop = switch (s) {
         case DEVIL -> 0.04F;
         case BABY -> 0.14F;
         case WEB -> 0.1F;
         default -> 0.24F;
      };
      // Schulter, Handgelenk (über dem Kopf), Fingerspitzen und Ansatz am Körper
      float sx = 0.0F;
      float sy = 0.0F;
      float wx = 10.0F * size;
      float wy = -15.0F * size;
      float[][] tips = s == Shape.DEVIL
         ? new float[][]{{29.0F, -10.0F}, {27.0F, 3.0F}, {20.0F, 13.0F}, {10.0F, 17.0F}}
         : new float[][]{{27.0F, -9.0F}, {25.0F, 2.5F}, {19.0F, 11.5F}, {10.0F, 16.0F}};
      int n = tips.length;
      float[] fx = new float[n + 1];
      float[] fy = new float[n + 1];

      for (int i = 0; i < n; i++) {
         // Finger fächern mit dem Schlag leicht auf und zu
         float f = i / (n - 1.0F);
         float ang = rad(wave(a, 1.0F - f * 0.5F) * (0.6F + f * 0.5F));
         float dx = tips[i][0] * size - wx;
         float dy = tips[i][1] * size - wy;
         fx[i] = wx + dx * Mth.cos(ang) - dy * Mth.sin(ang);
         fy[i] = wy + dx * Mth.sin(ang) + dy * Mth.cos(ang);
      }

      // Ansatz am Körper (unterer Rand der Flughaut)
      fx[n] = 1.2F;
      fy[n] = 12.0F * size;
      int bone = s == Shape.WEB ? k.root() : k.line();
      int boneLight = mix(k.line(), k.root(), 0.55F);
      float zm = 0.0F;
      float zb = 0.35F;

      // Flughaut zwischen den Fingern, Rand dazwischen nach innen gewellt
      for (int i = 0; i < n; i++) {
         float ax = fx[i];
         float ay = fy[i];
         float bx = fx[i + 1];
         float by = fy[i + 1];
         float mx = lerp(ax, bx, 0.5F);
         float my = lerp(ay, by, 0.5F);
         mx += (wx - mx) * scallop;
         my += (wy - my) * scallop;
         if (s == Shape.WEB) {
            web(c, wx, wy, ax, ay, bx, by, mx, my);
         } else {
            int inner = k.root();
            int outer = k.tip();
            c.tri(wx, wy, ax, ay, mx, my, zm, inner, outer, mix(inner, outer, 0.7F), false, 1.0F);
            c.tri(wx, wy, mx, my, bx, by, zm, inner, mix(inner, outer, 0.7F), outer, false, 1.0F);
            // Rand als Pixel-Kontur
            c.seg(ax, ay, mx, my, zm, 0.7F, 0.5F, k.line(), false, 1.0F);
            c.seg(mx, my, bx, by, zm, 0.7F, 0.5F, k.line(), false, 1.0F);
            if (s == Shape.DRAGON) {
               // Schuppen-Streifen in der Haut
               c.seg(wx, wy, lerp(mx, wx, 0.12F), lerp(my, wy, 0.12F), zm + 0.05F, 0.5F, 0.4F, mix(k.tip(), 0xFFFFFFFF, 0.15F), false, 1.0F);
            }

            if (c.glow) {
               membraneGlow(c, a, wx, wy, ax, ay, bx, by, mx, my, i);
            }
         }
      }

      // Rest der Flughaut bis zur Schulter
      if (s != Shape.WEB) {
         c.tri(wx, wy, fx[n], fy[n], sx, sy, zm, k.root(), k.tip(), k.root(), false, 1.0F);
      }

      // Knochen: Arm und Finger (zwei Glieder mit leichtem Knick)
      float armW = s == Shape.BABY ? 1.5F : 2.0F;
      c.seg(sx, sy, wx, wy, zb, armW + 0.7F, 1.0F, k.line(), false, 1.0F);
      c.seg(sx, sy, wx, wy, zb + 0.15F, armW, 1.2F, boneLight, false, 1.0F);

      for (int i = 0; i < n; i++) {
         float kx = lerp(wx, fx[i], 0.45F) + (i == 0 ? 0.0F : 0.6F);
         float ky = lerp(wy, fy[i], 0.45F) - 0.6F;
         float w = lerp(1.4F, 0.9F, i / (n - 1.0F)) * (s == Shape.BABY ? 0.85F : 1.0F);
         c.seg(wx, wy, kx, ky, zb, w, 0.9F, bone, false, 1.0F);
         c.seg(kx, ky, fx[i], fy[i], zb, w * 0.75F, 0.8F, bone, false, 1.0F);
         if (s != Shape.BABY && s != Shape.WEB) {
            // Krallen an den Fingerspitzen
            float dx = fx[i] - kx;
            float dy = fy[i] - ky;
            float len = (float)Math.sqrt(dx * dx + dy * dy);
            float ux = dx / len;
            float uy = dy / len;
            float cl = s == Shape.DEVIL ? 3.2F : 2.0F;
            c.tri(fx[i] - uy * 0.8F, fy[i] + ux * 0.8F, fx[i] + uy * 0.8F, fy[i] - ux * 0.8F, fx[i] + ux * cl, fy[i] + uy * cl, zb + 0.3F, k.line(), k.line(), mix(k.line(), 0xFFFFFFFF, 0.25F), false, 1.0F);
         }
      }

      // Daumenkralle am Handgelenk, nach oben
      float th = s == Shape.BABY ? 1.8F : 3.2F;
      c.tri(wx - 1.0F, wy + 0.5F, wx + 1.0F, wy, wx + 0.4F, wy - th, zb + 0.35F, k.line(), k.line(), mix(k.line(), 0xFFFFFFFF, 0.3F), false, 1.0F);
      if (s == Shape.DEMON || s == Shape.DRAGON || s == Shape.DEVIL) {
         // Stacheln entlang des Arms
         for (int i = 1; i <= 3; i++) {
            float t = i / 4.0F;
            float px = lerp(sx, wx, t);
            float py = lerp(sy, wy, t);
            float nx = -(wy - sy);
            float ny = wx - sx;
            float nl = (float)Math.sqrt(nx * nx + ny * ny);
            nx /= nl;
            ny /= nl;
            float sp = s == Shape.DRAGON ? 1.6F : 2.2F;
            c.tri(px - 0.8F * (wx - sx) / 18.0F, py - 0.8F * (wy - sy) / 18.0F, px + 0.8F * (wx - sx) / 18.0F, py + 0.8F * (wy - sy) / 18.0F, px - nx * sp, py - ny * sp, zb + 0.3F, k.line(), k.line(), mix(k.line(), 0xFFFFFFFF, 0.2F), false, 1.0F);
         }
      }

      if (c.glow && k.glow() == Glow.EDGE) {
         c.seg(sx, sy, wx, wy, zb + 0.2F, armW + 0.6F, 1.4F, k.glowA(), true, 0.12F);
      }
   }

   private static void membraneGlow(WingsModel.Ctx c, WingsModel.Anim a, float wx, float wy, float ax, float ay, float bx, float by, float mx, float my, int i) {
      WingsModel.Look k = c.k;
      float t = c.time;
      switch (k.glow()) {
         case NEBULA: {
            // Sterne in der Flughaut, funkeln nacheinander
            for (int s = 0; s < 7; s++) {
               int seed = i * 31 + s * 7;
               float u = rand(seed);
               float v = rand(seed + 1) * (1.0F - u);
               boolean first = rand(seed + 2) < 0.5F;
               float px = wx + (ax - wx) * u + ((first ? mx : bx) - wx) * v;
               float py = wy + (ay - wy) * u + ((first ? my : by) - wy) * v;
               float tw = Mth.sin(t * (1.5F + rand(seed + 3) * 2.5F) + seed);
               tw = tw > 0.0F ? tw * tw * tw : 0.0F;
               float r = 0.25F + 0.2F * rand(seed + 4);
               c.lbox(px - r, px + r, py - r, py + r, -0.15F, 0.15F, k.glowB(), true, 0.25F + 0.75F * tw);
            }

            float hue = 0.72F + 0.1F * Mth.sin(t * 0.5F + i);
            c.tri(wx, wy, ax, ay, mx, my, 0.02F, 0xFF000000, hsv(hue, 0.8F, 1.0F), hsv(hue + 0.05F, 0.8F, 1.0F), true, 0.22F);
            c.tri(wx, wy, mx, my, bx, by, 0.02F, 0xFF000000, hsv(hue + 0.05F, 0.8F, 1.0F), hsv(hue, 0.8F, 1.0F), true, 0.22F);
            break;
         }
         case EMBERS: {
            // glühender Rand, der langsam pulsiert
            float pulse = 0.5F + 0.5F * Mth.sin(t * 2.2F + i * 1.3F);
            c.seg(ax, ay, mx, my, 0.0F, 1.1F, 0.8F, k.glowA(), true, 0.18F + 0.2F * pulse);
            c.seg(mx, my, bx, by, 0.0F, 1.1F, 0.8F, k.glowA(), true, 0.18F + 0.2F * pulse);
            float e = frac(t * 0.4F + rand(i * 9) );
            float px = lerp(mx, wx, e * 0.8F);
            float py = lerp(my, wy, e * 0.8F);
            c.lbox(px - 0.3F, px + 0.3F, py - 0.3F, py + 0.3F, -0.2F, 0.2F, k.glowB(), true, 0.7F * (1.0F - e));
            break;
         }
         default:
      }
   }

   /** Spinnennetz statt Flughaut: Fäden in Bögen zwischen zwei Fingern. */
   private static void web(WingsModel.Ctx c, float wx, float wy, float ax, float ay, float bx, float by, float mx, float my) {
      WingsModel.Look k = c.k;
      int thread = k.tip();

      for (int r = 1; r <= 4; r++) {
         float f = r / 4.6F;
         float x1 = lerp(wx, ax, f);
         float y1 = lerp(wy, ay, f);
         float x2 = lerp(wx, bx, f);
         float y2 = lerp(wy, by, f);
         float xm = lerp(wx, mx, f);
         float ym = lerp(wy, my, f);
         c.seg(x1, y1, xm, ym, 0.0F, 0.4F, 0.3F, thread, false, 1.0F);
         c.seg(xm, ym, x2, y2, 0.0F, 0.4F, 0.3F, thread, false, 1.0F);
         if (c.glow) {
            c.seg(x1, y1, xm, ym, 0.0F, 0.7F, 0.5F, k.glowA(), true, 0.12F);
            c.seg(xm, ym, x2, y2, 0.0F, 0.7F, 0.5F, k.glowA(), true, 0.12F);
         }
      }

      c.seg(wx, wy, mx, my, 0.0F, 0.4F, 0.3F, thread, false, 1.0F);
   }

   // ================================================================== Kristall

   private static void crystal(WingsModel.Ctx c, WingsModel.Anim a) {
      WingsModel.Look k = c.k;
      float z = 0.0F;

      // zwei Reihen Splitter: lange hinten, kürzere vorn
      for (int row = 0; row < 2; row++) {
         int n = row == 0 ? 11 : 8;

         for (int i = 0; i < n; i++) {
            float t = row == 0 ? i / (n - 1.0F) : 0.06F + 0.9F * i / (n - 1.0F);
            int idx = row * 20 + i;
            float len = featherLen(t) * (row == 0 ? 1.05F : 0.6F) * (0.85F + 0.25F * rand(idx * 3));
            float w = (row == 0 ? 3.4F : 3.0F) * (0.85F + 0.3F * rand(idx * 5 + 1));
            float dir = featherDir(t) + wave(a, t) * 0.7F;
            shard(c, armX(t), armY(t), z, dir, len, w, row == 0 ? k.root() : mix(k.root(), k.tip(), 0.4F), idx);
            z += 0.12F;
         }

         z += 0.1F;
      }

      // Arm aus kleinen Kristallen
      for (int i = 0; i < 6; i++) {
         float t = (i + 0.5F) / 6.0F;
         shard(c, armX(t), armY(t), z, -60.0F + i * 12.0F, 3.5F, 2.6F, k.tip(), 60 + i);
      }
   }

   /** Länglicher Kristall: Raute mit hellen und dunklen Facetten. */
   private static void shard(WingsModel.Ctx c, float x, float y, float z, float angle, float len, float w, int col, int idx) {
      WingsModel.Look k = c.k;
      float h = w / 2.0F;
      int light = mix(col, k.tip(), 0.65F);
      int dark = mix(col, k.line(), 0.35F);
      c.enter(x, y, z, angle);
      float shoulder = len * 0.22F;
      // Kontur dahinter
      c.tri(-0.5F, 0.0F, shoulder, -h - 0.5F, len + 0.7F, 0.0F, -0.08F, k.line(), k.line(), k.line(), false, 1.0F);
      c.tri(-0.5F, 0.0F, len + 0.7F, 0.0F, shoulder, h + 0.5F, -0.08F, k.line(), k.line(), k.line(), false, 1.0F);
      // Facetten
      c.tri(0.0F, 0.0F, shoulder, -h, len, 0.0F, 0.0F, col, light, mix(light, 0xFFFFFFFF, 0.3F), false, 1.0F);
      c.tri(0.0F, 0.0F, len, 0.0F, shoulder, h, 0.0F, dark, mix(col, k.tip(), 0.3F), col, false, 1.0F);
      if (c.glow) {
         // Glanz wandert den Splitter hinauf
         float g = frac(c.time * 0.35F + rand(idx) );
         float px = len * g;
         float hw = h * (px < shoulder ? px / shoulder : (len - px) / (len - shoulder));
         c.lbox(px - 0.6F, px + 0.6F, -hw * 0.8F, hw * 0.8F, -0.12F, 0.12F, k.glowB(), true, 0.55F * Mth.sin(g * (float)Math.PI));
         c.tri(0.0F, 0.0F, shoulder, -h, len, 0.0F, 0.04F, 0xFF000000, k.glowA(), k.glowA(), true, 0.12F);
      }

      c.leave();
   }

   // ================================================================== Erd-Elementar

   private static void earth(WingsModel.Ctx c, WingsModel.Anim a) {
      WingsModel.Look k = c.k;
      int dirt = k.root();
      int stone = k.tip();
      int moss = 0xFF4E8A3A;
      int[] palette = new int[]{dirt, stone, mix(dirt, stone, 0.5F), moss, mix(stone, 0xFF000000, 0.2F)};

      // Arm aus Blöcken
      for (int i = 0; i < 7; i++) {
         float t = i / 6.0F;
         float s = lerp(3.6F, 2.6F, t);
         c.enter(armX(t), armY(t), 0.6F, 20.0F * t);
         c.lbox(-s / 2.0F - 0.3F, s / 2.0F + 0.3F, -s / 2.0F - 0.3F, s / 2.0F + 0.3F, -s / 2.0F + 0.1F, s / 2.0F - 0.1F, k.line(), false, 1.0F);
         c.lbox(-s / 2.0F, s / 2.0F, -s / 2.0F, s / 2.0F, -s / 2.0F, s / 2.0F, palette[(i * 3) % palette.length], false, 1.0F);
         c.leave();
      }

      // hängende Blockketten wie Federn
      for (int i = 0; i < 10; i++) {
         float t = i / 9.0F;
         float dir = featherDir(t) + wave(a, t) * 0.6F;
         float len = featherLen(t) * 0.95F;
         int blocks = Math.max(2, Math.round(len / 3.2F));
         c.enter(armX(t), armY(t), 0.0F, dir);

         for (int b = 0; b < blocks; b++) {
            float s = lerp(3.0F, 1.8F, b / (float)blocks);
            float p = b * 3.1F + 1.8F;
            int col = palette[(int)(rand(i * 17 + b) * palette.length) % palette.length];
            c.lbox(p - s / 2.0F - 0.25F, p + s / 2.0F + 0.25F, -s / 2.0F - 0.25F, s / 2.0F + 0.25F, -s / 2.0F + 0.1F, s / 2.0F - 0.1F, k.line(), false, 1.0F);
            c.lbox(p - s / 2.0F, p + s / 2.0F, -s / 2.0F, s / 2.0F, -s / 2.0F, s / 2.0F, col, false, 1.0F);
         }

         // grüne Kristalle zwischen den Steinen
         if (rand(i * 7 + 5) < 0.55F) {
            float p = (blocks - 1) * 3.1F + 1.8F + 1.2F;
            c.leave();
            c.enter(armX(t) + Mth.cos(rad(dir)) * p, armY(t) + Mth.sin(rad(dir)) * p, 0.4F, dir + (rand(i) - 0.5F) * 40.0F);
            c.tri(0.0F, -0.9F, 3.0F, 0.0F, 0.0F, 0.9F, 0.0F, 0xFF1E7A3A, 0xFFA0FFB8, 0xFF2EA850, false, 1.0F);
            if (c.glow) {
               float tw = 0.5F + 0.5F * Mth.sin(c.time * 2.0F + i * 1.7F);
               c.tri(-0.4F, -1.3F, 3.6F, 0.0F, -0.4F, 1.3F, 0.05F, k.glowA(), k.glowB(), k.glowA(), true, 0.25F + 0.35F * tw);
            }
         }

         c.leave();
      }
   }

   // ================================================================== Doppelflügel

   /** Zwei Flügel je Seite: oben der große, darunter ein kleinerer, der leicht versetzt schlägt. */
   private static void twin(WingsModel.Ctx c, WingsModel.Anim a, java.util.function.Consumer<WingsModel.Anim> wing) {
      wing.accept(a);
      WingsModel.Anim low = new WingsModel.Anim(a.time() + 0.45F, a.phase() - 0.9F, a.flap(), a.open(), a.lag(), a.elytra(), a.glide());
      c.m.pushPose();
      c.m.translate(c.side * 0.5F, 5.0F, -0.8F);
      c.m.rotate(Axis.ZP.rotationDegrees(c.side * 72.0F));
      c.m.scale(0.74F, 0.74F, 0.74F);
      wing.accept(low);
      c.m.popPose();
   }

   // ================================================================== Schmetterling

   private static void butterfly(WingsModel.Ctx c, WingsModel.Anim a) {
      // Vorderflügel groß nach oben außen, Hinterflügel kleiner nach unten
      lobe(c, a, -112.0F, -4.0F, 22.0F, 0.0F, 0);
      lobe(c, a, 6.0F, 82.0F, 15.0F, -0.35F, 1);
   }

   private static void lobe(WingsModel.Ctx c, WingsModel.Anim a, float from, float to, float size, float z, int part) {
      WingsModel.Look k = c.k;
      int n = 16;
      float[] xs = new float[n + 1];
      float[] ys = new float[n + 1];

      for (int i = 0; i <= n; i++) {
         float u = i / (float)n;
         float th = rad(lerp(from, to, u) + wave(a, u) * 0.5F);
         float r = size * (0.3F + 0.7F * (float)Math.pow(Mth.sin((float)Math.PI * u), 0.55)) * (part == 0 ? lerp(0.85F, 1.12F, u) : lerp(1.05F, 0.8F, u));
         xs[i] = Mth.cos(th) * r;
         ys[i] = Mth.sin(th) * r;
      }

      for (int i = 0; i < n; i++) {
         // schwarzer Rand (etwas größer, dahinter), Fläche mit Verlauf, helleres Innenfeld
         // schwarzer Rand als Ring, Fläche mit Verlauf, helleres Innenfeld auf beiden Seiten
         c.tri(xs[i] * 0.86F, ys[i] * 0.86F, xs[i] * 1.1F, ys[i] * 1.1F, xs[i + 1] * 1.1F, ys[i + 1] * 1.1F, z, k.line(), k.line(), k.line(), false, 1.0F);
         c.tri(xs[i] * 0.86F, ys[i] * 0.86F, xs[i + 1] * 1.1F, ys[i + 1] * 1.1F, xs[i + 1] * 0.86F, ys[i + 1] * 0.86F, z, k.line(), k.line(), k.line(), false, 1.0F);
         c.tri(0.0F, 0.0F, xs[i] * 0.86F, ys[i] * 0.86F, xs[i + 1] * 0.86F, ys[i + 1] * 0.86F, z, k.root(), k.tip(), k.tip(), false, 1.0F);
         int bright = mix(k.tip(), 0xFFFFFFFF, 0.3F);
         c.tri(0.0F, 0.0F, xs[i] * 0.6F, ys[i] * 0.6F, xs[i + 1] * 0.6F, ys[i + 1] * 0.6F, z + 0.06F, k.tip(), bright, bright, false, 1.0F);
         c.tri(0.0F, 0.0F, xs[i] * 0.6F, ys[i] * 0.6F, xs[i + 1] * 0.6F, ys[i + 1] * 0.6F, z - 0.06F, k.tip(), bright, bright, false, 1.0F);
         if (i % 2 == 0) {
            // Adern
            c.seg(0.0F, 0.0F, xs[i] * 0.88F, ys[i] * 0.88F, z, 0.3F, 0.3F, k.line(), false, 1.0F);
         }

         if (i % 3 == 1) {
            // weiße Punkte im Rand
            float px = xs[i] * 1.05F;
            float py = ys[i] * 1.05F;
            c.lbox(px - 0.45F, px + 0.45F, py - 0.45F, py + 0.45F, z - 0.12F, z + 0.12F, 0xFFF4F4FF, false, 1.0F);
            if (c.glow) {
               float tw = 0.5F + 0.5F * Mth.sin(c.time * 2.5F + i + part * 3);
               c.lbox(px - 0.7F, px + 0.7F, py - 0.7F, py + 0.7F, z - 0.05F, z + 0.18F, k.glowB(), true, 0.2F + 0.4F * tw);
            }
         }

         if (c.glow) {
            float pulse = 0.5F + 0.5F * Mth.sin(c.time * 1.6F - i * 0.35F);
            c.tri(0.0F, 0.0F, xs[i], ys[i], xs[i + 1], ys[i + 1], z + 0.12F, 0xFF000000, k.glowA(), k.glowA(), true, 0.1F + 0.14F * pulse);
         }
      }
   }

   // ================================================================== Libelle

   private static void dragonfly(WingsModel.Ctx c, WingsModel.Anim a) {
      // schnelles Flirren wie bei einer echten Libelle
      float buzz = Mth.sin(a.time() * 26.0F) * (2.0F + a.flap() * 4.0F);
      slimWing(c, 0.0F, -1.5F, -16.0F + buzz, 24.0F, 5.0F, 0.0F, 0);
      slimWing(c, 0.0F, 1.5F, 14.0F - buzz, 21.0F, 4.6F, -0.3F, 1);
   }

   private static void slimWing(WingsModel.Ctx c, float x, float y, float angle, float len, float w, float z, int idx) {
      WingsModel.Look k = c.k;
      int n = 12;
      float[] px = new float[n + 1];
      float[] ph = new float[n + 1];

      for (int i = 0; i <= n; i++) {
         float u = i / (float)n;
         px[i] = u * len;
         // schmal am Ansatz, breit in der Mitte, rund an der Spitze
         ph[i] = w / 2.0F * (float)Math.pow(Mth.sin((float)Math.PI * Math.min(1.0F, 0.12F + u * 0.92F)), 0.6) * (0.55F + 0.45F * u);
      }

      c.enter(x, y, z, angle);

      for (int i = 0; i < n; i++) {
         // Haut: durchsichtig schimmernd (Leuchtebene), Farbverlauf entlang des Flügels
         int c1 = hsv(0.5F + 0.25F * (i / (float)n) + 0.05F * Mth.sin(c.time * 0.8F + idx), 0.6F, 1.0F);
         int c2 = hsv(0.5F + 0.25F * ((i + 1) / (float)n) + 0.05F * Mth.sin(c.time * 0.8F + idx), 0.6F, 1.0F);
         c.tri(px[i], -ph[i], px[i + 1], -ph[i + 1], px[i + 1], ph[i + 1], 0.0F, c1, c2, c2, true, 0.3F);
         c.tri(px[i], -ph[i], px[i + 1], ph[i + 1], px[i], ph[i], 0.0F, c1, c2, c1, true, 0.3F);
         // leicht getönte feste Haut, damit sie auch ohne Effekte sichtbar bleibt
         c.tri(px[i], -ph[i], px[i + 1], -ph[i + 1], px[i + 1], ph[i + 1], -0.05F, mix(k.root(), k.tip(), 0.25F), mix(k.root(), k.tip(), 0.35F), mix(k.root(), k.tip(), 0.35F), false, 1.0F);
         c.tri(px[i], -ph[i], px[i + 1], ph[i + 1], px[i], ph[i], -0.05F, mix(k.root(), k.tip(), 0.25F), mix(k.root(), k.tip(), 0.35F), mix(k.root(), k.tip(), 0.25F), false, 1.0F);
         // Rand und Queradern
         c.seg(px[i], -ph[i], px[i + 1], -ph[i + 1], 0.05F, 0.4F, 0.3F, k.line(), false, 1.0F);
         c.seg(px[i], ph[i], px[i + 1], ph[i + 1], 0.05F, 0.3F, 0.3F, k.line(), false, 1.0F);
         if (i % 2 == 1) {
            c.seg(px[i], -ph[i], px[i], ph[i], 0.05F, 0.22F, 0.25F, mix(k.line(), k.tip(), 0.4F), false, 1.0F);
         }
      }

      // Längsader und dunkles Flügelmal an der Spitze
      c.seg(0.0F, -ph[n / 2] * 0.35F, len * 0.95F, -ph[n - 1] * 0.4F, 0.08F, 0.35F, 0.3F, k.line(), false, 1.0F);
      c.lbox(len * 0.8F, len * 0.9F, -ph[n - 2] * 0.95F, -ph[n - 2] * 0.45F, -0.05F, 0.2F, k.line(), false, 1.0F);
      c.leave();
   }

   // ================================================================== Farben

   static int mix(int a, int b, float t) {
      int r = (int)((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
      int g = (int)((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
      int bl = (int)((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
      return 0xFF000000 | r << 16 | g << 8 | bl;
   }

   private static float frac(float v) {
      return v - (float)Math.floor(v);
   }

   private static float rand(int n) {
      int h = n * 374761393 + 668265263;
      h = (h ^ h >>> 13) * 1274126177;
      return ((h ^ h >>> 16) & 16777215) / 1.6777216E7F;
   }

   public static int hsv(float h, float s, float v) {
      return 0xFF000000 | java.awt.Color.HSBtoRGB(frac(h), s, v) & 16777215;
   }

   /** Farbton einer Farbe um deg Grad drehen. */
   public static int shiftHue(int argb, float deg) {
      float[] hsb = java.awt.Color.RGBtoHSB(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, null);
      return argb & 0xFF000000 | java.awt.Color.HSBtoRGB(frac(hsb[0] + deg / 360.0F), hsb[1], hsb[2]) & 16777215;
   }
}
