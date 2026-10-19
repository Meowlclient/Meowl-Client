package com.lennart.nebula.emote;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Körperhaltungen der Emotes (Modellraum, Bogenmaß). Rechter Arm: negatives Yaw = nach innen, positives Roll = nach
 * außen; linker Arm gespiegelt. Wird für alle Spieler mit aktivem Emote am Ende von setAngles angewendet.
 */
public final class EmotePoses {
   private EmotePoses() {
   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   private static void pose(ModelPart p, float pitch, float yaw, float roll, float t) {
      p.xRot = lerp(p.xRot, pitch, t);
      p.yRot = lerp(p.yRot, yaw, t);
      p.zRot = lerp(p.zRot, roll, t);
   }

   private static float sin(float v) {
      return (float)Math.sin(v);
   }

   /** Ein-/Ausblenden: 0..1. */
   public static float blend(EmoteState.Active a, float now) {
      float t = now - a.start;
      float in = Math.min(1.0F, t / 0.25F);
      float out = 1.0F;
      if (a.end >= 0.0F) {
         out = Math.max(0.0F, 1.0F - (now - a.end) / 0.25F);
      } else if (!a.type.loops()) {
         out = Math.max(0.0F, Math.min(1.0F, (a.type.duration - t) / 0.3F));
      }

      float b = Math.min(in, out);
      return b * b * (3.0F - 2.0F * b);
   }

   /** Wie {@link #apply}, aber beim Laufen behalten die Beine ihre Laufbewegung (Emotes laufen beim Gehen weiter). */
   public static void apply(HumanoidModel<?> m, EmoteState.Active a, float now, float walk) {
      float[] legs = {
         m.rightLeg.xRot, m.rightLeg.yRot, m.rightLeg.zRot, m.rightLeg.y, m.rightLeg.z,
         m.leftLeg.xRot, m.leftLeg.yRot, m.leftLeg.zRot, m.leftLeg.y, m.leftLeg.z
      };
      apply(m, a, now);
      float k = Math.min(1.0F, walk * 2.5F);
      if (a.type.vehicle()) {
         // im Fahrzeug laufen die Beine nicht mit
         return;
      }

      if (k > 0.02F && a.type != EmoteType.SIT) {
         restore(m.rightLeg, legs, 0, k);
         restore(m.leftLeg, legs, 5, k);
      }
   }

   private static void restore(ModelPart p, float[] s, int o, float k) {
      p.xRot = lerp(p.xRot, s[o], k);
      p.yRot = lerp(p.yRot, s[o + 1], k);
      p.zRot = lerp(p.zRot, s[o + 2], k);
      p.y = lerp(p.y, s[o + 3], k);
      p.z = lerp(p.z, s[o + 4], k);
   }

   public static void apply(HumanoidModel<?> m, EmoteState.Active a, float now) {
      float b = blend(a, now);
      if (!(b <= 0.001F)) {
         float t = now - a.start;
         ModelPart ra = m.rightArm;
         ModelPart la = m.leftArm;
         switch (a.type) {
            case KLEIN: {
               // ganze Figur schrumpft zu den Füßen hin (Füße bleiben bei y = 24)
               ModelPart root = m.root();
               float s = lerp(1.0F, 0.45F, b);
               root.xScale = s;
               root.yScale = s;
               root.zScale = s;
               root.y += 24.0F * (1.0F - s);
               // kleines Wippen, damit es lebendig wirkt
               pose(m.head, m.head.xRot, m.head.yRot, 0.08F * sin(t * 2.5F), b);
               break;
            }
            case SIT:
               pose(m.rightLeg, -1.38F, 0.24F, 0.07F, b);
               pose(m.leftLeg, -1.38F, -0.24F, -0.07F, b);
               pose(m.body, 0.14F, 0.0F, 0.0F, b);
               pose(ra, -0.62F, -0.12F, 0.06F, b);
               pose(la, -0.62F, 0.12F, -0.06F, b);
               m.rightLeg.z += 1.67F * b;
               m.leftLeg.z += 1.67F * b;
               break;
            case WAVE:
               pose(ra, -2.75F, 0.0F, 0.3F + 0.45F * sin(t * 9.0F), b);
               pose(m.head, m.head.xRot, m.head.yRot, -0.08F, b);
               break;
            case CHEER: {
               float s = sin(t * 12.0F) * 0.14F;
               pose(ra, -2.9F, 0.0F, 0.35F + s, b);
               pose(la, -2.9F, 0.0F, -0.35F - s, b);
               pose(m.head, -0.25F, m.head.yRot, 0.0F, b);
               break;
            }
            case DANCE: {
               float p = t * 5.0F;
               pose(ra, -2.6F * (0.5F + 0.5F * sin(p)), 0.0F, 0.35F, b);
               pose(la, -2.6F * (0.5F - 0.5F * sin(p)), 0.0F, -0.35F, b);
               pose(m.rightLeg, 0.35F * sin(p), 0.0F, 0.05F, b);
               pose(m.leftLeg, -0.35F * sin(p), 0.0F, -0.05F, b);
               pose(m.body, 0.0F, 0.25F * sin(p * 0.5F), 0.06F * sin(p), b);
               pose(m.head, m.head.xRot, m.head.yRot, 0.15F * sin(p), b);
               break;
            }
            case BOW: {
               float d = Math.min(1.0F, t / 0.6F) * Math.min(1.0F, Math.max(0.0F, (a.type.duration - 0.2F - t) / 0.6F));
               float k = d * d * (3.0F - 2.0F * d) * b;
               m.body.xRot += 0.7F * k;
               m.body.y += 3.2F * k;
               m.head.y += 4.2F * k;
               m.head.xRot = lerp(m.head.xRot, 0.6F, k);
               ra.y += 3.2F * k;
               la.y += 3.2F * k;
               ra.xRot += 0.7F * k;
               la.xRot = lerp(la.xRot, -0.9F, k);
               la.yRot = lerp(la.yRot, 0.6F, k);
               m.rightLeg.z += 4.0F * k;
               m.leftLeg.z += 4.0F * k;
               m.rightLeg.y += 0.2F * k;
               m.leftLeg.y += 0.2F * k;
               break;
            }
            case SALUTE:
               pose(ra, -2.45F, -0.62F, -0.18F, b);
               pose(m.head, 0.0F, m.head.yRot, 0.0F, b);
               break;
            case CLAP: {
               float y = 0.32F + 0.24F * Math.abs(sin(t * 9.0F));
               pose(ra, -1.25F, -y, 0.0F, b);
               pose(la, -1.25F, y, 0.0F, b);
               break;
            }
            case POINT:
               pose(ra, m.head.xRot - 1.57F, m.head.yRot, 0.0F, b);
               break;
            case FACEPALM:
               pose(ra, -2.45F, -0.5F, -0.1F, b);
               pose(m.head, 0.38F, 0.14F * sin(t * 6.0F), 0.0F, b);
               break;
            case DAB:
               pose(ra, -2.1F, 0.0F, 1.05F, b);
               pose(la, -1.7F, 1.05F, 0.0F, b);
               pose(m.head, 0.55F, 0.4F, 0.0F, b);
               break;
            case THINK:
               pose(ra, -2.1F, -0.55F, 0.0F, b);
               pose(la, -0.9F, 0.75F, 0.0F, b);
               pose(m.head, 0.15F, m.head.yRot * 0.5F, 0.12F + 0.03F * sin(t * 1.5F), b);
               break;
            case CROSS: {
               float breathe = 0.03F * sin(t * 2.0F);
               pose(ra, -0.95F + breathe, -0.85F, 0.1F, b);
               pose(la, -0.95F + breathe, 0.85F, -0.1F, b);
               break;
            }
            case LAUGH: {
               float s = sin(t * 22.0F);
               pose(m.head, -0.35F + 0.1F * s, m.head.yRot, 0.0F, b);
               pose(ra, -0.55F, -0.6F, 0.0F, b);
               pose(la, -0.55F, 0.6F, 0.0F, b);
               pose(m.body, -0.08F + 0.03F * s, 0.0F, 0.0F, b);
               break;
            }
            case SHRUG: {
               float up = Math.min(1.0F, t / 0.4F) * Math.min(1.0F, Math.max(0.0F, (a.type.duration - t) / 0.5F));
               pose(ra, -0.6F, 0.0F, 0.75F, b);
               pose(la, -0.6F, 0.0F, -0.75F, b);
               ra.y -= 1.2F * up * b;
               la.y -= 1.2F * up * b;
               pose(m.head, m.head.xRot, m.head.yRot, 0.18F, b * up);
               break;
            }
            case GRIDDY: {
               // Griddy: abwechselnd Ferse nach vorn tippen, Arme gegengleich pumpen, Hände wie eine Brille vor den Augen im Wechsel
               float p = t * 7.0F;
               float s = sin(p);
               float tap = Math.max(0.0F, s);
               float tap2 = Math.max(0.0F, -s);
               pose(m.rightLeg, -0.75F * tap + 0.25F * tap2, 0.0F, 0.06F, b);
               pose(m.leftLeg, -0.75F * tap2 + 0.25F * tap, 0.0F, -0.06F, b);
               m.rightLeg.z -= 1.2F * tap * b;
               m.leftLeg.z -= 1.2F * tap2 * b;
               boolean glasses = sin(t * 1.75F) > 0.55F;
               if (glasses) {
                  // „Brille“: Hände als Ringe vor den Augen
                  pose(ra, -2.55F, -0.55F, 0.0F, b);
                  pose(la, -2.55F, 0.55F, 0.0F, b);
               } else {
                  pose(ra, -0.4F - 0.9F * tap2, 0.0F, 0.22F, b);
                  pose(la, -0.4F - 0.9F * tap, 0.0F, -0.22F, b);
               }

               pose(m.body, 0.18F, 0.12F * s, 0.0F, b);
               pose(m.head, 0.05F, m.head.yRot, 0.08F * s, b);
               break;
            }
            case ROLLER: {
               // E-Scooter: Hände am Lenker, ein Fuß leicht vor dem anderen
               pose(ra, -1.42F, 0.0F, 0.06F, b);
               pose(la, -1.42F, 0.0F, -0.06F, b);
               pose(m.leftLeg, -0.12F, 0.0F, -0.03F, b);
               pose(m.rightLeg, 0.12F, 0.0F, 0.03F, b);
               pose(m.head, m.head.xRot, m.head.yRot, 0.03F * sin(t * 3.0F), b);
               break;
            }
            case AUTO:
            case HELI: {
               // sitzen; im Auto am Lenkrad, im Hubschrauber am Steuerknüppel
               pose(m.rightLeg, -1.38F, 0.1F, 0.04F, b);
               pose(m.leftLeg, -1.38F, -0.1F, -0.04F, b);
               if (a.type == EmoteType.AUTO) {
                  float steer = 0.08F * sin(t * 1.3F);
                  pose(ra, -1.2F + steer, -0.1F, 0.0F, b);
                  pose(la, -1.2F - steer, 0.1F, 0.0F, b);
               } else {
                  pose(ra, -0.85F, -0.15F, 0.0F, b);
                  pose(la, -0.7F, 0.25F, 0.0F, b);
               }
               break;
            }
            case PANZER:
               // steht in der Luke, Arme auf dem Turmrand
               pose(ra, -0.45F, 0.0F, 0.35F, b);
               pose(la, -0.45F, 0.0F, -0.35F, b);
               pose(m.rightLeg, 0.0F, 0.0F, 0.0F, b);
               pose(m.leftLeg, 0.0F, 0.0F, 0.0F, b);
               break;
            case SIX_SEVEN: {
               // „67“: beide Handflächen nach oben, abwechselnd hoch und runter wie eine Waage
               float p = sin(t * 8.0F);
               pose(ra, -1.15F + 0.38F * p, -0.18F, 0.12F, b);
               pose(la, -1.15F - 0.38F * p, 0.18F, -0.12F, b);
               pose(m.head, 0.1F + 0.05F * Math.abs(p), m.head.yRot + 0.12F * p, 0.06F * p, b);
               pose(m.body, 0.04F, 0.08F * p, 0.0F, b);
               break;
            }
         }
      }
   }

   /** Höhenversatz in Blöcken (Hinsetzen, Hüpfen beim Jubeln/Tanzen). */
   public static double yOffset(EmoteState.Active a, float now) {
      float b = blend(a, now);
      if (b <= 0.001F) {
         return 0.0;
      } else {
         float t = now - a.start;
         switch (a.type) {
            case SIT:
               return -0.62F * b;
            case CHEER:
               return 0.18F * Math.abs(sin(t * 5.5F)) * b;
            case DANCE:
               return 0.06F * Math.abs(sin(t * 5.0F)) * b;
            case LAUGH:
               return 0.03F * Math.abs(sin(t * 11.0F)) * b;
            case GRIDDY:
               return 0.07F * Math.abs(sin(t * 7.0F)) * b;
            case SIX_SEVEN:
               return 0.02F * Math.abs(sin(t * 8.0F)) * b;
            case ROLLER:
               // Brett + dicke Stollenreifen unter den Füßen
               return 0.4F * b;
            case AUTO:
               // Sitz knapp über dem Boden
               return -0.45F * b;
            case PANZER:
               return 0.375F * b;
            case HELI:
               // schwebt
               return (1.6F + 0.08F * sin(t * 1.8F)) * b;
            default:
               return 0.0;
         }
      }
   }
}
