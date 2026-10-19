package com.lennart.nebula.module;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Planeten im Meowl-Menü. ox/oy = Position in der Übersicht (Anteil von Breite/Höhe), small = kleiner Planet. */
public enum Section {
   MEOWL("Meowl", "Szenen, Notizen & Bilder", -5215233, -11853942, 0.2F, 0.3F, false, null),
   CLIENT("Client", "Anzeigen & Einstellungen", -11549697, -15582613, 0.5F, 0.27F, false, Items.TARGET),
   PVP("PvP", "Kampf, HUD & Crosshair", 0xFFFF5F5F, 0xFF4A1414, 0.8F, 0.3F, false, Items.NETHERITE_SWORD),
   COSMETICS("Kosmetik", "Emotes, Pets & Looks", 0xFFFFC857, 0xFF4A3510, 0.09F, 0.74F, true, Items.TOTEM_OF_UNDYING),
   FRIENDS("Freunde", "Deine Freunde", 0xFFFF7EB6, 0xFF4A1530, 0.33F, 0.76F, false, Items.CAKE),
   GAMES("Games", "Spiele mit Freunden", 0xFF5BE38A, 0xFF123D22, 0.67F, 0.76F, false, Items.TNT),
   TOOLS("Tools", "Rechner & Vorschauen", 0xFFFF9F45, 0xFF4A2A0E, 0.91F, 0.74F, true, Items.ANVIL);

   private final String displayName;
   private final String subtitle;
   private final int color;
   private final int dark;
   private final float ox;
   private final float oy;
   private final boolean small;
   private final Item icon;

   private Section(String displayName, String subtitle, int color, int dark, float ox, float oy, boolean small, Item icon) {
      this.displayName = displayName;
      this.subtitle = subtitle;
      this.color = color;
      this.dark = dark;
      this.ox = ox;
      this.oy = oy;
      this.small = small;
      this.icon = icon;
   }

   public String displayName() {
      return this.displayName;
   }

   public String subtitle() {
      return this.subtitle;
   }

   public int color() {
      return this.color;
   }

   public int dark() {
      return this.dark;
   }

   public float ox() {
      return this.ox;
   }

   public float oy() {
      return this.oy;
   }

   public boolean small() {
      return this.small;
   }

   /** Symbol in der Mitte des Planeten (null = Meowl-Sprite). */
   public Item icon() {
      return this.icon;
   }
}
