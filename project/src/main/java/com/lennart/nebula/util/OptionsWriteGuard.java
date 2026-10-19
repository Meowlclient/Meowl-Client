package com.lennart.nebula.util;

public final class OptionsWriteGuard {
   private static boolean writing;

   private OptionsWriteGuard() {
   }

   public static boolean isWriting() {
      return writing;
   }

   public static void setWriting(boolean value) {
      writing = value;
   }
}
