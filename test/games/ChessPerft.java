import com.lennart.nebula.games.ChessGame;
import com.lennart.nebula.games.TicTacToeGame;

/** Prüft die Schach-Zuggenerierung gegen bekannte Perft-Werte und testet die Bots. */
public class ChessPerft {
   static int fails;

   static void check(String name, String fen, long... expected) {
      for (int d = 1; d <= expected.length; d++) {
         ChessGame g = fen == null ? new ChessGame() : ChessGame.fromFen(fen);
         long t = System.currentTimeMillis();
         long n = g.perft(d);
         boolean ok = n == expected[d - 1];
         if (!ok) {
            fails++;
         }

         System.out.printf("%-10s Tiefe %d: %9d (erwartet %9d) %s  %d ms%n", name, d, n, expected[d - 1], ok ? "OK" : "FEHLER", System.currentTimeMillis() - t);
      }
   }

   public static void main(String[] a) {
      check("Start", null, 20, 400, 8902, 197281);
      check("Kiwipete", "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq -", 48, 2039, 97862);
      check("Pos3", "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - -", 14, 191, 2812, 43238);
      check("Pos4", "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq -", 6, 264, 9467);
      check("Pos5", "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ -", 44, 1486, 62379);
      // Matt in 1 für den Bot: Weiß Dame h5, Läufer c4 -> Df7#
      ChessGame m = ChessGame.fromFen("r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq -");
      String bot = ChessGame.uci(m.copy().bestMove(2, new java.util.Random(1)));
      System.out.println("Bot findet Matt in 1: " + bot + (bot.equals("h5f7") ? " OK" : " FEHLER"));
      if (!bot.equals("h5f7")) {
         fails++;
      }

      boolean played = m.playUci("h5f7");
      System.out.println("Ergebnis nach Df7: " + m.result() + (played && m.result() == ChessGame.Result.WHITE_WINS ? " OK" : " FEHLER"));
      if (!played || m.result() != ChessGame.Result.WHITE_WINS) {
         fails++;
      }

      // Tic-Tac-Toe: unschlagbarer Bot gegen sich selbst muss immer remis spielen
      for (int i = 0; i < 20; i++) {
         TicTacToeGame t = new TicTacToeGame();
         while (t.winner() == ' ') {
            t.play(t.botMove(2));
         }

         if (t.winner() != 'D') {
            fails++;
            System.out.println("Tic-Tac-Toe Bot FEHLER: " + t.winner());
         }
      }

      System.out.println("Tic-Tac-Toe Bot: 20 Spiele geprüft");
      System.out.println(fails == 0 ? "ALLES OK" : fails + " FEHLER");
      System.exit(fails == 0 ? 0 : 1);
   }
}
