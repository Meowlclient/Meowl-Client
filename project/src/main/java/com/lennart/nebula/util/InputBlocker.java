package com.lennart.nebula.util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

public final class InputBlocker {
   private static final List<BooleanSupplier> USE = new CopyOnWriteArrayList<>();
   private static final List<BooleanSupplier> ATTACK = new CopyOnWriteArrayList<>();

   private InputBlocker() {
   }

   public static void blockUseWhen(BooleanSupplier condition) {
      USE.add(condition);
   }

   public static void blockAttackWhen(BooleanSupplier condition) {
      ATTACK.add(condition);
   }

   public static boolean isUseBlocked() {
      for (BooleanSupplier s : USE) {
         if (s.getAsBoolean()) {
            return true;
         }
      }

      return false;
   }

   public static boolean isAttackBlocked() {
      for (BooleanSupplier s : ATTACK) {
         if (s.getAsBoolean()) {
            return true;
         }
      }

      return false;
   }
}
