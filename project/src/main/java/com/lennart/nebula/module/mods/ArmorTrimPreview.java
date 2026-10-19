package com.lennart.nebula.module.mods;

import com.lennart.nebula.module.Section;
import com.lennart.nebula.util.MeowlCommands;
import com.lennart.nebula.gui.ArmorTrimScreen;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.util.ChatUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Items;

public class ArmorTrimPreview extends Module {
   public ArmorTrimPreview() {
      super(
         "Armor Trim Preview",
         "Alle Vanilla-Armor-Trims in 3D an deinem Charakter ausprobieren: Rüstung, Muster und Material – öffnet direkt die Vorschau.",
         Category.PLAYER
      );
      this.section(Section.TOOLS);
      this.icon(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE);
      this.tool();
      MeowlCommands.add("trims", "trims §7– Armor Trim Preview öffnen", root -> ClientCommandManager.literal(root).executes(ctx -> {
         this.mc.schedule(() -> this.openTool(null));
         return 1;
      }));
   }

   @Override
   public Screen createScreen(Screen parent) {
      if (this.mc.level != null && this.mc.player != null) {
         return new ArmorTrimScreen(parent);
      } else {
         ChatUtil.actionBar("§7Armor Trim Preview: erst eine Welt betreten");
         return null;
      }
   }
}
