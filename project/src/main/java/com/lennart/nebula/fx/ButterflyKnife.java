package com.lennart.nebula.fx;

import com.lennart.nebula.module.mods.PlasmaMode;
import com.lennart.nebula.plasma.PlasmaSword;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Butterfly-Messer (Balisong) in der Ich-Ansicht. Die Faust hält immer Griff A fest; Klinge und Griff B drehen sich um
 * den Bolzen – so wie bei einem echten Balisong. Ruhehaltung wie im Referenzvideo: Klinge schräg nach oben, freier Griff
 * seitlich ausgeklappt (X-Form), mit leichtem Pendeln.
 *
 * <p>Tricks sind Keyframe-Spuren (Zeit → Handgelenk, Rollen, Klingenwinkel, Griff-B-Winkel), weich interpoliert mit
 * kubischen Hermite-Kurven, damit die Bewegung durch jeden Keyframe durchläuft statt anzuhalten. Alles hängt nur an der
 * Uhrzeit (FPS-unabhängig); es läuft immer genau ein Trick. Ein Angriff bricht den Trick ab und blendet in 0,15 s zur
 * Ruhehaltung, dazu ein kurzer Schnitt mit dem Handgelenk, synchron zum Schlag.
 */
public final class ButterflyKnife {
   /** Klingenrichtung der Ruhehaltung im Sichtraum (nach oben rechts). */
   private static final Vector3f OPEN = new Vector3f(0.42F, 1.0F, -0.22F).normalize();
   private static final float SIZE = 1.35F;
   /** Winkel des freien Griffs in der Ruhehaltung (0 = Klinge, 180 = am gehaltenen Griff). */
   private static final float IDLE_B = 140.0F;
   /** Abstand Bolzen → Faust auf Griff A (Messer-Einheiten, mal Klingen-Skalierung). */
   private static final float FIST = 0.12F;
   private static final float BLEND = 0.15F;
   static float SLASH_SIGN = 1.0F;
   /** Spalten: Zeit, Handgelenk (Drehung in der Bildebene um die Faust), Rollen um die Längsachse, Klinge, Griff B, Anheben. */
   private static final float[][] DRAW = {
      {0.0F, 180.0F, 0.0F, 180.0F, 180.0F, 0.0F},
      {0.14F, 172.0F, 0.0F, 180.0F, 228.0F, 0.6F},
      {0.32F, 125.0F, 0.0F, 142.0F, 300.0F, 1.0F},
      {0.52F, 45.0F, 0.0F, 42.0F, 420.0F, 1.0F},
      {0.7F, -8.0F, 0.0F, -6.0F, 493.0F, 0.6F},
      {0.88F, 0.0F, 0.0F, 0.0F, 500.0F, 0.0F}
   };
   /** Aerial: der freie Griff wirbelt zweimal herum, das Messer rollt einmal um seine Längsachse. */
   private static final float[][] AERIAL = {
      {0.0F, 0.0F, 0.0F, 0.0F, 140.0F, 0.0F},
      {0.12F, -15.0F, 0.0F, 0.0F, 62.0F, 0.7F},
      {0.45F, 18.0F, 180.0F, 0.0F, 300.0F, 1.0F},
      {0.8F, -10.0F, 360.0F, 0.0F, 560.0F, 1.0F},
      {1.08F, 4.0F, 360.0F, 0.0F, 840.0F, 0.6F},
      {1.3F, 0.0F, 360.0F, 0.0F, 860.0F, 0.0F}
   };
   /** Fan wie im Video: Klinge und freier Griff kreisen zweimal um den Bolzen, der freie Griff schleppt etwas nach. */
   private static final float[][] FAN = {
      {0.0F, 0.0F, 0.0F, 0.0F, 140.0F, 0.0F},
      {0.1F, -10.0F, 0.0F, -20.0F, 100.0F, 0.7F},
      {0.36F, 10.0F, 0.0F, 200.0F, 270.0F, 1.0F},
      {0.62F, -10.0F, 0.0F, 380.0F, 480.0F, 1.0F},
      {0.88F, 8.0F, 0.0F, 560.0F, 650.0F, 1.0F},
      {1.12F, -4.0F, 0.0F, 720.0F, 855.0F, 0.5F},
      {1.3F, 0.0F, 0.0F, 720.0F, 860.0F, 0.0F}
   };
   /** Rollover: zuklappen, Handgelenk dreht das geschlossene Messer nach oben, es rollt einmal, dann wieder aufklappen. */
   private static final float[][] ROLLOVER = {
      {0.0F, 0.0F, 0.0F, 0.0F, 140.0F, 0.0F},
      {0.22F, -30.0F, 0.0F, 120.0F, 165.0F, 0.8F},
      {0.42F, -125.0F, 0.0F, 180.0F, 180.0F, 1.0F},
      {0.58F, -180.0F, 0.0F, 180.0F, 180.0F, 1.0F},
      {0.95F, -180.0F, 360.0F, 180.0F, 180.0F, 1.0F},
      {1.1F, -188.0F, 360.0F, 180.0F, 228.0F, 1.0F},
      {1.28F, -235.0F, 360.0F, 142.0F, 300.0F, 1.0F},
      {1.48F, -315.0F, 360.0F, 42.0F, 420.0F, 1.0F},
      {1.66F, -368.0F, 360.0F, -6.0F, 493.0F, 0.6F},
      {1.84F, -360.0F, 360.0F, 0.0F, 500.0F, 0.0F}
   };
   private static final float[][][] TRICKS = {AERIAL, FAN, ROLLOVER};
   private static float[][] trick;
   private static long trickStart = -1L;
   private static int nextTrick;
   private static boolean drawPending = true;
   private static float[] blendFrom;
   private static long blendStart = -1L;

