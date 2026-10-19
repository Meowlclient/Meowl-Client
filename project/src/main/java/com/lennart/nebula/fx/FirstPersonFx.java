package com.lennart.nebula.fx;

import com.mojang.blaze3d.vertex.PoseStack;

public final class FirstPersonFx {
   private static FirstPersonFx.Item item;
   private static FirstPersonFx.Arm arm;
   private static FirstPersonFx.Arm offArm;
   private static FirstPersonFx.Arm watchMain;
   private static FirstPersonFx.Arm watchOff;

   private FirstPersonFx() {
   }

   public static void setItemAnimation(FirstPersonFx.Item animation) {
      item = animation;
   }

   public static void setArmAnimation(FirstPersonFx.Arm animation) {
      arm = animation;
   }

   public static void setOffArmAnimation(FirstPersonFx.Arm animation) {
      offArm = animation;
   }

   public static void setWatchArms(FirstPersonFx.Arm main, FirstPersonFx.Arm off) {
      watchMain = main;
      watchOff = off;
   }

   public static boolean applyOffArm(PoseStack matrices, float tickProgress, boolean rightHanded) {
      return offArm != null && offArm.apply(matrices, tickProgress, rightHanded)
         ? true
         : watchOff != null && watchOff.apply(matrices, tickProgress, rightHanded);
   }

   public static boolean applyItem(PoseStack matrices, float tickProgress, boolean rightHanded) {
      return item != null && item.apply(matrices, tickProgress, rightHanded);
   }

   public static boolean applyArm(PoseStack matrices, float tickProgress, boolean rightHanded) {
      return arm != null && arm.apply(matrices, tickProgress, rightHanded) ? true : watchMain != null && watchMain.apply(matrices, tickProgress, rightHanded);
   }

   @FunctionalInterface
   public interface Arm {
      boolean apply(PoseStack var1, float var2, boolean var3);
   }

   @FunctionalInterface
   public interface Item {
      boolean apply(PoseStack var1, float var2, boolean var3);
   }
}
