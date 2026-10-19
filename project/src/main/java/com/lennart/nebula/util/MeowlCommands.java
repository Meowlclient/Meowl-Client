package com.lennart.nebula.util;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * Alle Befehle des Meowl Clients hängen unter {@code /meowl}, z.B. {@code /meowl duel <spieler>} – so gibt es
 * keine Konflikte mit Befehlen des Minecraft-Servers (/duel, /friend, /tiers …). {@code /meowl} allein zeigt die Hilfe.
 */
public final class MeowlCommands {
   public static final String ROOT = "meowl";
   private static final List<MeowlCommands.Sub> SUBS = new ArrayList<>();
   private static boolean hooked;

   private MeowlCommands() {
   }

   /** Fertiger Befehlstext für Chat-Hinweise, z.B. {@code cmd("duel accept")} → "/meowl duel accept". */
   public static String cmd(String rest) {
      return "/" + ROOT + " " + rest;
   }

   /**
    * Registriert einen Unterbefehl. {@code builder} bekommt den Namen (auch für Aliasse) und baut den Knoten.
    * @param usage kurze Hilfe, z.B. "duel <spieler> – Sheriff Duel herausfordern"
    */
   public static void add(String name, String usage, Function<String, LiteralArgumentBuilder<FabricClientCommandSource>> builder, String... aliases) {
      SUBS.add(new MeowlCommands.Sub(name, usage, builder, aliases));
      if (!hooked) {
         hooked = true;
         ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal(ROOT).executes(c -> {
               help();
               return 1;
            });
            root.then(ClientCommands.literal("help").executes(c -> {
               help();
               return 1;
            }));

            for (MeowlCommands.Sub s : SUBS) {
               root.then(s.builder.apply(s.name));

               for (String alias : s.aliases) {
                  root.then(s.builder.apply(alias));
               }
            }

            dispatcher.register(root);
         });
      }
   }

   public static List<String> names() {
      List<String> out = new ArrayList<>();

      for (MeowlCommands.Sub s : SUBS) {
         out.add(s.name);
         out.addAll(List.of(s.aliases));
      }

      return out;
   }

   private static void help() {
      ChatUtil.chat("§d§lMeowl-Befehle §7(alle mit §d/meowl§7 davor)");

      for (MeowlCommands.Sub s : SUBS) {
         ChatUtil.chat("§d/meowl " + s.usage);
      }
   }

   private record Sub(String name, String usage, Function<String, LiteralArgumentBuilder<FabricClientCommandSource>> builder, String[] aliases) {
   }
}
