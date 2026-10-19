package com.lennart.nebula.blackjack;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class BlackjackGame {
   private static final int DECKS = 4;
   private final SecureRandom random = new SecureRandom();
   private final List<BlackjackGame.Card> shoe = new ArrayList<>();
   private final List<BlackjackGame.Card> dealer = new ArrayList<>();
   private BlackjackGame.Phase phase = BlackjackGame.Phase.BETTING;
   private BlackjackGame.Outcome outcome = BlackjackGame.Outcome.NONE;
   private boolean holeHidden = true;
   private boolean doubled;
   private int bet;
   private final boolean dealerHitsSoft17;

   public BlackjackGame(boolean dealerHitsSoft17) {
      this.dealerHitsSoft17 = dealerHitsSoft17;
      this.shuffle();
   }

   private void shuffle() {
      this.shoe.clear();

      for (int d = 0; d < 4; d++) {
         for (BlackjackGame.Suit s : BlackjackGame.Suit.values()) {
            for (int r = 1; r <= 13; r++) {
               this.shoe.add(new BlackjackGame.Card(r, s));
            }
         }
      }

      Collections.shuffle(this.shoe, (Random)this.random);
   }

   private BlackjackGame.Card draw() {
      if (this.shoe.size() < 52) {
         this.shuffle();
      }

      return this.shoe.remove(this.shoe.size() - 1);
   }

   public static int score(List<BlackjackGame.Card> hand) {
      int total = 0;
      int aces = 0;

      for (BlackjackGame.Card c : hand) {
         total += c.value();
         if (c.rank() == 1) {
            aces++;
         }
      }

      while (total > 21 && aces > 0) {
         total -= 10;
         aces--;
      }

      return total;
   }

   public static boolean soft(List<BlackjackGame.Card> hand) {
      int total = 0;
      int aces = 0;

      for (BlackjackGame.Card c : hand) {
         total += c.value();
         if (c.rank() == 1) {
            aces++;
         }
      }

      while (total > 21 && aces > 0) {
         total -= 10;
         aces--;
      }

      return aces > 0 && total <= 21;
   }

   public static boolean blackjack(List<BlackjackGame.Card> hand) {
      return hand.size() == 2 && score(hand) == 21;
   }

   /** Eine Hand des Spielers (nach dem Teilen gibt es zwei). */
   public static final class Hand {
      final List<BlackjackGame.Card> cards = new ArrayList<>();
      int bet;
      boolean doubled;
      boolean done;
      boolean fromSplit;
      BlackjackGame.Outcome outcome = BlackjackGame.Outcome.NONE;

      public List<BlackjackGame.Card> cards() {
         return Collections.unmodifiableList(this.cards);
      }

      public int bet() {
         return this.bet;
      }

      public BlackjackGame.Outcome outcome() {
         return this.outcome;
      }

      public boolean doubled() {
         return this.doubled;
      }

      /** Nach dem Teilen zählt 21 aus zwei Karten nicht als Blackjack. */
      boolean natural() {
         return !this.fromSplit && blackjack(this.cards);
      }
   }

   private final List<BlackjackGame.Hand> hands = new ArrayList<>();
   private int active;

   public boolean deal(int bet) {
      if (this.phase != BlackjackGame.Phase.BETTING && this.phase != BlackjackGame.Phase.DONE) {
         return false;
      } else {
         this.bet = bet;
         this.hands.clear();
         this.active = 0;
         BlackjackGame.Hand h = new BlackjackGame.Hand();
         h.bet = bet;
         this.hands.add(h);
         this.dealer.clear();
         this.doubled = false;
         this.holeHidden = true;
         this.outcome = BlackjackGame.Outcome.NONE;
         h.cards.add(this.draw());
         this.dealer.add(this.draw());
         h.cards.add(this.draw());
         this.dealer.add(this.draw());
         this.phase = BlackjackGame.Phase.PLAYER;
         if (blackjack(h.cards) || blackjack(this.dealer)) {
            this.holeHidden = false;
            this.finish();
         }

         return true;
      }
   }

   private BlackjackGame.Hand current() {
      return this.hands.get(this.active);
   }

   public List<BlackjackGame.Hand> hands() {
      return Collections.unmodifiableList(this.hands);
   }

   public int activeHand() {
      return this.active;
   }

   public int activeBet() {
      return this.hands.isEmpty() ? this.bet : this.current().bet;
   }

   public boolean canDouble() {
      return this.phase == BlackjackGame.Phase.PLAYER && this.current().cards.size() == 2;
   }

   /** Teilen: einmal pro Runde, bei zwei Karten mit gleichem Wert. */
   public boolean canSplit() {
      if (this.phase != BlackjackGame.Phase.PLAYER || this.hands.size() != 1) {
         return false;
      } else {
         List<BlackjackGame.Card> c = this.current().cards;
         return c.size() == 2 && c.get(0).value() == c.get(1).value();
      }
   }

   public void split() {
      if (this.canSplit()) {
         BlackjackGame.Hand first = this.current();
         BlackjackGame.Hand second = new BlackjackGame.Hand();
         second.bet = first.bet;
         first.fromSplit = true;
         second.fromSplit = true;
         second.cards.add(first.cards.remove(1));
         boolean aces = first.cards.get(0).rank() == 1;
         first.cards.add(this.draw());
         second.cards.add(this.draw());
         this.hands.add(second);
         if (aces) {
            // geteilte Asse bekommen nur je eine Karte
            first.done = true;
            second.done = true;
            this.advance();
         } else if (score(first.cards) == 21) {
            first.done = true;
            this.advance();
         }
      }
   }

   /** Nächste offene Hand – oder Dealer, wenn alle fertig sind. */
   private void advance() {
      while (this.active < this.hands.size() && this.current().done) {
         if (this.active == this.hands.size() - 1) {
            break;
         }

         this.active++;
         if (score(this.current().cards) == 21) {
            this.current().done = true;
         }
      }

      boolean allDone = true;
      boolean anyAlive = false;

      for (BlackjackGame.Hand h : this.hands) {
         allDone &= h.done;
         anyAlive |= score(h.cards) <= 21;
      }

      if (allDone) {
         this.holeHidden = false;
         if (anyAlive) {
            this.phase = BlackjackGame.Phase.DEALER;
         } else {
            this.finish();
         }
      }
   }

   public void hit() {
      if (this.phase == BlackjackGame.Phase.PLAYER) {
         BlackjackGame.Hand h = this.current();
         h.cards.add(this.draw());
         if (score(h.cards) >= 21) {
            h.done = true;
            this.advance();
         }
      }
   }

   public void doubleDown() {
      if (this.canDouble()) {
         BlackjackGame.Hand h = this.current();
         h.doubled = true;
         this.doubled = true;
         h.bet *= 2;
         h.cards.add(this.draw());
         h.done = true;
         this.advance();
      }
   }

   public void stand() {
      if (this.phase == BlackjackGame.Phase.PLAYER) {
         this.current().done = true;
         this.advance();
      }
   }

   public boolean dealerStep() {
      if (this.phase != BlackjackGame.Phase.DEALER) {
         return false;
      } else {
         int s = score(this.dealer);
         boolean hit = s < 17 || s == 17 && this.dealerHitsSoft17 && soft(this.dealer);
         if (hit) {
            this.dealer.add(this.draw());
            return true;
         } else {
            this.finish();
            return false;
         }
      }
   }

   private BlackjackGame.Outcome judge(BlackjackGame.Hand h) {
      int p = score(h.cards);
      int d = score(this.dealer);
      boolean pbj = h.natural();
      boolean dbj = blackjack(this.dealer);
      if (pbj && dbj) {
         return BlackjackGame.Outcome.PUSH;
      } else if (pbj) {
         return BlackjackGame.Outcome.BLACKJACK;
      } else if (p > 21) {
         return BlackjackGame.Outcome.BUST;
      } else if (dbj) {
         return BlackjackGame.Outcome.LOSE;
      } else if (d > 21) {
         return BlackjackGame.Outcome.DEALER_BUST;
      } else if (p > d) {
         return BlackjackGame.Outcome.WIN;
      } else {
         return p < d ? BlackjackGame.Outcome.LOSE : BlackjackGame.Outcome.PUSH;
      }
   }

   private void finish() {
      this.phase = BlackjackGame.Phase.DONE;
      this.holeHidden = false;

      for (BlackjackGame.Hand h : this.hands) {
         h.outcome = this.judge(h);
      }

      if (this.hands.size() == 1) {
         this.outcome = this.hands.get(0).outcome;
      } else {
         // Gesamtergebnis nach Gewinn/Verlust über beide Hände
         int net = this.payout() - this.bet();
         this.outcome = net > 0 ? BlackjackGame.Outcome.WIN : (net < 0 ? BlackjackGame.Outcome.LOSE : BlackjackGame.Outcome.PUSH);
      }
   }

   private static int payout(BlackjackGame.Hand h) {
      return switch (h.outcome) {
         case WIN, DEALER_BUST -> h.bet * 2;
         case BLACKJACK -> h.bet + h.bet * 3 / 2;
         case PUSH -> h.bet;
         default -> 0;
      };
   }

   public int payout() {
      int sum = 0;

      for (BlackjackGame.Hand h : this.hands) {
         sum += payout(h);
      }

      return sum;
   }

   /** Karten der gerade gespielten Hand. */
   public List<BlackjackGame.Card> player() {
      return this.hands.isEmpty() ? List.of() : this.current().cards();
   }

   public List<BlackjackGame.Card> dealer() {
      return Collections.unmodifiableList(this.dealer);
   }

   public BlackjackGame.Phase phase() {
      return this.phase;
   }

   public BlackjackGame.Outcome outcome() {
      return this.outcome;
   }

   public boolean holeHidden() {
      return this.holeHidden;
   }

   public boolean doubled() {
      return this.doubled;
   }

   /** Gesamteinsatz über alle Hände. */
   public int bet() {
      if (this.hands.isEmpty()) {
         return this.bet;
      } else {
         int sum = 0;

         for (BlackjackGame.Hand h : this.hands) {
            sum += h.bet;
         }

         return sum;
      }
   }
   public record Card(int rank, BlackjackGame.Suit suit) {
      public int value() {
         return this.rank == 1 ? 11 : Math.min(10, this.rank);
      }

      public String label() {
         return switch (this.rank) {
            case 1 -> "A";
            case 11 -> "B";
            case 12 -> "D";
            case 13 -> "K";
            default -> String.valueOf(this.rank);
         };
      }

      public boolean red() {
         return this.suit == BlackjackGame.Suit.HEARTS || this.suit == BlackjackGame.Suit.DIAMONDS;
      }
   }

   public static enum Outcome {
      NONE,
      WIN,
      BLACKJACK,
      LOSE,
      BUST,
      PUSH,
      DEALER_BUST;
   }

   public static enum Phase {
      BETTING,
      PLAYER,
      DEALER,
      DONE;
   }

   public static enum Suit {
      HEARTS,
      DIAMONDS,
      SPADES,
      CLUBS;
   }
}
