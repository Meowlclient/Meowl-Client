package com.lennart.nebula.games;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Tic-Tac-Toe: Felder 0–8 (Zeile für Zeile), X beginnt. Bot mit Minimax. */
public final class TicTacToeGame {
   public static final int[][] LINES = new int[][]{{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};
   private final char[] cells = new char[9];
   private char turn = 'X';
   private final Random random = new Random();

   public TicTacToeGame() {
      java.util.Arrays.fill(this.cells, ' ');
   }

   public char at(int i) {
      return this.cells[i];
   }

   public char turn() {
      return this.turn;
   }

   public boolean canPlay(int i) {
      return i >= 0 && i < 9 && this.cells[i] == ' ' && this.winner() == ' ';
   }

   public boolean play(int i) {
      if (!this.canPlay(i)) {
         return false;
      } else {
         this.cells[i] = this.turn;
         this.turn = this.turn == 'X' ? 'O' : 'X';
         return true;
      }
   }

   /** 'X', 'O', 'D' (unentschieden) oder ' ' (läuft). */
   public char winner() {
      return winner(this.cells);
   }

   public int[] winLine() {
      for (int[] l : LINES) {
         if (this.cells[l[0]] != ' ' && this.cells[l[0]] == this.cells[l[1]] && this.cells[l[1]] == this.cells[l[2]]) {
            return l;
         }
      }

      return null;
   }

   private static char winner(char[] c) {
      for (int[] l : LINES) {
         if (c[l[0]] != ' ' && c[l[0]] == c[l[1]] && c[l[1]] == c[l[2]]) {
            return c[l[0]];
         }
      }

      for (char x : c) {
         if (x == ' ') {
            return ' ';
         }
      }

      return 'D';
   }

   /** Bot-Zug. level: 0 = leicht (oft zufällig), 1 = mittel, 2 = unschlagbar. */
   public int botMove(int level) {
      List<Integer> free = new ArrayList<>();

      for (int i = 0; i < 9; i++) {
         if (this.cells[i] == ' ') {
            free.add(i);
         }
      }

      if (free.isEmpty()) {
         return -1;
      }

      double randomChance = level == 0 ? 0.65 : (level == 1 ? 0.25 : 0.0);
      if (this.random.nextDouble() < randomChance) {
         return free.get(this.random.nextInt(free.size()));
      }

      char me = this.turn;
      int best = -2;
      List<Integer> bestMoves = new ArrayList<>();

      for (int i : free) {
         char[] c = this.cells.clone();
         c[i] = me;
         int score = -negamax(c, me == 'X' ? 'O' : 'X');
         if (score > best) {
            best = score;
            bestMoves.clear();
         }

         if (score == best) {
            bestMoves.add(i);
         }
      }

      return bestMoves.get(this.random.nextInt(bestMoves.size()));
   }

   /** +1 = Spieler am Zug gewinnt, 0 = remis, -1 = verliert. */
   private static int negamax(char[] c, char toMove) {
      char w = winner(c);
      if (w == 'D') {
         return 0;
      } else if (w != ' ') {
         return w == toMove ? 1 : -1;
      } else {
         int best = -2;

         for (int i = 0; i < 9; i++) {
            if (c[i] == ' ') {
               c[i] = toMove;
               best = Math.max(best, -negamax(c, toMove == 'X' ? 'O' : 'X'));
               c[i] = ' ';
               if (best == 1) {
                  break;
               }
            }
         }

         return best;
      }
   }
}
