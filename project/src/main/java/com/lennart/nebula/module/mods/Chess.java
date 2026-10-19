package com.lennart.nebula.module.mods;

import com.lennart.nebula.games.GameNet;
import com.lennart.nebula.gui.ChessScreen;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.util.MeowlCommands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Items;

public class Chess extends Module {
   public Chess() {
      super("Schach", "Schach mit allen Regeln – gegen den Meowl-Bot (3 Stufen) oder online gegen deine Freunde.", Category.MEOWL);
      this.section(Section.GAMES);
      this.icon(Items.CHISELED_QUARTZ_BLOCK);
      this.tool();
      GameNet.get().hook();
      GameNet.get().registerOpener("chess", () -> this.mc.setScreen(new ChessScreen(this.mc.screen)));
      MeowlCommands.add(
         "chess",
         "chess [spieler] §7– Schach öffnen oder einen Freund einladen",
         root -> ClientCommandManager.literal(root).executes(c -> {
            this.mc.schedule(() -> this.openTool(null));
            return 1;
         }).then(ClientCommandManager.argument("spieler", StringArgumentType.word()).executes(c -> {
            String name = StringArgumentType.getString(c, "spieler");
            this.mc.schedule(() -> GameNet.get().invite("chess", name, com.lennart.nebula.util.ChatUtil::chat));
            return 1;
         })),
         "schach"
      );
   }

   @Override
   public Screen createScreen(Screen parent) {
      return new ChessScreen(parent);
   }
}
