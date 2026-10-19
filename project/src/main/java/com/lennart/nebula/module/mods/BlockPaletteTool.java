package com.lennart.nebula.module.mods;

import com.lennart.nebula.gui.BlockPaletteScreen;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.util.MeowlCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Items;

public class BlockPaletteTool extends Module {
   public BlockPaletteTool() {
      super("Block Palette", "Farben eingeben und einen Farbverlauf aus passenden Minecraft-Blöcken bekommen – Länge frei einstellbar.", Category.WORLD);
      this.section(Section.TOOLS);
      this.icon(Items.BRUSH);
      this.tool();
      MeowlCommands.add("palette", "palette §7– Block-Palette öffnen (auch: verlauf)", root -> ClientCommandManager.literal(root).executes(ctx -> {
         this.mc.schedule(() -> this.openTool(null));
         return 1;
      }), "verlauf");
   }

   @Override
   public Screen createScreen(Screen parent) {
      return new BlockPaletteScreen(parent);
   }
}