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
 * Butterfly-Messer (Balisong) in der Hand: zugeklappt liegt es waagerecht in der Faust; mit der Inspect-Taste klappt es
 * mit Drehungen der Griffe auf (Klinge nach oben, Griffe als V gespreizt) und wieder zu.
 */
public final class ButterflyKnife {
   private static final float OPEN_TIME = 1.25F;
   /** Dauer des Tricks (für die Handbewegung). */
   public static final float TRICK_TIME = 1.25F;
   /** Klingenrichtung im Sichtraum: zugeklappt nach links, offen nach oben rechts. */
   private static final Vector3f OPEN = new Vector3f(0.42F, 1.0F, -0.22F).normalize();
   /** Spreizung des zweiten Griffs im offenen Zustand (Winkel zur Klinge). */
   private static final float SPREAD = 152.0F;
   private static final float SIZE = 1.35F;
   private static long animStart = -1L;

   private ButterflyKnife() {
   }

   /** Inspect-Taste: Trick-Animation (Griffe wirbeln, Messer rollt) – das Messer bleibt danach offen in der Hand. */
   public static void toggle() {
      if (animStart < 0L) {
         animStart = System.currentTimeMillis();
      }
   }

   /** Messer weggesteckt: beim nächsten Ziehen einmal den Trick zeigen. */
   public static void reset() {
      drawPending = true;
      animStart = -1L;
   }

   private static boolean drawPending = true;

   /** Sekunden seit Beginn des laufenden Tricks, sonst -1. */
   public static float trickTime() {
      if (animStart < 0L) {
         return -1.0F;
      }

      float t = (System.currentTimeMillis() - animStart) / 1000.0F;
      return t >= OPEN_TIME ? -1.0F : t;
   }

   private static float smooth(float x) {
      x = Math.max(0.0F, Math.min(1.0F, x));
      return x * x * (3.0F - 2.0F * x);
   }

   private static float easeOut(float x) {
      x = Math.max(0.0F, Math.min(1.0F, x));
      return 1.0F - (1.0F - x) * (1.0F - x) * (1.0F - x);
   }

   /** Aktuelle Haltung: Klingenrichtung, Rollen des ganzen Messers, Winkel der beiden Griffe. */
   private record Pose(Vector3f dir, float spin, float handleA, float handleB) {
   }

   private static ButterflyKnife.Pose pose() {
      long now = System.currentTimeMillis();
      if (drawPending) {
         drawPending = false;
         animStart = now;
      }

      float sway = (float)Math.sin(now / 1000.0 * 1.6) * 4.0F;
      if (animStart >= 0L) {
         float t = (now - animStart) / 1000.0F;
         if (t >= OPEN_TIME) {
            animStart = -1L;
         } else {
            // Trick wie im Video: Griff B wirbelt zweimal ganz herum, das Messer rollt einmal und wackelt kurz nach
            float b = SPREAD + 720.0F * easeOut(t / OPEN_TIME);
            float spin = 360.0F * smooth(t / 0.85F);
            float wobble = (float)Math.sin(t * 18.0F) * 12.0F * (1.0F - smooth(t / OPEN_TIME));
            Vector3f dir = new Vector3f(OPEN).add(new Vector3f(-0.35F, -0.25F, 0.0F).mul((float)Math.sin(Math.PI * Math.min(1.0F, t / 0.9F)))).normalize();
            return new ButterflyKnife.Pose(dir, spin + wobble, 180.0F, b);
         }
      }

      return new ButterflyKnife.Pose(OPEN, sway, 180.0F, SPREAD + sway * 2.0F);
   }
   /** Drehung im Raum der Hand, damit die Klinge im Sichtraum in Richtung {@code dir} zeigt (flache Seite zur Kamera). */
   private static Quaternionf armSpace(Vector3f dir) {
      Vector3f y = new Vector3f(dir).normalize();
      Vector3f x = new Vector3f(0.0F, 0.0F, 1.0F);
      x.sub(new Vector3f(y).mul(x.dot(y))).normalize();
      Vector3f z = new Vector3f(x).cross(y).normalize();
      Quaternionf view = new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
      view = new Quaternionf().rotateY((float)Math.toRadians(-20.0)).mul(view);
      return KarambitHand.armRest().conjugate().mul(view);
   }

   /** Ich-Ansicht: Matrizen im Raum des Arm-Modellteils (wie nach dem Zeichnen der leeren Hand). */
   public static void drawFirstPerson(PoseStack m, SubmitNodeCollector queue, boolean right) {
      PlasmaSword.Look look = PlasmaMode.currentLook(PlasmaSword.BUTTERFLY);
      if (look == null) {
         return;
      }

      ButterflyKnife.Pose p = pose();
      float scale = look.bladeLength() / 0.95F;
      m.pushPose();
      if (!right) {
         m.scale(-1.0F, 1.0F, 1.0F);
      }

      m.translate(-5.0F / 16.0F, 2.0F / 16.0F, 0.0F);
      m.rotate(Axis.ZP.rotation(0.1F));
      // Faust
      m.translate(-1.0F / 16.0F, 9.0F / 16.0F, -0.6F / 16.0F);
      m.rotate(armSpace(p.dir()));
      m.rotate(Axis.XP.rotationDegrees(p.spin()));
      m.scale(SIZE, SIZE, SIZE);
      // Faust: zugeklappt am Griffende, offen knapp unter dem Drehpunkt auf Griff A
      float grip = (0.12F + 0.24F * (float)Math.cos(Math.toRadians(p.handleA()))) * scale;
      m.translate(0.0F, -grip, 0.0F);
      PlasmaSword.submitButterfly(m, queue, look, p.handleA(), p.handleB());
      m.popPose();
   }

   /** Dritte Person/Vorschau (Matrizen in der Faust): offen gehalten, Klinge nach oben. */
   public static void thirdPersonPose(PoseStack m) {
      m.rotate(armSpace(OPEN));
      m.scale(0.85F, 0.85F, 0.85F);
      m.translate(0.0F, 0.12F, 0.0F);
   }
}
