package com.lennart.nebula.gui;

import com.lennart.nebula.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Ingame-Vorschau beim Ziehen an einem Regler: das Menü blendet aus, und das passende Item liegt (nur bei dir, nur
 * optisch) in deiner Hand – z.B. ein Schild in der Nebenhand oder ein Totem in der Haupthand.
 */
public final class IngamePreview {
   private static ItemStack stack = ItemStack.EMPTY;
   private static boolean offhand;
   private static long until;

   private IngamePreview() {
   }

   /** Beim Ziehen: Vorschau für dieses Modul einschalten (läuft kurz nach). */
   public static boolean show(Module module, long millis) {
      if (module == null || !module.hasIngamePreview() || Minecraft.getInstance().player == null) {
         return false;
      }

      module.ingamePreviewTick();
      ItemStack s = module.ingamePreviewItem();
      stack = s == null ? ItemStack.EMPTY : s;
      offhand = module.ingamePreviewOffhand();
      until = Math.max(until, System.currentTimeMillis() + millis);
      return true;
   }

   public static void stop() {
      until = 0L;
   }

   public static boolean active() {
      return System.currentTimeMillis() < until && Minecraft.getInstance().player != null;
   }

   /** Item, das gerade statt des echten in der Haupt- bzw. Nebenhand gezeigt wird (null = echtes Item). */
   public static ItemStack override(boolean offHand) {
      return active() && offhand == offHand && !stack.isEmpty() ? stack : null;
   }
}
