package com.lennart.nebula.games;

import com.google.gson.JsonObject;
import com.lennart.nebula.net.MeowlService;
import com.lennart.nebula.util.ChatUtil;
import com.lennart.nebula.util.MeowlCommands;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Online-Brettspiele über den Meowl Server (Tic-Tac-Toe = "ttt", Schach = "chess"):
 * Einladungen, Spielstart, Züge und Spielende. Die Spiel-Bildschirme melden sich als {@link Listener} an.
 */
public final class GameNet {
   private static final GameNet INSTANCE = new GameNet();
   private final Map<String, GameNet.Invite> invites = new ConcurrentHashMap<>();
   private final Map<String, GameNet.Listener> listeners = new ConcurrentHashMap<>();
   private final Map<String, GameNet.Online> active = new ConcurrentHashMap<>();
   private final Map<String, Opener> openers = new ConcurrentHashMap<>();
   private boolean hooked;

   private GameNet() {
   }

   public static GameNet get() {
      return INSTANCE;
   }

   public static String label(String game) {
      return game.equals("chess") ? "Schach" : "Tic-Tac-Toe";
   }

   /** Einmalig: Server-Ereignisse und /meowl-Befehle registrieren. */
   public synchronized void hook() {
      if (this.hooked) {
         return;
      }

      this.hooked = true;
      MeowlService s = MeowlService.get();
      s.on("game_invite", this::onInvite);
      s.on("game_invite_result", this::onInviteResult);
      s.on("game_start", this::onStart);
      s.on("game_move", d -> {
         GameNet.Online g = this.byId(d.get("id").getAsString());
         if (g != null) {
            GameNet.Listener l = this.listeners.get(g.game());
            if (l != null) {
               l.onRemoteMove(g, d.get("move").getAsString());
            } else {
               g.pendingMoves().append(d.get("move").getAsString()).append(' ');
            }
         }
      });
      s.on("game_end", d -> {
         GameNet.Online g = this.byId(d.get("id").getAsString());
         if (g != null) {
            this.active.remove(g.game());
            String reason = d.get("reason").getAsString();
            String by = d.has("by") ? d.get("by").getAsString() : "";
            GameNet.Listener l = this.listeners.get(g.game());
            if (l != null) {
               l.onRemoteEnd(g, reason, by);
            } else {
               ChatUtil.chat("§7" + label(g.game()) + " gegen §f" + g.opponent() + " §7ist vorbei (" + reasonText(reason, by) + ")");
            }
         }
      });
      MeowlCommands.add(
         "game",
         "game accept|decline <id> §7– Spiel-Einladung annehmen/ablehnen",
         root -> ClientCommands.literal(root)
            .then(ClientCommands.literal("accept").then(ClientCommands.argument("id", StringArgumentType.word()).executes(c -> {
               String id = StringArgumentType.getString(c, "id");
               Minecraft.getInstance().schedule(() -> this.respond(id, true));
               return 1;
            })))
            .then(ClientCommands.literal("decline").then(ClientCommands.argument("id", StringArgumentType.word()).executes(c -> {
               String id = StringArgumentType.getString(c, "id");
               Minecraft.getInstance().schedule(() -> this.respond(id, false));
               return 1;
            })))
      );
   }

   public static String reasonText(String reason, String by) {
      return switch (reason) {
         case "resign" -> by.isEmpty() ? "aufgegeben" : by + " hat aufgegeben";
         case "timeout" -> "zu lange inaktiv";
         case "desync" -> "Spielstand nicht mehr gleich";
         default -> by.isEmpty() ? "Verbindung weg" : by + " hat das Spiel verlassen";
      };
   }

   /** Wie ein Spiel-Bildschirm geöffnet wird (für "Annehmen" aus dem Chat). */
   public interface Opener {
      void open();
   }

   public void registerOpener(String game, Opener opener) {
      this.openers.put(game, opener);
   }

   public void setListener(String game, GameNet.Listener listener) {
      if (listener == null) {
         this.listeners.remove(game);
      } else {
         this.listeners.put(game, listener);
      }
   }

   public GameNet.Online active(String game) {
      return this.active.get(game);
   }

   public Map<String, GameNet.Invite> invites() {
      return this.invites;
   }

   private GameNet.Online byId(String id) {
      for (GameNet.Online g : this.active.values()) {
         if (g.id().equals(id)) {
            return g;
         }
      }

      return null;
   }

