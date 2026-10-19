package com.lennart.nebula.games;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Schach mit allen Regeln: Rochade, en passant, Umwandlung, Schach/Matt/Patt, 50-Züge-Regel,
 * dreifache Wiederholung und zu wenig Material. Felder: index = reihe * 8 + linie (a1 = 0, h8 = 63).
 */
public final class ChessGame {
   public static final int EMPTY = 0;
   public static final int PAWN = 1;
   public static final int KNIGHT = 2;
   public static final int BISHOP = 3;
   public static final int ROOK = 4;
   public static final int QUEEN = 5;
   public static final int KING = 6;
   public static final int BLACK = 8;
   private static final int F_EP = 1;
   private static final int F_CASTLE = 2;
   private static final int F_DOUBLE = 4;
   // Richtungen als {Linie, Reihe}
   private static final int[][] KNIGHT_D = new int[][]{{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
   private static final int[][] KING_D = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
   private static final int[][] DIAG = new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
   private static final int[][] ORTHO = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
   private final int[] board = new int[64];
   private int side;
   private int castling = 15;
   private int ep = -1;
   private int halfmove;
   private final Deque<long[]> undo = new ArrayDeque<>();
   private final List<String> history = new ArrayList<>();
   private final Map<String, Integer> seen = new HashMap<>();
   private int lastMove = -1;

   public ChessGame() {
      int[] back = new int[]{ROOK, KNIGHT, BISHOP, QUEEN, KING, BISHOP, KNIGHT, ROOK};

      for (int f = 0; f < 8; f++) {
         this.board[f] = back[f];
         this.board[8 + f] = PAWN;
         this.board[48 + f] = PAWN | BLACK;
         this.board[56 + f] = back[f] | BLACK;
      }

      this.seen.put(this.key(), 1);
   }

   private ChessGame(ChessGame o) {
      System.arraycopy(o.board, 0, this.board, 0, 64);
      this.side = o.side;
      this.castling = o.castling;
      this.ep = o.ep;
      this.halfmove = o.halfmove;
   }

   public ChessGame copy() {
      return new ChessGame(this);
   }

   public int at(int sq) {
      return this.board[sq];
   }

   /** 0 = Weiß am Zug, 1 = Schwarz. */
   public int side() {
      return this.side;
   }

   public List<String> history() {
      return this.history;
   }

   public int lastMove() {
      return this.lastMove;
   }

   public static int from(int m) {
      return m & 63;
   }

   public static int to(int m) {
      return m >> 6 & 63;
   }

   public static int promo(int m) {
      return m >> 12 & 7;
   }

   private static int flags(int m) {
      return m >> 16 & 7;
   }

   private static int move(int from, int to, int promo, int flags) {
      return from | to << 6 | promo << 12 | flags << 16;
   }

   private static boolean white(int p) {
      return p != 0 && (p & BLACK) == 0;
   }

   private static int color(int p) {
      return (p & BLACK) == 0 ? 0 : 1;
   }

   public static int type(int p) {
      return p & 7;
   }

   /** Feld nach einem Schritt, -1 wenn außerhalb des Bretts. */
   private static int step(int sq, int[] d) {
      int f = (sq & 7) + d[0];
      int r = (sq >> 3) + d[1];
      return f >= 0 && f < 8 && r >= 0 && r < 8 ? r * 8 + f : -1;
   }

   public boolean attacked(int sq, int byColor) {
      int pawnDir = byColor == 0 ? -8 : 8;

      for (int df : new int[]{-1, 1}) {
         int s = sq + pawnDir + df;
         if (s >= 0 && s < 64 && Math.abs((s & 7) - (sq & 7)) == 1) {
            int p = this.board[s];
            if (p == (PAWN | (byColor == 1 ? BLACK : 0))) {
               return true;
            }
         }
      }

      for (int[] d : KNIGHT_D) {
         int s = step(sq, d);
         if (s >= 0 && this.board[s] == (KNIGHT | (byColor == 1 ? BLACK : 0))) {
            return true;
         }
      }

      for (int[] d : KING_D) {
         int s = step(sq, d);
         if (s >= 0 && this.board[s] == (KING | (byColor == 1 ? BLACK : 0))) {
            return true;
         }
      }

      if (this.slideHits(sq, DIAG, byColor, BISHOP)) {
         return true;
      } else {
         return this.slideHits(sq, ORTHO, byColor, ROOK);
      }
   }

   private boolean slideHits(int sq, int[][] dirs, int byColor, int slider) {
      for (int[] d : dirs) {
         int s = sq;

         while (true) {
            s = step(s, d);
            if (s < 0) {
               break;
            }

            int p = this.board[s];
            if (p != 0) {
               if (color(p) == byColor && (type(p) == slider || type(p) == QUEEN)) {
                  return true;
               }
               break;
            }
         }
      }

      return false;
   }

   public int king(int c) {
      int k = KING | (c == 1 ? BLACK : 0);

      for (int i = 0; i < 64; i++) {
         if (this.board[i] == k) {
            return i;
         }
      }

      return -1;
   }

   public boolean inCheck() {
      int k = this.king(this.side);
      return k >= 0 && this.attacked(k, 1 - this.side);
   }

   private void pseudo(List<Integer> out, boolean capturesOnly) {
      int us = this.side;

      for (int sq = 0; sq < 64; sq++) {
         int p = this.board[sq];
         if (p != 0 && color(p) == us) {
            int t = type(p);
            if (t == PAWN) {
               int dir = us == 0 ? 8 : -8;
               int startRank = us == 0 ? 1 : 6;
               int promoRank = us == 0 ? 7 : 0;
               int one = sq + dir;
               if (!capturesOnly && one >= 0 && one < 64 && this.board[one] == 0) {
                  this.addPawn(out, sq, one, promoRank, 0);
                  int two = one + dir;
                  if (sq >> 3 == startRank && this.board[two] == 0) {
                     out.add(move(sq, two, 0, F_DOUBLE));
                  }
               }

               for (int df : new int[]{-1, 1}) {
                  int c = sq + dir + df;
                  if (c >= 0 && c < 64 && Math.abs((c & 7) - (sq & 7)) == 1) {
                     if (this.board[c] != 0 && color(this.board[c]) != us) {
                        this.addPawn(out, sq, c, promoRank, 0);
                     } else if (c == this.ep) {
                        out.add(move(sq, c, 0, F_EP));
                     }
                  }
               }
            } else if (t == KNIGHT || t == KING) {
               for (int[] d : t == KNIGHT ? KNIGHT_D : KING_D) {
                  int s = step(sq, d);
                  if (s >= 0 && (this.board[s] == 0 ? !capturesOnly : color(this.board[s]) != us)) {
                     out.add(move(sq, s, 0, 0));
                  }
               }

               if (t == KING && !capturesOnly) {
                  this.castles(out, sq);
               }
            } else {
               int[][] dirs = t == BISHOP ? DIAG : (t == ROOK ? ORTHO : KING_D);

               for (int[] d : dirs) {
                  int s = sq;

                  while (true) {
                     s = step(s, d);
                     if (s < 0) {
                        break;
                     }

                     if (this.board[s] == 0) {
                        if (!capturesOnly) {
                           out.add(move(sq, s, 0, 0));
                        }
                     } else {
                        if (color(this.board[s]) != us) {
                           out.add(move(sq, s, 0, 0));
                        }
                        break;
                     }
                  }
               }
            }
         }
      }
   }

   private void addPawn(List<Integer> out, int from, int to, int promoRank, int flags) {
      if (to >> 3 == promoRank) {
         for (int pr : new int[]{QUEEN, ROOK, BISHOP, KNIGHT}) {
            out.add(move(from, to, pr, flags));
         }
      } else {
         out.add(move(from, to, 0, flags));
      }
   }

   private void castles(List<Integer> out, int k) {
      int us = this.side;
      int base = us == 0 ? 0 : 56;
      if (k == base + 4 && !this.attacked(k, 1 - us)) {
         int kingSide = us == 0 ? 1 : 4;
         int queenSide = us == 0 ? 2 : 8;
         int rook = ROOK | (us == 1 ? BLACK : 0);
         if ((this.castling & kingSide) != 0 && this.board[base + 5] == 0 && this.board[base + 6] == 0 && this.board[base + 7] == rook
            && !this.attacked(base + 5, 1 - us) && !this.attacked(base + 6, 1 - us)) {
            out.add(move(k, base + 6, 0, F_CASTLE));
         }

         if ((this.castling & queenSide) != 0 && this.board[base + 3] == 0 && this.board[base + 2] == 0 && this.board[base + 1] == 0 && this.board[base] == rook
            && !this.attacked(base + 3, 1 - us) && !this.attacked(base + 2, 1 - us)) {
            out.add(move(k, base + 2, 0, F_CASTLE));
         }
      }
   }

   public List<Integer> legalMoves() {
      return this.legal(false);
   }

   private List<Integer> legal(boolean capturesOnly) {
      List<Integer> pseudo = new ArrayList<>();
      this.pseudo(pseudo, capturesOnly);
      List<Integer> out = new ArrayList<>();
      int us = this.side;

      for (int m : pseudo) {
         this.make(m);
         if (!this.attacked(this.king(us), 1 - us)) {
            out.add(m);
         }

         this.unmake();
      }

      return out;
   }

   private static final int[] CASTLE_MASK = new int[64];

   static {
      java.util.Arrays.fill(CASTLE_MASK, 15);
      CASTLE_MASK[0] = 13;
      CASTLE_MASK[7] = 14;
      CASTLE_MASK[4] = 12;
      CASTLE_MASK[56] = 7;
      CASTLE_MASK[63] = 11;
      CASTLE_MASK[60] = 3;
   }

   private void make(int m) {
      int from = from(m);
      int to = to(m);
      int piece = this.board[from];
      int captured = this.board[to];
      int capSq = to;
      if (flags(m) == F_EP) {
         capSq = to + (this.side == 0 ? -8 : 8);
         captured = this.board[capSq];
         this.board[capSq] = 0;
      }

      this.undo.push(new long[]{m, captured, capSq, this.castling, this.ep, this.halfmove, this.lastMove});
      this.board[to] = promo(m) != 0 ? promo(m) | (this.side == 1 ? BLACK : 0) : piece;
      this.board[from] = 0;
      if (flags(m) == F_CASTLE) {
         if ((to & 7) == 6) {
            this.board[to - 1] = this.board[to + 1];
            this.board[to + 1] = 0;
         } else {
            this.board[to + 1] = this.board[to - 2];
            this.board[to - 2] = 0;
         }
      }

      this.castling = this.castling & CASTLE_MASK[from] & CASTLE_MASK[to];
      this.ep = flags(m) == F_DOUBLE ? (from + to) / 2 : -1;
      this.halfmove = type(piece) != PAWN && captured == 0 ? this.halfmove + 1 : 0;
      this.side = 1 - this.side;
      this.lastMove = m;
   }

   private void unmake() {
      long[] u = this.undo.pop();
      int m = (int)u[0];
      int from = from(m);
      int to = to(m);
      this.side = 1 - this.side;
      int moved = this.board[to];
      this.board[from] = promo(m) != 0 ? PAWN | (this.side == 1 ? BLACK : 0) : moved;
      this.board[to] = 0;
      this.board[(int)u[2]] = (int)u[1];
      if (flags(m) == F_CASTLE) {
         if ((to & 7) == 6) {
            this.board[to + 1] = this.board[to - 1];
            this.board[to - 1] = 0;
         } else {
            this.board[to - 2] = this.board[to + 1];
            this.board[to + 1] = 0;
         }
      }

      this.castling = (int)u[3];
      this.ep = (int)u[4];
      this.halfmove = (int)u[5];
      this.lastMove = (int)u[6];
   }

   /** Führt einen legalen Zug im echten Spiel aus (mit Verlauf). */
   public boolean play(int m) {
      if (!this.legalMoves().contains(m)) {
         return false;
      } else {
         this.history.add(uci(m));
         this.make(m);
         this.undo.clear();
         this.seen.merge(this.key(), 1, Integer::sum);
         return true;
      }
   }

   public boolean playUci(String uci) {
      Integer m = this.parse(uci);
      return m != null && this.play(m);
   }

   public Integer parse(String uci) {
      for (int m : this.legalMoves()) {
         if (uci(m).equals(uci)) {
            return m;
         }
      }

      return null;
   }

   public static String uci(int m) {
      String s = square(from(m)) + square(to(m));
      return promo(m) == 0 ? s : s + "  nbrq".charAt(promo(m));
   }

   public static String square(int sq) {
      return "" + (char)('a' + (sq & 7)) + (char)('1' + (sq >> 3));
   }

   private String key() {
      StringBuilder sb = new StringBuilder(80);

      for (int p : this.board) {
         sb.append((char)('A' + p));
      }

      return sb.append(this.side).append(this.castling).append(',').append(this.ep).toString();
   }

   public enum Result {
      ONGOING,
      WHITE_WINS,
      BLACK_WINS,
      STALEMATE,
      FIFTY,
      REPETITION,
      MATERIAL;

      public boolean draw() {
         return this == STALEMATE || this == FIFTY || this == REPETITION || this == MATERIAL;
      }

      public String text() {
         return switch (this) {
            case WHITE_WINS -> "Schachmatt – Weiß gewinnt";
            case BLACK_WINS -> "Schachmatt – Schwarz gewinnt";
            case STALEMATE -> "Patt – Remis";
            case FIFTY -> "50-Züge-Regel – Remis";
            case REPETITION -> "Dreifache Wiederholung – Remis";
            case MATERIAL -> "Zu wenig Material – Remis";
            default -> "";
         };
      }
   }

   public ChessGame.Result result() {
      if (this.legalMoves().isEmpty()) {
         return this.inCheck() ? (this.side == 0 ? ChessGame.Result.BLACK_WINS : ChessGame.Result.WHITE_WINS) : ChessGame.Result.STALEMATE;
      } else if (this.halfmove >= 100) {
         return ChessGame.Result.FIFTY;
      } else if (this.seen.getOrDefault(this.key(), 0) >= 3) {
         return ChessGame.Result.REPETITION;
      } else {
         return this.insufficient() ? ChessGame.Result.MATERIAL : ChessGame.Result.ONGOING;
      }
   }

   private boolean insufficient() {
      int minors = 0;

      for (int p : this.board) {
         int t = type(p);
         if (t == PAWN || t == ROOK || t == QUEEN) {
            return false;
         }

         if (t == KNIGHT || t == BISHOP) {
            minors++;
         }
      }

      return minors <= 1;
   }

   /** Zählt alle Stellungen bis zur Tiefe (zum Testen der Zuggenerierung). */
   public long perft(int depth) {
      if (depth == 0) {
         return 1L;
      } else {
         long n = 0L;

         for (int m : this.legalMoves()) {
            this.make(m);
            n += this.perft(depth - 1);
            this.unmake();
         }

         return n;
      }
   }

   /** Stellung aus FEN laden (nur Brett, Zugrecht, Rochade, en passant). */
   public static ChessGame fromFen(String fen) {
      ChessGame g = new ChessGame();
      java.util.Arrays.fill(g.board, 0);
      String[] parts = fen.trim().split("\\s+");
      int r = 7;
      int f = 0;

      for (char c : parts[0].toCharArray()) {
         if (c == '/') {
            r--;
            f = 0;
         } else if (Character.isDigit(c)) {
            f += c - '0';
         } else {
            int t = "pnbrqk".indexOf(Character.toLowerCase(c)) + 1;
            g.board[r * 8 + f] = t | (Character.isLowerCase(c) ? BLACK : 0);
            f++;
         }
      }

      g.side = parts.length > 1 && parts[1].equals("b") ? 1 : 0;
      g.castling = 0;
      if (parts.length > 2) {
         for (char c : parts[2].toCharArray()) {
            g.castling |= switch (c) {
               case 'K' -> 1;
               case 'Q' -> 2;
               case 'k' -> 4;
               case 'q' -> 8;
               default -> 0;
            };
         }
      }

      g.ep = parts.length > 3 && !parts[3].equals("-") ? (parts[3].charAt(0) - 'a') + (parts[3].charAt(1) - '1') * 8 : -1;
      g.seen.clear();
      g.seen.put(g.key(), 1);
      return g;
   }

   // ------------------------------------------------------------------ Bot

   private static final int[] VALUE = new int[]{0, 100, 320, 330, 500, 900, 0};
   private static final int[] PST_PAWN = new int[]{
      0, 0, 0, 0, 0, 0, 0, 0, 5, 10, 10, -20, -20, 10, 10, 5, 5, -5, -10, 0, 0, -10, -5, 5, 0, 0, 0, 20, 20, 0, 0, 0,
      5, 5, 10, 25, 25, 10, 5, 5, 10, 10, 20, 30, 30, 20, 10, 10, 50, 50, 50, 50, 50, 50, 50, 50, 0, 0, 0, 0, 0, 0, 0, 0
   };
   private static final int[] PST_KNIGHT = new int[]{
      -50, -40, -30, -30, -30, -30, -40, -50, -40, -20, 0, 5, 5, 0, -20, -40, -30, 5, 10, 15, 15, 10, 5, -30, -30, 0, 15, 20, 20, 15, 0, -30,
      -30, 5, 15, 20, 20, 15, 5, -30, -30, 0, 10, 15, 15, 10, 0, -30, -40, -20, 0, 0, 0, 0, -20, -40, -50, -40, -30, -30, -30, -30, -40, -50
   };
   private static final int[] PST_BISHOP = new int[]{
      -20, -10, -10, -10, -10, -10, -10, -20, -10, 5, 0, 0, 0, 0, 5, -10, -10, 10, 10, 10, 10, 10, 10, -10, -10, 0, 10, 10, 10, 10, 0, -10,
      -10, 5, 5, 10, 10, 5, 5, -10, -10, 0, 5, 10, 10, 5, 0, -10, -10, 0, 0, 0, 0, 0, 0, -10, -20, -10, -10, -10, -10, -10, -10, -20
   };
   private static final int[] PST_ROOK = new int[]{
      0, 0, 0, 5, 5, 0, 0, 0, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5,
      -5, 0, 0, 0, 0, 0, 0, -5, -5, 0, 0, 0, 0, 0, 0, -5, 5, 10, 10, 10, 10, 10, 10, 5, 0, 0, 0, 0, 0, 0, 0, 0
   };
   private static final int[] PST_KING = new int[]{
      20, 30, 10, 0, 0, 10, 30, 20, 20, 20, 0, 0, 0, 0, 20, 20, -10, -20, -20, -20, -20, -20, -20, -10, -20, -30, -30, -40, -40, -30, -30, -20,
      -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30, -30, -40, -40, -50, -50, -40, -40, -30
   };

   private int evaluate() {
      int score = 0;

      for (int sq = 0; sq < 64; sq++) {
         int p = this.board[sq];
         if (p != 0) {
            int t = type(p);
            int idx = white(p) ? sq : (7 - (sq >> 3)) * 8 + (sq & 7);
            int v = VALUE[t]
               + switch (t) {
                  case PAWN -> PST_PAWN[idx];
                  case KNIGHT -> PST_KNIGHT[idx];
                  case BISHOP -> PST_BISHOP[idx];
                  case ROOK -> PST_ROOK[idx];
                  case KING -> PST_KING[idx];
                  default -> 0;
               };
            score += white(p) ? v : -v;
         }
      }

      return this.side == 0 ? score : -score;
   }

   private void order(List<Integer> moves) {
      moves.sort((a, b) -> this.moveScore(b) - this.moveScore(a));
   }

   private int moveScore(int m) {
      int victim = type(this.board[to(m)]);
      int s = victim != 0 ? 10 * VALUE[victim] - VALUE[type(this.board[from(m)])] : 0;
      return s + (promo(m) == QUEEN ? 800 : 0);
   }

   private int quiesce(int alpha, int beta, int depth) {
      int stand = this.evaluate();
      if (stand >= beta || depth <= 0) {
         return stand;
      }

      alpha = Math.max(alpha, stand);
      List<Integer> caps = this.legal(true);
      this.order(caps);

      for (int m : caps) {
         this.make(m);
         int score = -this.quiesce(-beta, -alpha, depth - 1);
         this.unmake();
         if (score >= beta) {
            return beta;
         }

         alpha = Math.max(alpha, score);
      }

      return alpha;
   }

   private int search(int depth, int alpha, int beta, int ply) {
      List<Integer> moves = this.legalMoves();
      if (moves.isEmpty()) {
         return this.inCheck() ? -100000 + ply : 0;
      } else if (depth <= 0) {
         return this.quiesce(alpha, beta, 4);
      }

      this.order(moves);

      for (int m : moves) {
         this.make(m);
         int score = -this.search(depth - 1, -beta, -alpha, ply + 1);
         this.unmake();
         if (score >= beta) {
            return beta;
         }

         alpha = Math.max(alpha, score);
      }

      return alpha;
   }

   /** Bot-Zug (auf einer Kopie berechnen!). level 0 = leicht, 1 = mittel, 2 = stark. */
   public int bestMove(int level, Random random) {
      List<Integer> moves = this.legalMoves();
      if (moves.isEmpty()) {
         return -1;
      }

      int depth = level == 0 ? 1 : (level == 1 ? 2 : 3);
      int noise = level == 0 ? 160 : (level == 1 ? 30 : 0);
      this.order(moves);
      int best = moves.get(0);
      int bestScore = Integer.MIN_VALUE;

      for (int m : moves) {
         this.make(m);
         int score = -this.search(depth - 1, -1000000, 1000000, 1);
         this.unmake();
         if (noise > 0) {
            score += random.nextInt(noise * 2 + 1) - noise;
         }

         if (score > bestScore) {
            bestScore = score;
            best = m;
         }
      }

      return best;
   }
}
