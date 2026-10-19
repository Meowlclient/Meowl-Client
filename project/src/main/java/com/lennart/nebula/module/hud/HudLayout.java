package com.lennart.nebula.module.hud;

public final class HudLayout {
   private static volatile boolean editing;

   private HudLayout() {
   }

   public static boolean isEditing() {
      return editing;
   }

   public static void setEditing(boolean value) {
      editing = value;
   }
}