   private void onInvite(JsonObject d) {
      String id = d.get("inviteId").getAsString();
      String game = d.get("game").getAsString();
      String from = d.get("fromName").getAsString();
      long ttl = d.has("expiresIn") ? d.get("expiresIn").getAsLong() : 60000L;
      this.invites.put(id, new GameNet.Invite(id, game, from, System.currentTimeMillis() + ttl));
      ChatUtil.chat(
         Component.literal("§f" + from + " §7lädt dich zu §a" + label(game) + " §7ein ")
            .append(ChatUtil.button("[Annehmen]", 4906624, MeowlCommands.cmd("game accept " + id), "Spiel starten"))
            .append(Component.literal(" "))
            .append(ChatUtil.button("[Ablehnen]", 15680580, MeowlCommands.cmd("game decline " + id), "Einladung ablehnen"))
      );
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
         mc.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.7F, 1.4F);
      }

      GameNet.Listener l = this.listeners.get(game);
      if (l != null) {
         l.onInvitesChanged();
      }
   }

   private void onInviteResult(JsonObject d) {
      String game = d.get("game").getAsString();
      boolean ok = d.get("accepted").getAsBoolean();
      String by = d.get("by").getAsString();
      if (!ok) {
         ChatUtil.chat("§f" + by + " §7hat die " + label(game) + "-Einladung abgelehnt");
         GameNet.Listener l = this.listeners.get(game);
         if (l != null) {
            l.onInviteDeclined(by);
         }
      }
   }

   private void onStart(JsonObject d) {
      String game = d.get("game").getAsString();
      GameNet.Online g = new GameNet.Online(d.get("id").getAsString(), game, d.get("opponentName").getAsString(), d.get("first").getAsBoolean(), new StringBuilder());
      this.active.put(game, g);
      this.invites.values().removeIf(i -> i.game().equals(game));
      GameNet.Listener l = this.listeners.get(game);
      if (l != null) {
         l.onStart(g);
      } else {
         Opener o = this.openers.get(game);
         if (o != null) {
            o.open();
            GameNet.Listener now = this.listeners.get(game);
            if (now != null) {
               now.onStart(g);
            }
         }
      }
   }

   public void invite(String game, String name, Consumer<String> feedback) {
      if (!MeowlService.get().isConnected()) {
         feedback.accept("§cNicht mit dem Meowl Server verbunden");
         return;
      }

      JsonObject o = new JsonObject();
      o.addProperty("game", game);
      o.addProperty("target", name);
      MeowlService.get().post("/game/invite", o).whenComplete((r, e) -> Minecraft.getInstance().execute(() -> {
         if (e != null) {
            feedback.accept("§c" + e.getMessage());
         } else if (!r.ok()) {
            feedback.accept("§c" + (r.status() == 404 && r.json() != null && r.error().startsWith("Unbekannter") ? "Der Meowl Server ist zu alt – bitte Server-Update einspielen" : r.error()));
         } else {
            feedback.accept("§aEinladung an " + name + " geschickt – warte auf Antwort …");
         }
      }));
   }

   public void respond(String inviteId, boolean accept) {
      GameNet.Invite inv = this.invites.remove(inviteId);
      if (inv == null) {
         ChatUtil.chat("§7Diese Einladung ist abgelaufen");
         return;
      }

      JsonObject o = new JsonObject();
      o.addProperty("inviteId", inviteId);
      o.addProperty("accept", accept);
      MeowlService.get().post("/game/respond", o).whenComplete((r, e) -> Minecraft.getInstance().execute(() -> {
         if (e != null || !r.ok()) {
            ChatUtil.chat("§c" + (e != null ? e.getMessage() : r.error()));
         } else if (!accept) {
            ChatUtil.chat("§7Einladung von " + inv.from() + " abgelehnt");
         }
      }));
   }

   public void sendMove(GameNet.Online g, String move, Consumer<String> onError) {
      JsonObject o = new JsonObject();
      o.addProperty("id", g.id());
      o.addProperty("move", move);
      MeowlService.get().post("/game/move", o).whenComplete((r, e) -> Minecraft.getInstance().execute(() -> {
         if (e != null || !r.ok()) {
            onError.accept(e != null ? e.getMessage() : r.error());
         }
      }));
   }

   /** Spiel nur lokal als beendet merken (der Gegner meldet das Ende an den Server). */
   public void forget(GameNet.Online g) {
      this.active.remove(g.game(), g);
   }

   /** reason: resign, over, leave */
   public void end(GameNet.Online g, String reason) {
      this.active.remove(g.game(), g);
      JsonObject o = new JsonObject();
      o.addProperty("id", g.id());
      o.addProperty("reason", reason);
      MeowlService.get().post("/game/end", o);
   }

   public interface Listener {
      void onStart(GameNet.Online game);

      void onRemoteMove(GameNet.Online game, String move);

      void onRemoteEnd(GameNet.Online game, String reason, String by);

      default void onInviteDeclined(String by) {
      }

      default void onInvitesChanged() {
      }
   }

   public record Invite(String id, String game, String from, long expires) {
   }

   /** Laufendes Online-Spiel. first = du beginnst (Weiß bzw. X). */
   public record Online(String id, String game, String opponent, boolean first, StringBuilder pendingMoves) {
   }
}
