package com.lennart.nebula.module.mods;

import com.lennart.nebula.module.Section;
import com.lennart.nebula.util.MeowlCommands;
import com.lennart.nebula.gui.EnchantCalcScreen;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Items;

public class EnchantCalculator extends Module {
   public EnchantCalculator() {
      super(
         "Enchant Calculator", "Berechnet Amboss-Kosten exakt wie Vanilla und findet die günstigste Reihenfolge – öffnet direkt den Rechner.", Category.PLAYER
      );
      this.section(Section.TOOLS);
      this.icon(Items.ANVIL);
      this.tool();
      MeowlCommands.add("amboss", "amboss §7– Enchant Calculator öffnen (auch: enchcalc)", root -> ClientCommands.literal(root).executes(ctx -> {
         this.mc.schedule(() -> this.openTool(null));
         return 1;
      }), "enchcalc");
   }

   @Override
   public Screen createScreen(Screen parent) {
      return new EnchantCalcScreen(parent);
   }
}