   private ButterflyKnife() {
   }

   /** Haltung: Handgelenk, Rollen, Klinge, Griff B (Grad) und Anheben 0..1. */
   public record Pose(float wrist, float roll, float blade, float handleB, float lift) {
      float[] array() {
         return new float[]{this.wrist, this.roll, this.blade, this.handleB, this.lift};
      }
   }

   private static float speed() {
      return PlasmaMode.trickSpeed();
   }

   /** Inspect-Taste: nächster Trick der Reihe (Aerial → Fan → Rollover). Läuft schon einer, passiert nichts. */
   public static void toggle() {
      if (trick == null) {
         start(TRICKS[nextTrick]);
         nextTrick = (nextTrick + 1) % TRICKS.length;
      }
   }

   /** Messer weggesteckt: beim nächsten Ziehen klappt es auf. */
   public static void reset() {
      drawPending = true;
      trick = null;
      trickStart = -1L;
      blendStart = -1L;
   }

   /** Angriff: laufenden Trick abbrechen und weich in die Ruhehaltung blenden. */
   public static void cancel() {
      if (trick != null) {
         blendFrom = pose(System.currentTimeMillis()).array();
         blendStart = System.currentTimeMillis();
         trick = null;
         trickStart = -1L;
      }
   }

   private static void start(float[][] keys) {
      trick = keys;
      trickStart = System.currentTimeMillis();
      blendStart = -1L;
   }

   /** Sekunden seit Beginn des laufenden Tricks (in Trick-Zeit), sonst -1. */
   public static float trickTime() {
      if (trick == null) {
         return -1.0F;
      }

      float t = (System.currentTimeMillis() - trickStart) / 1000.0F * speed();
      return t >= duration() ? -1.0F : t;
   }

   /** Dauer des laufenden Tricks (in Trick-Zeit). */
   public static float duration() {
      return trick == null ? 1.0F : trick[trick.length - 1][0];
   }

