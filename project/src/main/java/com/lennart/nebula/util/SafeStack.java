package com.lennart.nebula.util;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * ItemStack für Symbole in Menüs. In 26.x lassen sich Items im Hauptmenü (ohne Welt) noch nicht erzeugen
 * („Components not bound yet“) – dann gibt es ein leeres Symbol statt eines Absturzes.
 */
public final class SafeStack {
   private static long failedAt = -1L;

   private SafeStack() {
   }

   public static ItemStack of(ItemLike item) {
      if (item == null) {
         return ItemStack.EMPTY;
      }

      // nach einem Fehlschlag nicht in jedem Bild erneut versuchen (Ausnahmen sind teuer)
      if (failedAt > 0L && System.currentTimeMillis() - failedAt < 2000L) {
         return ItemStack.EMPTY;
      }

      try {
         ItemStack s = new ItemStack(item);
         failedAt = -1L;
         return s;
      } catch (RuntimeException var2) {
         failedAt = System.currentTimeMillis();
         return ItemStack.EMPTY;
      }
   }
}
