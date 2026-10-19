package com.lennart.nebula.module;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public enum Category {
   HUD("HUD", "Anzeigen im Spiel-Bildschirm", Items.ITEM_FRAME, -10443270),
   RENDER("Render", "Darstellung der Welt und von Spielern", Items.ENDER_EYE, -13315175),
   MOVEMENT("Movement", "Bewegung", Items.FEATHER, -278748),
   COMBAT("Combat", "Kampf", Items.IRON_SWORD, -495247),
   PLAYER("Player", "Inventar und Spieler", Items.CHEST, -680437),
   WORLD("World", "Dinge in der Welt", Items.GRASS_BLOCK, -11870592),
   MEOWL("Meowl", "Meowl-Features mit Freunden", Items.AMETHYST_SHARD, -4160260),
   MISC("Misc", "Sonstiges", Items.COMPARATOR, -7035976),
   @Deprecated
   MODS("Mods", "Ältere Addon-Module", Items.BOOK, -7035976);

   private final String displayName;
   private final String description;
   private final Item icon;
   private final int color;

   private Category(String displayName, String description, Item icon, int color) {
      this.displayName = displayName;
      this.description = description;
      this.icon = icon;
      this.color = color;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public String getDescription() {
      return this.description;
   }

   public ItemStack icon() {
      return com.lennart.nebula.util.SafeStack.of(this.icon);
   }

   public int color() {
      return this.color;
   }
}