   /** Haltung zu einem Zeitpunkt; startet beim ersten Aufruf nach dem Ziehen das Aufklappen. */
   public static Pose pose(long now) {
      if (drawPending) {
         drawPending = false;
         start(DRAW);
      }

      float idleT = (now % 600000L) / 1000.0F;
      Pose idle = idle(idleT);
      if (trick != null) {
         float t = (now - trickStart) / 1000.0F * speed();
         if (t < duration()) {
            return sample(trick, t, idle);
         }

         trick = null;
         trickStart = -1L;
      }

      if (blendStart >= 0L && blendFrom != null) {
         float k = (now - blendStart) / 1000.0F / BLEND;
         if (k < 1.0F) {
            float e = smooth(k);
            float[] a = blendFrom, b = idle.array();
            float[] out = new float[5];
            for (int i = 0; i < 5; i++) {
               float from = i < 4 ? nearest(a[i], b[i]) : a[i];
               out[i] = from + (b[i] - from) * e;
            }

            return new Pose(out[0], out[1], out[2], out[3], out[4]);
         }

         blendStart = -1L;
      }

      return idle;
   }

   /** Ruhehaltung mit dezentem Pendeln des freien Griffs und Atmen des Handgelenks. */
   public static Pose idle(float t) {
      float swing = (float)Math.sin(t * 1.3F) * 8.0F + (float)Math.sin(t * 2.9F) * 2.0F;
      return new Pose((float)Math.sin(t * 0.9F) * 3.0F, 0.0F, (float)Math.sin(t * 1.6F) * 2.0F, IDLE_B + swing, 0.0F);
   }

   /**
    * Keyframe-Spur abtasten: kubische Hermite-Kurve je Kanal (Tangenten aus den Nachbarn, an den Enden null). Erster und
    * letzter Keyframe werden mit der Pendelbewegung der Ruhehaltung verrechnet, damit es nahtlos ein- und ausläuft.
    */
   public static Pose sample(float[][] k, float t, Pose idle) {
      int n = k.length;
      int i = 0;
      while (i < n - 2 && t > k[i + 1][0]) {
         i++;
      }

      float t0 = k[i][0], t1 = k[i + 1][0];
      float u = Math.max(0.0F, Math.min(1.0F, (t - t0) / (t1 - t0)));
      float[] out = new float[5];
      for (int c = 1; c <= 5; c++) {
         float p0 = k[i][c], p1 = k[i + 1][c];
         float m0 = i > 0 ? (k[i + 1][c] - k[i - 1][c]) / (k[i + 1][0] - k[i - 1][0]) * (t1 - t0) : 0.0F;
         float m1 = i + 2 < n ? (k[i + 2][c] - k[i][c]) / (k[i + 2][0] - k[i][0]) * (t1 - t0) : 0.0F;
         float u2 = u * u, u3 = u2 * u;
         out[c - 1] = (2.0F * u3 - 3.0F * u2 + 1.0F) * p0 + (u3 - 2.0F * u2 + u) * m0 + (-2.0F * u3 + 3.0F * u2) * p1 + (u3 - u2) * m1;
      }

      // Pendeln der Ruhehaltung am Anfang/Ende einblenden (0 mitten im Trick)
      float dur = k[n - 1][0];
      float rest = 1.0F - smooth(Math.min(t / 0.12F, (dur - t) / 0.12F));
      float[] sway = {idle.wrist(), idle.roll(), idle.blade(), idle.handleB() - IDLE_B};
      for (int c = 0; c < 4; c++) {
         out[c] += sway[c] * rest;
      }

      return new Pose(out[0], out[1], out[2], out[3], out[4]);
   }

   /** a auf die Umdrehung verschoben, die am nächsten an b liegt. */
   private static float nearest(float a, float b) {
      float d = (a - b) % 360.0F;
      if (d > 180.0F) {
         d -= 360.0F;
      } else if (d < -180.0F) {
         d += 360.0F;
      }

      return b + d;
   }

