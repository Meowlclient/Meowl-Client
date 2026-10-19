package com.lennart.nebula.duel;

import java.util.UUID;

public final class DuelSession {
   final String id;
   final UUID opponent;
   final String opponentName;
   DuelArena arena;
   final int maxHp;
   final int mag;
   final long cooldownMs;
   final long reloadMs;
   long startAt;
   int rounds = 1;
   int winsNeeded = 1;
   int round = 1;
   int myWins;
   int opponentWins;
   String roundBanner;
   long roundBannerUntil;
   int myHp;
   int opponentHp;
   int ammo;
   long reloadUntil;
   DuelSession.State state = DuelSession.State.COUNTDOWN;
   String resultTitle;
   String resultText;
   long endedAt;

   DuelSession(String id, UUID opponent, String opponentName, DuelArena arena, int maxHp, int mag, long cooldownMs, long reloadMs, long startIn) {
      this.id = id;
      this.opponent = opponent;
      this.opponentName = opponentName;
      this.arena = arena;
      this.maxHp = maxHp;
      this.mag = mag;
      this.cooldownMs = cooldownMs;
      this.reloadMs = reloadMs;
      this.startAt = System.currentTimeMillis() + startIn;
      this.myHp = maxHp;
      this.opponentHp = maxHp;
      this.ammo = mag;
   }

   void update() {
      if (this.state == DuelSession.State.COUNTDOWN && System.currentTimeMillis() >= this.startAt) {
         this.state = DuelSession.State.ACTIVE;
      }
   }

   public boolean isReloading() {
      return System.currentTimeMillis() < this.reloadUntil;
   }

   public DuelSession.State state() {
      return this.state;
   }

   public DuelArena arena() {
      return this.arena;
   }

   public UUID opponent() {
      return this.opponent;
   }

   public static enum State {
      COUNTDOWN,
      ACTIVE,
      ENDED;
   }
}
