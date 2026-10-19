package com.lennart.nebula.afk;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

public final class SitPose {
   private static final float LOWER_GROUND = 0.62F;
   private static final float LOWER_LEDGE = 0.72F;
   private static boolean afk;
   private static boolean ledge;
   private static boolean emote;
   private static float progress;
   private static float duration = 0.8F;
   private static long last = System.nanoTime();

   private SitPose() {
   }

   public static void setAfk(boolean on) {
      if (on && !afk) {
         ledge = true;
      }

      afk = on;
   }

   public static void setEmote(boolean on) {
      if (on && !emote && !afk) {
         ledge = false;
      }

      emote = on;
   }

   public static boolean emote() {
      return emote;
   }

   public static void setDuration(float seconds) {
      duration = Math.max(0.15F, seconds);
   }

   public static void reset() {
      afk = false;
      emote = false;
      progress = 0.0F;
   }

   private static void update() {
      long now = System.nanoTime();
      float dt = Math.min(0.1F, (float)(now - last) / 1.0E9F);
      last = now;
      float target = !afk && !emote ? 0.0F : 1.0F;
      float step = dt / duration;
      progress = target > progress ? Math.min(target, progress + step) : Math.max(target, progress - step);
   }

   public static float progress() {
      update();
      float p = progress;
      return p * p * (3.0F - 2.0F * p);
   }

   private static boolean isLocal(int entityId) {
      Minecraft mc = Minecraft.getInstance();
      return mc.player != null && mc.player.getId() == entityId;
   }

   public static double yOffset(int entityId, boolean hasVehicle) {
      return !hasVehicle && (!(progress <= 0.0F) || afk || emote) && isLocal(entityId) ? -(ledge ? 0.72F : 0.62F) * progress() : 0.0;
   }

   private static float lerp(float a, float b, float t) {
      return a + (b - a) * t;
   }

   private static void blend(ModelPart part, float pitch, float yaw, float roll, float t) {
      part.xRot = lerp(part.xRot, pitch, t);
      part.yRot = lerp(part.yRot, yaw, t);
      part.zRot = lerp(part.zRot, roll, t);
   }

   public static void applyModel(HumanoidModel<?> model, HumanoidRenderState state, int entityId) {
      if (!state.isPassenger && (progress > 0.0F || afk || emote) && isLocal(entityId)) {
         float t = progress();
         if (!(t <= 0.001F)) {
            if (ledge) {
               float swing = (float)Math.sin(System.currentTimeMillis() / 1000.0 * 1.1) * 0.12F;
               blend(model.rightLeg, -0.38F + swing, 0.08F, 0.03F, t);
               blend(model.leftLeg, -0.38F - swing, -0.08F, -0.03F, t);
               blend(model.body, 0.1F, 0.0F, 0.0F, t);
               blend(model.rightArm, 0.22F, 0.0F, 0.2F, t);
               blend(model.leftArm, 0.22F, 0.0F, -0.2F, t);
               float back = 12.0F * (float)Math.sin(0.1F);
               model.rightLeg.z += back * t;
               model.leftLeg.z += back * t;
            } else {
               blend(model.rightLeg, -1.38F, 0.24F, 0.07F, t);
               blend(model.leftLeg, -1.38F, -0.24F, -0.07F, t);
               blend(model.body, 0.14F, 0.0F, 0.0F, t);
               blend(model.rightArm, -0.62F, -0.12F, 0.06F, t);
               blend(model.leftArm, -0.62F, 0.12F, -0.06F, t);
               float back = 12.0F * (float)Math.sin(0.14F);
               model.rightLeg.z += back * t;
               model.leftLeg.z += back * t;
               model.rightArm.z += 0.4F * t;
               model.leftArm.z += 0.4F * t;
            }
         }
      }
   }
}