   private static float smooth(float x) {
      x = Math.max(0.0F, Math.min(1.0F, x));
      return x * x * (3.0F - 2.0F * x);
   }

   /** Drehung im Raum der Hand, damit die Klinge im Sichtraum in Richtung {@code dir} zeigt (flache Seite zur Kamera). */
   public static Quaternionf armSpace(Vector3f dir) {
      Vector3f y = new Vector3f(dir).normalize();
      Vector3f x = new Vector3f(0.0F, 0.0F, 1.0F);
      x.sub(new Vector3f(y).mul(x.dot(y))).normalize();
      Vector3f z = new Vector3f(x).cross(y).normalize();
      Quaternionf view = new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
      view = new Quaternionf().rotateY((float)Math.toRadians(-20.0)).mul(view);
      return KarambitHand.armRest().conjugate().mul(view);
   }

   /**
    * Messer an die Faust setzen (Matrizen im Raum der Faust): Faust hält Griff A, Klinge und Griff B drehen um den Bolzen.
    * {@code swing} = Schlagfortschritt 0..1 (0 = kein Schlag).
    */
   public static void place(PoseStack m, Pose p, float bladeScale, float swing) {
      m.rotate(armSpace(OPEN));
      if (swing > 0.0F) {
         // Schnitt: schnell ausholen und durchziehen, dann zurückfedern – im Takt des Vanilla-Schlags
         float s = (float)Math.sqrt(swing);
         float cut = (float)Math.sin(s * Math.PI);
         float pre = (float)Math.sin(Math.min(1.0F, swing * 4.0F) * Math.PI) * (1.0F - swing);
         // um die Achse quer zur Klinge: Klinge kippt nach vorn ins Ziel, der gehaltene Griff bleibt am Arm
         m.rotate(Axis.ZP.rotationDegrees(SLASH_SIGN * (70.0F * cut - 12.0F * pre)));
         m.rotate(Axis.YP.rotationDegrees(-18.0F * cut));
      }

      m.rotate(Axis.XP.rotationDegrees(p.wrist()));
      m.rotate(Axis.YP.rotationDegrees(p.roll()));
      m.scale(SIZE, SIZE, SIZE);
      m.translate(0.0F, FIST * bladeScale, 0.0F);
      m.rotate(Axis.XP.rotationDegrees(p.blade()));
   }

   /** Ich-Ansicht: Matrizen im Raum des Arm-Modellteils (wie nach dem Zeichnen der leeren Hand). */
   public static void drawFirstPerson(PoseStack m, SubmitNodeCollector queue, boolean right, float swing) {
      PlasmaSword.Look look = PlasmaMode.currentLook(PlasmaSword.BUTTERFLY);
      if (look == null) {
         return;
      }

      Pose p = pose(System.currentTimeMillis());
      float scale = look.bladeLength() / 0.95F;
      m.pushPose();
      if (!right) {
         m.scale(-1.0F, 1.0F, 1.0F);
      }

      m.translate(-5.0F / 16.0F, 2.0F / 16.0F, 0.0F);
      m.rotate(Axis.ZP.rotation(0.1F));
      // Faust
      m.translate(-1.0F / 16.0F, 9.0F / 16.0F, -0.6F / 16.0F);
      place(m, p, scale, PlasmaMode.knifeSlash() ? swing : 0.0F);
      // im Messerraum: Griff A bleibt bei 180° (nach unten in der Faust), egal wie die Klinge steht
      PlasmaSword.submitButterfly(m, queue, look, 180.0F - p.blade(), p.handleB() - p.blade());
      m.popPose();
   }

   /** Dritte Person/Vorschau (Matrizen in der Faust): offen gehalten, Klinge nach oben. */
   public static void thirdPersonPose(PoseStack m) {
      m.rotate(armSpace(OPEN));
      m.scale(0.85F, 0.85F, 0.85F);
      m.translate(0.0F, 0.12F, 0.0F);
   }
}
