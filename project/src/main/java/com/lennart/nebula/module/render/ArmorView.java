package com.lennart.nebula.module.render;

import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

/**
 * Rüstung über dem Skin ein-/ausblenden (je Teil) und durchsichtig machen (samt Trims), damit man den Skin sieht – getrennt
 * für dich selbst und für andere Spieler. Nur bei dir sichtbar.
 */
public class ArmorView extends Module {
   private static ArmorView instance;
   /** Deckkraft des gerade gezeichneten Rüstungsteils (1 = normal), gesetzt vom Rüstungs-Layer. */
   public static float currentAlpha = 1.0F;
   private final ArmorView.Side self = new ArmorView.Side("ich", true);
   private final ArmorView.Side others = new ArmorView.Side("andere", false);

   /** Einstellungen für eine Seite (ich / andere). */
   private final class Side {
      final BooleanSetting on;
      final NumberSetting opacity;
      final BooleanSetting helmet;
      final BooleanSetting chest;
      final BooleanSetting legs;
      final BooleanSetting boots;

      Side(String who, boolean def) {
         String label = who.equals("ich") ? "bei dir" : "bei anderen Spielern";
         this.on = ArmorView.this.add(new BooleanSetting("Ändern (" + who + ")", "Rüstung " + label + " verändern", def));
         this.opacity = ArmorView.this.add(
            new NumberSetting("Deckkraft (" + who + ")", "Wie sichtbar die Rüstung " + label + " ist – 0 % = unsichtbar, nur Skin", 45.0, 0.0, 100.0, 5.0, "%")
         );
         this.helmet = ArmorView.this.add(new BooleanSetting("Helm (" + who + ")", "Helm " + label + " anzeigen", true));
         this.chest = ArmorView.this.add(new BooleanSetting("Brustplatte (" + who + ")", "Brustplatte " + label + " anzeigen", true));
         this.legs = ArmorView.this.add(new BooleanSetting("Hose (" + who + ")", "Hose " + label + " anzeigen", true));
         this.boots = ArmorView.this.add(new BooleanSetting("Schuhe (" + who + ")", "Schuhe " + label + " anzeigen", true));
         for (Setting<?> s : new Setting[]{this.opacity, this.helmet, this.chest, this.legs, this.boots}) {
            s.setVisibility(this.on::get);
         }
      }

      Setting<?>[] all() {
         return new Setting[]{this.on, this.opacity, this.helmet, this.chest, this.legs, this.boots};
      }
   }

   public ArmorView() {
      super("Rüstung", "Rüstung über dem Skin ein- oder ausblenden und durchsichtig machen (auch Trims) – getrennt für dich und andere. Nur bei dir sichtbar.", Category.RENDER);
      this.section(Section.MEOWL);
      this.icon(Items.DIAMOND_CHESTPLATE);
      instance = this;
      this.group("Bei mir", this.self.all());
      this.group("Bei anderen", this.others.all());
   }

   private static ArmorView.Side side(int entityId) {
      ArmorView a = instance;
      if (a == null || !a.isEnabled()) {
         return null;
      }

      Minecraft mc = Minecraft.getInstance();
      ArmorView.Side s = mc.player != null && mc.player.getId() == entityId ? a.self : a.others;
      return s.on.get() ? s : null;
   }

   /** Soll dieses Rüstungsteil bei diesem Spieler gar nicht gezeichnet werden? */
   public static boolean hidden(int entityId, EquipmentSlot slot) {
      ArmorView.Side s = side(entityId);
      if (s == null) {
         return false;
      }

      return switch (slot) {
         case HEAD -> !s.helmet.get();
         case CHEST -> !s.chest.get();
         case LEGS -> !s.legs.get();
         case FEET -> !s.boots.get();
         default -> false;
      };
   }

   /** Deckkraft (0..1) der Rüstung dieses Spielers. */
   public static float alpha(int entityId) {
      ArmorView.Side s = side(entityId);
      return s == null ? 1.0F : (float)(s.opacity.get() / 100.0);
   }

   /** Farbe mit der aktuellen Deckkraft (für den Rüstungs-Layer). */
   public static int withAlpha(int argb) {
      if (currentAlpha >= 0.999F) {
         return argb;
      }

      int a = Math.round((argb >>> 24) * currentAlpha);
      return a << 24 | argb & 16777215;
   }
}
