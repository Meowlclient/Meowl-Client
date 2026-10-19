package com.lennart.nebula.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

public final class CameraFx {
   private static float roll;
   private static float pitch;
   private static float shakeStrength;
   private static long shakeStart;
   private static long shakeDuration;

   private CameraFx() {
   }

   public static void setTilt(float rollDegrees, float pitchDegrees) {
      roll = rollDegrees;
      pitch = pitchDegrees;
   }

   public static void shake(float strength, long durationMs) {
      shakeStrength = Math.max(shakeStrength * remaining(), strength);
      shakeStart = System.currentTimeMillis();
      shakeDuration = durationMs;
   }

   private static float remaining() {
      if (shakeDuration <= 0L) {
         return 0.0F;
      } else {
         float t = (float)(System.currentTimeMillis() - shakeStart) / (float)shakeDuration;
         return t >= 1.0F ? 0.0F : (1.0F - t) * (1.0F - t);
      }
   }

   public static void apply(PoseStack matrices) {
      float s = shakeStrength * remaining();
      float shakeRoll = 0.0F;
      float shakePitch = 0.0F;
      if (s > 0.001F) {
         double t = System.nanoTime() / 1.0E9;
         shakeRoll = (float)(Math.sin(t * 61.0) * 0.6 + Math.sin(t * 37.0) * 0.4) * s;
         shakePitch = (float)(Math.sin(t * 53.0) * 0.5 + Math.sin(t * 29.0) * 0.5) * s * 0.6F;
      }

      float r = roll + shakeRoll;
      float p = pitch + shakePitch;
      if (r != 0.0F) {
         matrices.rotate(Axis.ZP.rotationDegrees(r));
      }

      if (p != 0.0F) {
         matrices.rotate(Axis.XP.rotationDegrees(p));
      }
   }
}
