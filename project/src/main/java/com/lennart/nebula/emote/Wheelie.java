package com.lennart.nebula.emote;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;

/**
 * E-Scooter-Emote: Shift (Schleichen) gedrückt = Wheelie. Spieler und Roller kippen gemeinsam um die Aufstandsfläche des
 * Hinterrads nach hinten, das Vorderrad hebt ab. Schleichen wird an andere übertragen, daher sehen es alle.
 */
public final class Wheelie {
   /** Hinterrad-Aufstandspunkt im Raum nach setupRotations (Blöcke, vorne = -z, Boden des Rollers). */
   private static final float PIVOT_Y = -0.374F;
   private static final float PIVOT_Z = 0.78F;
   private static final float ANGLE = 24.0F;
   private static final Map<Integer, double[]> STATE = new HashMap<>();

   private Wheelie() {
   }

   /** Weicher Übergang 0..1 je Spieler (zeitbasiert, mehrfach pro Bild aufrufbar). */
   public static float amount(int id, boolean want) {
      double now = System.nanoTime();
      double[] s = STATE.computeIfAbsent(id, k -> new double[]{0.0, now});
      double dt = Math.min(0.1, (now - s[1]) / 1.0E9);
      s[1] = now;
      double speed = want ? 3.2 : 4.5;
      s[0] = want ? Math.min(1.0, s[0] + dt * speed) : Math.max(0.0, s[0] - dt * speed);
      float x = (float)s[0];
      return x * x * (3.0F - 2.0F * x);
   }
   /** Ganzen Spieler samt Roller hochkippen (nach den normalen Körperdrehungen). */
   public static void tilt(PoseStack m, float k, float time) {
      if (k <= 0.001F) {
         return;
      }

      // leichtes Balancieren, solange das Vorderrad oben ist
      float angle = k * (ANGLE + 2.5F * (float)Math.sin(time * 3.7F) * k);
      m.translate(0.0F, PIVOT_Y, PIVOT_Z);
      m.mulPose(Axis.XP.rotationDegrees(angle));
      m.translate(0.0F, -PIVOT_Y, -PIVOT_Z);
   }

   /** Haltung beim Wheelie: Ducken aufheben, Oberkörper leicht nach vorn, Blick nach vorn, Arme ziehen am Lenker. */
   public static void pose(HumanoidModel<?> m, float k) {
      // Vanilla-Ducken (Körper gesenkt, Beine nach hinten) zurücknehmen
      m.head.y = 0.0F;
      m.body.y = 0.0F;
      m.rightArm.y = 2.0F;
      m.leftArm.y = 2.0F;
      m.rightLeg.y = 12.0F;
      m.leftLeg.y = 12.0F;
      m.rightLeg.z = 0.0F;
      m.leftLeg.z = 0.0F;
      m.body.xRot = 0.22F * k;
      m.head.xRot = m.head.xRot * (1.0F - k) + 0.35F * k;
      m.rightArm.xRot = -1.15F * k + m.rightArm.xRot * (1.0F - k);
      m.leftArm.xRot = -1.15F * k + m.leftArm.xRot * (1.0F - k);
      m.rightLeg.xRot = -0.18F * k;
      m.leftLeg.xRot = 0.18F * k;
   }
}
