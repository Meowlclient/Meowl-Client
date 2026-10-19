package com.lennart.nebula.fx;

import com.lennart.nebula.module.mods.PlasmaMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Karambit in der Ich-Ansicht wie in CS: die Hand wird gezeichnet wie ohne Item (gleiche Größe und Haltung), das
 * Messer sitzt im Rückwärtsgriff in der Faust – Ring über dem Zeigefinger, Klinge unten aus der Faust nach vorn
 * gebogen. Dazu eine Inspect-Animation (Taste in den Minecraft-Steuerungen), die für jeden frei ist.
 */
public final class KarambitHand {
   private static final float INSPECT = 2.9F;
   /** Mitte der leeren rechten Hand im Sichtraum (aus der Vanilla-Armhaltung berechnet). */
   private static final float HX = 0.755F;
   private static final float HY = -0.438F;
   private static final float HZ = -1.095F;
   static final float FLAT = 55.0F;
   private static final Quaternionf GRIP = grip(0.0F, -20.0F);
   private static long inspectStart = -1L;

   /**
    * Ausrichtung des Messers im Raum der Hand, so berechnet, dass es in der Ich-Ansicht seitlich zu sehen ist (wie in CS):
    * flache Klingenseite zur Kamera, Klinge zeigt nach rechts und biegt sich nach oben.
    *
    * @param down Neigung der Klinge nach unten (Grad) @param turn wie weit die Fläche zur Kamera gedreht ist (Grad)
    */
   /** Ruhehaltung des leeren rechten Arms in der Ich-Ansicht (Vanilla renderPlayerArm + Arm-Drehpunkt), als Drehung. */
   public static Quaternionf armRest() {
      return new Quaternionf()
         .rotateY((float)Math.toRadians(45.0))
         .rotateZ((float)Math.toRadians(120.0))
         .rotateX((float)Math.toRadians(200.0))
         .rotateY((float)Math.toRadians(-135.0))
         .rotateZ(0.1F);
   }
   public static Quaternionf grip(float down, float turn) {
      return grip(down, turn, FLAT);
   }

   /** @param flat wie weit die Klinge um ihre Längsachse flach (waagerecht) gekippt ist (Grad) */
   public static Quaternionf grip(float down, float turn, float flat) {
      Quaternionf arm = armRest();

      // gewünschte Lage im Sichtraum: Messer-y = nach rechts, Messer-z = nach unten (Krümmung zeigt nach oben)
      Vector3f y = new Vector3f(1.0F, (float)Math.tan(Math.toRadians(down)), 0.0F).normalize();
      Vector3f z = new Vector3f(0.0F, -1.0F, 0.0F);
      z.sub(new Vector3f(y).mul(z.dot(y))).normalize();
      Vector3f x = new Vector3f(y).cross(z).normalize();
      Quaternionf view = new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
      view = new Quaternionf().rotateY((float)Math.toRadians(turn)).rotateX((float)Math.toRadians(-flat)).mul(view);
      return new Quaternionf(arm).conjugate().mul(view);
   }

   private KarambitHand() {
   }

   public static boolean active(ItemStack item) {
      return PlasmaMode.karambitFirstPerson(item);
   }

   public static void inspect() {
      inspectStart = System.currentTimeMillis();
   }

   public static void cancelInspect() {
      inspectStart = -1L;
   }

   public static boolean inspecting() {
      return time() >= 0.0F;
   }

   private static float time() {
      if (inspectStart < 0L) {
         return -1.0F;
      }

      float t = (System.currentTimeMillis() - inspectStart) / 1000.0F;
      if (t > INSPECT) {
         inspectStart = -1L;
         return -1.0F;
      }

      return t;
   }

   private static float smooth(float x) {
      x = Math.max(0.0F, Math.min(1.0F, x));
      return x * x * (3.0F - 2.0F * x);
   }

   /** 0 → 1 zwischen a und b, 1 → 0 zwischen c und d. */
   private static float env(float t, float a, float b, float c, float d) {
      return t < c ? smooth((t - a) / (b - a)) : 1.0F - smooth((t - c) / (d - c));
   }

