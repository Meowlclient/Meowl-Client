package com.lennart.meowlserver.games;

import com.lennart.meowlserver.ApiException;
import com.lennart.meowlserver.Json;
import com.lennart.meowlserver.auth.AuthService;
import com.lennart.meowlserver.auth.Session;
import com.lennart.meowlserver.events.EventHub;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brettspiele zwischen zwei Meowl-Spielern (Tic-Tac-Toe, Schach). Der Server prüft Zugreihenfolge und Format
 * und leitet Züge weiter; die Spielregeln prüfen beide Clients selbst.
 */
public final class GameService {
   public static final long INVITE_TTL_MS = 60000L;
   public static final long IDLE_TIMEOUT_MS = 1800000L;
   private final AuthService auth;
   private final EventHub events;
   private final Map<String, GameService.Invite> invites = new ConcurrentHashMap<>();
   private final Map<String, GameService.Game> games = new ConcurrentHashMap<>();

   public GameService(AuthService auth, EventHub events) {
      this.auth = auth;
      this.events = events;
   }

   private static String game(Map<String, Object> body) {
      String g = Json.str(body, "game", 12);
      if (!g.equals("ttt") && !g.equals("chess")) {
         throw new ApiException(400, "Unbekanntes Spiel");
      } else {
         return g;
      }
   }

   private static String label(String game) {
      return game.equals("chess") ? "Schach" : "Tic-Tac-Toe";
   }

   private Session byName(String name) {
      for (Session s : this.auth.all()) {
         if (s.isOnline() && s.name().equalsIgnoreCase(name)) {
            return s;
         }
      }

      return null;
   }

   private GameService.Game active(UUID player, String game) {
      for (GameService.Game g : this.games.values()) {
         if (g.game.equals(game) && (g.a.equals(player) || g.b.equals(player))) {
            return g;
         }
      }

      return null;
   }

   public synchronized Map<String, Object> invite(Session from, Map<String, Object> body) {
      String game = game(body);
      String targetName = Json.str(body, "target", 16);
      Session to = this.byName(targetName);
      if (to == null) {
         throw new ApiException(404, targetName + " ist gerade nicht mit Meowl verbunden");
      } else if (to.uuid().equals(from.uuid())) {
         throw new ApiException(400, "Du kannst nicht gegen dich selbst spielen");
      } else if (!to.clientAtLeast(1, 11, 0)) {
         throw new ApiException(409, to.name() + " hat eine ältere Meowl-Version ohne " + label(game));
      } else if (this.active(from.uuid(), game) != null) {
         throw new ApiException(409, "Du spielst schon " + label(game));
      } else if (this.active(to.uuid(), game) != null) {
         throw new ApiException(409, to.name() + " spielt gerade schon " + label(game));
      } else {
         this.invites.values().removeIf(i -> i.from.equals(from.uuid()) && i.to.equals(to.uuid()) && i.game.equals(game));
         String id = UUID.randomUUID().toString();
         this.invites.put(id, new GameService.Invite(id, game, from.uuid(), from.name(), to.uuid(), System.currentTimeMillis() + INVITE_TTL_MS));
         Map<String, Object> ev = new LinkedHashMap<>();
         ev.put("inviteId", id);
         ev.put("game", game);
         ev.put("from", from.uuid().toString());
         ev.put("fromName", from.name());
         ev.put("expiresIn", INVITE_TTL_MS);
         this.events.send(to.uuid(), "game_invite", ev);
         return Map.of("inviteId", id, "to", to.name(), "expiresIn", INVITE_TTL_MS);
      }
   }

   public synchronized Map<String, Object> respond(Session me, Map<String, Object> body) {
      String inviteId = Json.str(body, "inviteId", 40);
      GameService.Invite inv = this.invites.remove(inviteId);
      if (inv == null || !inv.to.equals(me.uuid()) || inv.expires < System.currentTimeMillis()) {
         throw new ApiException(404, "Einladung nicht gefunden oder abgelaufen");
      }

      boolean accept = Json.bool(body, "accept");
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("inviteId", inviteId);
      result.put("game", inv.game);
      result.put("accepted", accept);
      result.put("by", me.name());
      this.events.send(inv.from, "game_invite_result", result);
      if (!accept) {
         return result;
      }

      Session from = this.auth.byUuid(inv.from);
      if (from == null || !from.isOnline()) {
         throw new ApiException(409, inv.fromName + " ist nicht mehr verbunden");
      } else if (this.active(me.uuid(), inv.game) != null || this.active(inv.from, inv.game) != null) {
         throw new ApiException(409, "Einer von euch spielt schon " + label(inv.game));
      }

      // Der Einladende beginnt (Schach: Weiß, Tic-Tac-Toe: X)
      GameService.Game g = new GameService.Game(UUID.randomUUID().toString(), inv.game, inv.from, from.name(), me.uuid(), me.name());
      this.games.put(g.id, g);
      this.sendStart(g, g.a, g.bName, true);
      this.sendStart(g, g.b, g.aName, false);
      return result;
   }

