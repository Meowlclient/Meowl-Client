package com.lennart.nebula.module.mods;

import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import net.minecraft.world.item.Items;

public class NoHotbarScroll extends Module {
   private static NoHotbarScroll instance;

   public NoHotbarScroll() {
      super("No Hotbar Scroll", "Das Mausrad wechselt nicht mehr den Hotbar-Slot – kein versehentliches Wegscrollen im Kampf.", Category.PLAYER);
      this.icon(Items.BARRIER);
      instance = this;
   }

   public static boolean blocks() {
      NoHotbarScroll m = instance;
      return m != null && m.isEnabled() && m.mc.player != null;
   }
}
