package com.lennart.nebula.module.mods;

import com.lennart.nebula.games.GameNet;
import com.lennart.nebula.gui.TicTacToeScreen;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.util.MeowlCommands;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Items;

public class TicTacToe extends Module {
   public TicTacToe() {
      super("Tic-Tac-Toe", "Tic-Tac-Toe gegen den Meowl-Bot (3 Stufen) oder online gegen deine Freunde.", Category.MEOWL);
      this.section(Section.GAMES);
      this.icon(Items.CRAFTING_TABLE);
      this.tool();
      GameNet.get().hook();
      GameNet.get().registerOpener("ttt", () -> this.mc.setScreen(new TicTacToeScreen(this.mc.screen)));
      MeowlCommands.add(
         "ttt",
         "ttt [spieler] §7– Tic-Tac-Toe öffnen oder einen Freund einladen",
         root -> ClientCommandManager.literal(root).executes(c -> {
            this.mc.schedule(() -> this.openTool(null));
            return 1;
         }).then(ClientCommandManager.argument("spieler", StringArgumentType.word()).executes(c -> {
            String name = StringArgumentType.getString(c, "spieler");
            this.mc.schedule(() -> GameNet.get().invite("ttt", name, com.lennart.nebula.util.ChatUtil::chat));
            return 1;
         })),
         "tictactoe"
      );
   }

   @Override
   public Screen createScreen(Screen parent) {
      return new TicTacToeScreen(parent);
   }
}
