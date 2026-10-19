package com.lennart.nebula.module.mods;

import com.lennart.nebula.gui.PaintScreen;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.util.MeowlCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Items;

public class Paint extends Module {
   public Paint() {
      super("Paint", "Pixel-Bilder malen (32–128 px) und mit einem Klick als Block Image an eine Wand hängen – Freunde sehen es auch.", Category.MEOWL);
      this.section(Section.GAMES);
      this.icon(Items.PAINTING);
      this.tool();
      MeowlCommands.add("paint", "paint §7– Meowl Paint öffnen", root -> ClientCommands.literal(root).executes(c -> {
         this.mc.schedule(() -> this.openTool(null));
         return 1;
      }), "malen");
   }

   @Override
   public Screen createScreen(Screen parent) {
      return new PaintScreen(parent);
   }
}