   private void sendStart(GameService.Game g, UUID to, String opponentName, boolean first) {
      Map<String, Object> ev = new LinkedHashMap<>();
      ev.put("id", g.id);
      ev.put("game", g.game);
      ev.put("opponentName", opponentName);
      ev.put("first", first);
      this.events.send(to, "game_start", ev);
   }

   private GameService.Game require(Session me, Map<String, Object> body) {
      GameService.Game g = this.games.get(Json.str(body, "id", 40));
      if (g == null || !g.a.equals(me.uuid()) && !g.b.equals(me.uuid())) {
         throw new ApiException(404, "Kein laufendes Spiel");
      } else {
         return g;
      }
   }

   public synchronized Map<String, Object> move(Session me, Map<String, Object> body) {
      GameService.Game g = this.require(me, body);
      String move = Json.str(body, "move", 12).toLowerCase(Locale.ROOT);
      if (!move.matches(g.game.equals("chess") ? "[a-h][1-8][a-h][1-8][qrbn]?" : "[0-8]")) {
         throw new ApiException(400, "Ungültiger Zug");
      }

      UUID turn = g.ply % 2 == 0 ? g.a : g.b;
      if (!turn.equals(me.uuid())) {
         throw new ApiException(409, "Du bist nicht am Zug");
      }

      if (g.game.equals("ttt")) {
         int cell = move.charAt(0) - '0';
         if ((g.tttTaken & 1 << cell) != 0) {
            throw new ApiException(409, "Feld ist schon belegt");
         }

         g.tttTaken |= 1 << cell;
      }

      g.ply++;
      g.lastActive = System.currentTimeMillis();
      Map<String, Object> ev = new LinkedHashMap<>();
      ev.put("id", g.id);
      ev.put("move", move);
      ev.put("ply", g.ply);
      this.events.send(g.other(me.uuid()), "game_move", ev);
      return Map.of("ok", true, "ply", g.ply);
   }

   /** Spiel beenden: reason = resign (aufgeben), over (regulär vorbei, z.B. Matt) oder leave. */
   public synchronized Map<String, Object> end(Session me, Map<String, Object> body) {
      GameService.Game g = this.require(me, body);
      String reason = body.get("reason") instanceof String r && r.matches("[a-z]{1,12}") ? r : "leave";
      this.games.remove(g.id);
      if (!reason.equals("over")) {
         Map<String, Object> ev = new LinkedHashMap<>();
         ev.put("id", g.id);
         ev.put("reason", reason);
         ev.put("by", me.name());
         this.events.send(g.other(me.uuid()), "game_end", ev);
      }

      return Map.of("ok", true);
   }

   public synchronized void leaveAll(Session me) {
      for (GameService.Game g : this.games.values()) {
         if (g.a.equals(me.uuid()) || g.b.equals(me.uuid())) {
            this.games.remove(g.id);
            this.events.send(g.other(me.uuid()), "game_end", Map.of("id", g.id, "reason", "leave", "by", me.name()));
         }
      }
   }

   public synchronized void tick() {
      long now = System.currentTimeMillis();
      this.invites.values().removeIf(i -> i.expires < now);

      for (GameService.Game g : this.games.values()) {
         Session a = this.auth.byUuid(g.a);
         Session b = this.auth.byUuid(g.b);
         boolean gone = a == null || !a.isOnline() || b == null || !b.isOnline();
         if (gone || now - g.lastActive > IDLE_TIMEOUT_MS) {
            this.games.remove(g.id);
            String reason = gone ? "leave" : "timeout";
            for (UUID u : new UUID[]{g.a, g.b}) {
               this.events.send(u, "game_end", Map.of("id", g.id, "reason", reason, "by", ""));
            }
         }
      }
   }

   private static final class Game {
      final String id;
      final String game;
      final UUID a;
      final String aName;
      final UUID b;
      final String bName;
      int ply;
      int tttTaken;
      long lastActive = System.currentTimeMillis();

      Game(String id, String game, UUID a, String aName, UUID b, String bName) {
         this.id = id;
         this.game = game;
         this.a = a;
         this.aName = aName;
         this.b = b;
         this.bName = bName;
      }

      UUID other(UUID u) {
         return this.a.equals(u) ? this.b : this.a;
      }
   }

   private record Invite(String id, String game, UUID from, String fromName, UUID to, long expires) {
   }
}
