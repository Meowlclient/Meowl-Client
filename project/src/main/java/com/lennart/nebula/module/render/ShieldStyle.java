package com.lennart.nebula.module.render;

import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;

/** Schilde anpassen: durchsichtig (man sieht durch das eigene Schild) und eingefärbt. Nur bei dir sichtbar. */
public class ShieldStyle extends Module {
   private static final String FIRST = "Ego-Sicht";
   private static final String HANDS = "In der Hand (auch andere)";
   private static final String ALL = "Überall (auch Inventar)";
   private static ShieldStyle instance;
   private final BooleanSetting seeThrough = this.add(new BooleanSetting("Durchsichtig", "Man kann durch das Schild hindurchsehen", true));
   private final NumberSetting opacity = this.add(new NumberSetting("Deckkraft", "Wie sichtbar das Schild noch ist", 35.0, 5.0, 100.0, 5.0, "%"));
   private final BooleanSetting tint = this.add(new BooleanSetting("Einfärben", "Schild in einer Farbe einfärben", false));
   private final ColorSetting color = this.add(new ColorSetting("Farbe", "Farbe des Schilds", 0xFF6EC8FF, false));
   private final ModeSetting where = this.add(new ModeSetting("Gilt für", "Wo das Schild verändert wird", FIRST, FIRST, HANDS, ALL));

   public ShieldStyle() {
      super("Shield", "Schilde durchsichtig machen, damit man beim Blocken durchsehen kann, und einfärben.", Category.RENDER);
      this.section(Section.PVP);
      this.icon(Items.SHIELD);
      instance = this;
      this.opacity.setVisibility(this.seeThrough::get);
      this.color.setVisibility(this.tint::get);
      this.group("Schild", new Setting[]{this.seeThrough, this.opacity, this.tint, this.color, this.where});
   }

   /** Ingame-Vorschau beim Ziehen an den Reglern: Schild in der Nebenhand. */
   @Override
   public net.minecraft.world.item.ItemStack ingamePreviewItem() {
      return new net.minecraft.world.item.ItemStack(Items.SHIELD);
   }

   @Override
   public boolean ingamePreviewOffhand() {
      return true;
   }

   /** Gilt die Anpassung für diese Darstellung? {@code ctx} = null, wenn die Version es nicht verrät (dann: in der Hand). */
   private static boolean applies(ItemDisplayContext ctx) {
      ShieldStyle s = instance;
      if (s == null || !s.isEnabled()) {
         return false;
      } else if (s.where.is(ALL) || ctx == null) {
         return true;
      } else if (s.where.is(FIRST)) {
         return ctx.firstPerson();
      } else {
         return ctx.firstPerson() || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
      }
   }

   /** Durchsichtige Darstellung (Render-Typ mit Transparenz) nötig? */
   public static boolean translucent(ItemDisplayContext ctx) {
      return applies(ctx) && instance.seeThrough.get() && instance.opacity.get() < 100.0;
   }

   /** Farbe (ARGB) für das Schild: vanilla = -1 (weiß, deckend). */
   public static int color(ItemDisplayContext ctx, int vanilla) {
      if (!applies(ctx)) {
         return vanilla;
      } else {
         int rgb = instance.tint.get() ? instance.color.get() & 16777215 : vanilla & 16777215;
         int alpha = instance.seeThrough.get() ? (int)Math.round(instance.opacity.get() / 100.0 * 255.0) : 255;
         return alpha << 24 | rgb;
      }
   }
}