   /** Vor dem Zeichnen des Arms: Bewegung der ganzen Hand beim Inspect (Karambit) bzw. beim Trick (Butterfly), im Sichtraum. */
   public static void armMotion(PoseStack m, boolean right) {
      float f = right ? 1.0F : -1.0F;
      if (PlasmaMode.butterfly()) {
         float bt = ButterflyKnife.trickTime();
         if (bt < 0.0F) {
            return;
         }

         // Butterfly: Hand schnellt hoch, das Handgelenk rollt mit dem wirbelnden Griff, kurzes Nachwippen
         float d = ButterflyKnife.TRICK_TIME;
         float lift = env(bt, 0.0F, 0.12F, d - 0.3F, d);
         float roll = (float)Math.sin(bt / d * Math.PI * 2.0) * 28.0F * lift;
         float bob = (float)Math.sin(bt * Math.PI * 5.0) * 0.03F * lift;
         m.translate(f * HX, HY, HZ);
         m.translate(-f * 0.06F * lift, 0.07F * lift + bob, 0.04F * lift);
         m.mulPose(Axis.ZP.rotationDegrees(f * (roll + 12.0F * lift)));
         m.mulPose(Axis.XP.rotationDegrees(-14.0F * lift + 10.0F * (float)Math.sin(bt * Math.PI * 3.0) * lift));
         m.translate(-f * HX, -HY, -HZ);
         return;
      }

      float t = time();
      if (t < 0.0F) {
         return;
      }

      // Karambit (wie in CS): anheben, Messer anwerfen, bei jeder Umdrehung am Ring mitwippen, dann Klinge zur Kamera drehen
      float lift = env(t, 0.0F, 0.35F, INSPECT - 0.45F, INSPECT);
      float toss = env(t, 0.25F, 0.4F, 0.4F, 0.6F);
      float spinPhase = t > 0.4F && t < 1.35F ? (t - 0.4F) / 0.95F : 0.0F;
      float spinBob = spinPhase > 0.0F ? (float)Math.sin(spinPhase * Math.PI * 4.0) * (1.0F - spinPhase) : 0.0F;
      float show = env(t, 1.45F, 1.8F, 2.1F, 2.45F);
      float hold = show * (float)Math.sin((t - 1.8F) * 6.0F) * 2.5F;
      m.translate(f * HX, HY, HZ);
      m.translate(-f * 0.1F * lift, 0.08F * lift + 0.035F * spinBob + 0.03F * toss, 0.06F * lift - 0.05F * show);
      m.mulPose(Axis.ZP.rotationDegrees(f * (30.0F * lift + 35.0F * show - 22.0F * toss + 9.0F * spinBob + hold)));
      m.mulPose(Axis.YP.rotationDegrees(f * (-18.0F * lift - 25.0F * show)));
      m.mulPose(Axis.XP.rotationDegrees(12.0F * lift - 10.0F * toss + 6.0F * spinBob));
      m.translate(-f * HX, -HY, -HZ);
   }
   /** Drehung des Messers um den Fingerring (zwei schnelle Umdrehungen am Anfang des Inspects). */
   private static float spin() {
      float t = time();
      if (t < 0.4F || t > 1.35F) {
         return 0.0F;
      }

      return smooth((t - 0.4F) / 0.95F) * (float)(Math.PI * 4.0);
   }

   /** Vom Raum der Hand (Arm-Modellteil, Faust bei y = 9 px) in die Lage des Messers – gleich in Ich- und dritter Person. */
   public static void gripPose(PoseStack m) {
      // Griffmitte liegt in der Faust; Klinge zeigt nach außen und biegt sich nach oben (wie in CS)
      m.mulPose(GRIP);
      m.scale(0.85F, 0.85F, 0.85F);
      m.translate(0.0F, 0.07F, 0.0F);
   }

   /** Nach dem Zeichnen des Arms (Matrizen wie für die Hand): Karambit in die Faust setzen. */
   public static void drawKnife(PoseStack m, SubmitNodeCollector queue, boolean right) {
      if (PlasmaMode.butterfly()) {
         ButterflyKnife.drawFirstPerson(m, queue, right);
         return;
      }

      m.pushPose();
      if (!right) {
         m.scale(-1.0F, 1.0F, 1.0F);
      }

      // Raum des rechten Arms (Schulter-Drehpunkt, leicht abgewinkelt wie bei Vanilla)
      m.translate(-5.0F / 16.0F, 2.0F / 16.0F, 0.0F);
      m.mulPose(Axis.ZP.rotation(0.1F));
      // in die Faust: Griff quer durch die Finger, Ring oben, Klinge unten nach vorn
      m.translate(-1.0F / 16.0F, 9.0F / 16.0F, -0.6F / 16.0F);
      gripPose(m);

      PlasmaMode.submitKarambitHand(m, queue, spin());
      m.popPose();
   }
}
