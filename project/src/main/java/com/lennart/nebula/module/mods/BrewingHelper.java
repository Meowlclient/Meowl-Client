package com.lennart.nebula.module.mods;

import com.lennart.nebula.gui.BrewingScreen;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.util.MeowlCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Items;

public class BrewingHelper extends Module {
   public BrewingHelper() {
      super("Brewing Helper", "Zeigt für jeden Trank Schritt für Schritt, wie man ihn braut – inklusive Länger, Stärker, Wurf- und Verweiltrank.", Category.PLAYER);
      this.section(Section.TOOLS);
      this.icon(Items.BREWING_STAND);
      this.tool();
      MeowlCommands.add("brauen", "brauen §7– Brau-Helfer öffnen (auch: brew)", root -> ClientCommandManager.literal(root).executes(ctx -> {
         this.mc.schedule(() -> this.openTool(null));
         return 1;
      }), "brew");
   }

   @Override
   public Screen createScreen(Screen parent) {
      return new BrewingScreen(parent);
   }
}
