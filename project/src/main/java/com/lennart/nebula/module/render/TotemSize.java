package com.lennart.nebula.module.render;

import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Totem-Größe: wie groß die Totem-Animation beim Auslösen und das Totem in der Hand (Ego-Sicht) erscheinen. */
public class TotemSize extends Module {
   private static TotemSize instance;
   private final BooleanSetting showPop = this.add(new BooleanSetting("Totem-Animation", "Die große Totem-Animation beim Auslösen zeigen", true));
   private final NumberSetting popSize = this.add(new NumberSetting("Größe der Animation", "Größe der Totem-Animation beim Auslösen", 1.0, 0.2, 2.0, 0.05, "x"));
   private final NumberSetting handSize = this.add(new NumberSetting("Größe in der Hand", "Größe des Totems in deiner Hand (Ego-Sicht)", 1.0, 0.3, 1.6, 0.05, "x"));

   public TotemSize() {
      super("Totem Size", "Größe der Totem-Animation beim Auslösen und des Totems in der Hand einstellen – oder die Animation ausblenden.", Category.RENDER);
      this.section(Section.PVP);
      this.icon(Items.TOTEM_OF_UNDYING);
      instance = this;
      this.popSize.setVisibility(this.showPop::get);
      this.group("Totem", new Setting[]{this.showPop, this.popSize, this.handSize});
   }

   private long lastPreviewPop;

   /** Beim Ziehen an den Reglern: die Totem-Animation in der eingestellten Größe immer wieder abspielen. */
   @Override
   public void ingamePreviewTick() {
      long now = System.currentTimeMillis();
      if (this.showPop.get() && now - this.lastPreviewPop > 1300L) {
         this.lastPreviewPop = now;
         try {
            com.lennart.nebula.compat.ItemActivation.show(new ItemStack(Items.TOTEM_OF_UNDYING));
         } catch (RuntimeException var4) {
         }
      }
   }

   /** Ingame-Vorschau beim Ziehen an den Reglern: Totem in der Hand. */
   @Override
   public ItemStack ingamePreviewItem() {
      return new ItemStack(Items.TOTEM_OF_UNDYING);
   }

   private static boolean on() {
      return instance != null && instance.isEnabled();
   }

   /** Faktor für die Totem-Animation (1 = Vanilla, 0 = ausgeblendet). */
   public static float popScale() {
      if (!on()) {
         return 1.0F;
      } else {
         return instance.showPop.get() ? (float)instance.popSize.get().doubleValue() : 0.0F;
      }
   }

   /** Faktor für ein Item in der eigenen Hand (nur Totems). */
   public static float handScale(ItemStack stack) {
      return on() && stack.is(Items.TOTEM_OF_UNDYING) ? (float)instance.handSize.get().doubleValue() : 1.0F;
   }
}
