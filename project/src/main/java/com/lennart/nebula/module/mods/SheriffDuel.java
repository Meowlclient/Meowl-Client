package com.lennart.nebula.module.mods;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.util.MeowlCommands;
import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.duel.DuelManager;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.util.ChatUtil;
import com.lennart.nebula.util.InputBlocker;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.lennart.nebula.util.WorldCtx;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Items;

public class SheriffDuel extends Module {
   private final NumberSetting radius = this.add(new NumberSetting("Bubble-Radius", "Größe der Arena beim Herausfordern", 14.0, 8.0, 32.0, 1.0, "Blöcke"));
   private final NumberSetting rounds = this.add(new NumberSetting("Runden", "Best-of-N: wer zuerst die Mehrheit der Runden gewinnt", 3.0, 1.0, 15.0, 1.0, ""));
   private final NumberSetting health = this.add(
      new NumberSetting("Leben", "Lebenspunkte pro Runde (Kopftreffer 150, Körper 55, Beine 46)", 200.0, 50.0, 1000.0, 50.0, "HP")
   );
   private final NumberSetting recoil = this.add(new NumberSetting("Rückstoß", "Wie weit der Blick bei jedem Schuss nach oben kickt", 2.0, 0.0, 6.0, 0.5, "°"));
   private final ColorSetting bubbleColor = this.add(new ColorSetting("Bubble-Farbe", "Farbe der Arena-Bubble", 1619746038));
   private final BooleanSetting grid = this.add(new BooleanSetting("Gitter", "Gitterlinien auf der Bubble", true));
   private final BooleanSetting sounds = this.add(new BooleanSetting("Sounds", "Schuss-, Treffer- und Nachlade-Sounds", true));
   private final KeybindSetting reloadKey = this.add(new KeybindSetting("Nachladen", "Taste zum Nachladen", Bind.keyboard(InputConstants.KEY_R)));
   private final DuelManager manager = new DuelManager(this::isEnabled);

   public SheriffDuel() {
      super("Sheriff Duel", "Duell-Modus mit Sheriff, Bubble und Deckungen – nur zwischen Meowl-Spielern.", Category.MEOWL);
      this.setEnabled(true);
      this.section(Section.GAMES);
      this.icon(Items.CROSSBOW);
      InputBlocker.blockUseWhen(this.manager::inDuel);
      InputBlocker.blockAttackWhen(this.manager::inDuel);
      MeowlCommands.add("duel", "duel <spieler> [runden] [leben]|accept|decline|leave §7– Sheriff Duel", this::build);
   }

   private LiteralArgumentBuilder<FabricClientCommandSource> build(String name) {
      return ClientCommandManager.literal(name)
         .then(ClientCommandManager.literal("accept").executes(c -> this.run(() -> this.manager.respond(true))))
         .then(ClientCommandManager.literal("decline").executes(c -> this.run(() -> this.manager.respond(false))))
         .then(ClientCommandManager.literal("leave").executes(c -> this.run(this.manager::leave)))
         .then(
            ClientCommandManager.literal("runden")
               .executes(c -> this.run(() -> ChatUtil.chat("§dRunden: §f" + DuelManager.roundsText(this.rounds.getInt()) + " §7(/meowl " + name + " runden <1-15>)")))
               .then(
                  ClientCommandManager.argument("anzahl", IntegerArgumentType.integer(1, 15))
                     .executes(c -> this.run(() -> this.setDefault(this.rounds, IntegerArgumentType.getInteger(c, "anzahl"), "Runden")))
               )
         )
         .then(
            ClientCommandManager.literal("leben")
               .executes(c -> this.run(() -> ChatUtil.chat("§dLeben: §f" + this.health.getInt() + " §7(/meowl " + name + " leben <50-1000>)")))
               .then(
                  ClientCommandManager.argument("hp", IntegerArgumentType.integer(50, 1000))
                     .executes(c -> this.run(() -> this.setDefault(this.health, IntegerArgumentType.getInteger(c, "hp"), "Leben")))
               )
         )
         .then(
            ClientCommandManager.argument("spieler", StringArgumentType.word())
               .executes(c -> this.run(() -> this.challenge(StringArgumentType.getString(c, "spieler"), this.rounds.getInt(), this.health.getInt())))
               .then(
                  ClientCommandManager.argument("runden", IntegerArgumentType.integer(1, 15))
                     .executes(
                        c -> this.run(
                           () -> this.challenge(StringArgumentType.getString(c, "spieler"), IntegerArgumentType.getInteger(c, "runden"), this.health.getInt())
                        )
                     )
                     .then(
                        ClientCommandManager.argument("leben", IntegerArgumentType.integer(50, 1000))
                           .executes(
                              c -> this.run(
                                 () -> this.challenge(
                                    StringArgumentType.getString(c, "spieler"),
                                    IntegerArgumentType.getInteger(c, "runden"),
                                    IntegerArgumentType.getInteger(c, "leben")
                                 )
                              )
                           )
                     )
               )
         );
   }

   private void challenge(String player, int r, int hp) {
      this.manager.challenge(player, this.radius.get(), hp, r);
   }

   private void setDefault(NumberSetting setting, int value, String label) {
      setting.set((double)value);
      NebulaClient client = NebulaClient.getInstance();
      if (client != null) {
         client.getModuleManager().notifyChanged();
      }

      ChatUtil.chat("§d" + label + " für neue Duelle: §f" + (setting == this.rounds ? DuelManager.roundsText(value) : value + " HP"));
   }

   private int run(Runnable action) {
      if (!this.isEnabled()) {
         ChatUtil.chat("§7Sheriff Duel ist aus (Mods-Seite → Sheriff Duel).");
         return 0;
      } else {
         action.run();
         return 1;
      }
   }

   @Override
   protected void onDisable() {
      this.manager.shutdown();
   }

   @Override
   public void onPreTick() {
      this.manager.setSounds(this.sounds.get());
      this.manager.setRecoilKick((float)this.recoil.get().doubleValue());
      this.manager.preTick(this.reloadKey.get().isPressed());
   }

   @Override
   public void onTick() {
      this.manager.tick();
   }

   @Override
   public void onRenderWorld(WorldCtx context, float tickDelta) {
      this.manager.renderWorld(context, tickDelta, this.bubbleColor.get(), this.grid.get());
   }

   @Override
   public void onRenderHud(GuiGraphics ctx, float tickDelta) {
      this.manager.renderHud(ctx);
   }
}
