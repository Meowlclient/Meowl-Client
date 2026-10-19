package com.lennart.meowlserver.duel;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DuelSession {
   public static final int DEFAULT_HP = 200;
   public static final int MIN_HP = 50;
   public static final int MAX_HP = 1000;
   public static final int MAX_ROUNDS = 15;
   public static final long ROUND_PAUSE_MS = 3000L;
   public static final int MAG = 6;
   public static final long COOLDOWN_MS = 400L;
   public static final long RELOAD_MS = 2250L;
   public static final long COUNTDOWN_MS = 3000L;
   final String id;
   DuelArena arena;
   final UUID challenger;
   boolean grounded;
   long startAt;
   final int maxHp;
   final int rounds;
   final int winsNeeded;
   int round = 1;
   final long created = System.currentTimeMillis();
   final Map<UUID, DuelSession.PlayerState> players = new HashMap<>();
   DuelSession.State state = DuelSession.State.COUNTDOWN;
   UUID winner;
   long endedAt;

   DuelSession(String id, DuelArena arena, UUID a, String aName, UUID b, String bName, int maxHp, int rounds) {
      this.id = id;
      this.arena = arena;
      this.challenger = a;
      this.maxHp = maxHp;
      this.rounds = rounds;
      this.winsNeeded = rounds / 2 + 1;
      this.startAt = System.currentTimeMillis() + 3000L;
      this.players.put(a, new DuelSession.PlayerState(a, aName, maxHp));
      this.players.put(b, new DuelSession.PlayerState(b, bName, maxHp));
   }

   void nextRound() {
      this.round++;

      for (DuelSession.PlayerState p : this.players.values()) {
         p.hp = this.maxHp;
         p.ammo = 6;
         p.reloadUntil = 0L;
         p.lastShot = 0L;
      }

      this.state = DuelSession.State.COUNTDOWN;
      this.startAt = System.currentTimeMillis() + 3000L;
   }

   Map<String, Object> winsMap() {
      Map<String, Object> m = new HashMap<>();
      this.players.forEach((u, p) -> m.put(u.toString(), p.wins));
      return m;
   }

   DuelSession.PlayerState player(UUID uuid) {
      return this.players.get(uuid);
   }

   DuelSession.PlayerState opponent(UUID uuid) {
      for (DuelSession.PlayerState p : this.players.values()) {
         if (!p.uuid.equals(uuid)) {
            return p;
         }
      }

      return null;
   }

   void update() {
      if (this.state == DuelSession.State.COUNTDOWN && System.currentTimeMillis() >= this.startAt) {
         this.state = DuelSession.State.ACTIVE;
      }
   }

   void end(UUID winner) {
      if (this.state != DuelSession.State.ENDED) {
         this.state = DuelSession.State.ENDED;
         this.winner = winner;
         this.endedAt = System.currentTimeMillis();
      }
   }

   Map<String, Object> hpMap() {
      Map<String, Object> m = new HashMap<>();
      this.players.forEach((u, p) -> m.put(u.toString(), p.hp));
      return m;
   }

   static final class PlayerState {
      final UUID uuid;
      final String name;
      int hp;
      int ammo = 6;
      int wins;
      long reloadUntil;
      long lastShot;
      final Deque<DuelSession.Pose> history = new ArrayDeque<>();

      PlayerState(UUID uuid, String name, int hp) {
         this.uuid = uuid;
         this.name = name;
         this.hp = hp;
      }

      DuelSession.Pose latest() {
         return this.history.peekLast();
      }
   }

   public record Pose(long time, Vec3 feet, float yaw, float pitch, boolean sneaking) {
   }

   public static enum State {
      COUNTDOWN,
      ACTIVE,
      ENDED;
   }
}
