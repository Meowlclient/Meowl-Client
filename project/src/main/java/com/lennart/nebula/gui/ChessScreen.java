package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.games.ChessGame;
import com.lennart.nebula.games.GameNet;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundEvents;

/** Schach gegen den Bot oder einen Freund. */
public class ChessScreen extends BoardGameScreen {
   private static final ExecutorService BOT = Executors.newSingleThreadExecutor(r -> {
      Thread t = new Thread(r, "Meowl-Chess-Bot");
      t.setDaemon(true);
      return t;
   });
   private static final int LIGHT = 0xFFB9A6D6;
   private static final int DARK = 0xFF6D5A99;
   private static final String GLYPHS = " ♟♞♝♜♛♚";
   private ChessGame game = new ChessGame();
   private int myColor;
   private int selected = -1;
   private List<Integer> targets = List.of();
   private int[] promoChoice;
   private boolean thinking;
   private int generation;
   private ChessGame.Result result = ChessGame.Result.ONGOING;
   private String endText;
   private int bx;
   private int by;
   private int sq;
   private final Random random = new Random();

   public ChessScreen(Screen parent) {
      super(parent, "chess", "Schach");
   }

   @Override
   protected int preferredWidth() {
      return 380;
   }

   @Override
   protected int preferredHeight() {
      return 270;
   }

   private void reset(int myColor) {
      this.game = new ChessGame();
      this.myColor = myColor;
      this.selected = -1;
      this.targets = List.of();
      this.promoChoice = null;
      this.thinking = false;
      this.generation++;
      this.result = ChessGame.Result.ONGOING;
      this.endText = null;
   }

   @Override
   protected void startBot(boolean youFirst) {
      this.mode = BoardGameScreen.Mode.BOT;
      this.reset(youFirst ? 0 : 1);
      this.maybeBot();
   }

   @Override
   protected void startOnline(GameNet.Online g) {
      this.reset(g.first() ? 0 : 1);
      this.say(g.first() ? "Du spielst Weiß und beginnst" : "Du spielst Schwarz – " + g.opponent() + " beginnt");
   }

   @Override
   protected boolean finished() {
      return this.result != ChessGame.Result.ONGOING || this.endText != null;
   }

   @Override
   protected void onlineEnded(String reason, String by) {
      super.onlineEnded(reason, by);
      this.endText = reason.equals("resign") ? "Gegner hat aufgegeben – du gewinnst" : GameNet.reasonText(reason, by);
   }

   @Override
   protected void resigned() {
      super.resigned();
      this.endText = "Du hast aufgegeben";
   }

   @Override
   protected boolean applyRemote(String move) {
      if (this.game.side() == this.myColor || !this.game.playUci(move)) {
         return false;
      } else {
         this.moved(true);
         return true;
      }
   }

   private void moved(boolean remote) {
      this.selected = -1;
      this.targets = List.of();
      boolean capture = this.game.history().size() > 0;
      this.result = this.game.result();
      if (this.result != ChessGame.Result.ONGOING) {
         boolean iWin = this.result == ChessGame.Result.WHITE_WINS && this.myColor == 0 || this.result == ChessGame.Result.BLACK_WINS && this.myColor == 1;
         this.say(this.result.draw() ? "§e" + this.result.text() : (iWin ? "§a" : "§c") + this.result.text());
         this.sound(iWin ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.NOTE_BLOCK_BASS.value(), iWin ? 1.3F : 0.8F);
         if (this.mode == BoardGameScreen.Mode.ONLINE && this.online != null) {
            if (remote) {
               GameNet.get().end(this.online, "over");
            } else {
               GameNet.get().forget(this.online);
            }

            this.online = null;
         }
      } else {
         this.sound(this.game.inCheck() ? SoundEvents.NOTE_BLOCK_PLING.value() : SoundEvents.WOOD_PLACE, this.game.inCheck() ? 1.6F : 1.2F);
         if (this.game.side() == this.myColor) {
            this.say(this.game.inCheck() ? "§cSchach! §fDu bist dran" : "Du bist dran");
         } else {
            this.say(this.opponentName() + " ist dran …");
         }

         this.maybeBot();
      }
   }

   private void maybeBot() {
      if (this.mode == BoardGameScreen.Mode.BOT && this.game.side() != this.myColor && this.result == ChessGame.Result.ONGOING && !this.thinking) {
         this.thinking = true;
         int gen = this.generation;
         ChessGame copy = this.game.copy();
         int level = this.botLevel;
         long started = System.currentTimeMillis();
         BOT.submit(() -> {
            int m = copy.bestMove(level, this.random);
            long wait = Math.max(0L, 350L - (System.currentTimeMillis() - started));

            try {
               Thread.sleep(wait);
            } catch (InterruptedException var8) {
               return;
            }

            this.minecraft.execute(() -> {
               if (gen == this.generation) {
                  this.thinking = false;
                  if (m >= 0 && this.game.play(m)) {
                     this.moved(false);
                  }
               }
            });
         });
      }
   }

   private boolean myTurn() {
      return this.result == ChessGame.Result.ONGOING
         && this.endText == null
         && this.game.side() == this.myColor
         && !this.thinking
         && (this.mode == BoardGameScreen.Mode.BOT || this.online != null);
   }

   // ------------------------------------------------------------ Zeichnen

   private int squareAt(double mx, double my) {
      if (this.sq <= 0 || mx < this.bx || my < this.by || mx >= this.bx + this.sq * 8 || my >= this.by + this.sq * 8) {
         return -1;
      } else {
         int col = (int)((mx - this.bx) / this.sq);
         int row = (int)((my - this.by) / this.sq);
         return this.myColor == 0 ? (7 - row) * 8 + col : row * 8 + (7 - col);
      }
   }

   private int[] screenPos(int square) {
      int f = square & 7;
      int r = square >> 3;
      int col = this.myColor == 0 ? f : 7 - f;
      int row = this.myColor == 0 ? 7 - r : r;
      return new int[]{this.bx + col * this.sq, this.by + row * this.sq};
   }

   @Override
   protected void renderBoard(GuiGraphics ctx, int x, int y, int w, int h, int mx, int my, float delta) {
      int size = Math.min(h - 2, w - 130);
      this.sq = Math.max(8, size / 8);
      size = this.sq * 8;
      this.bx = x + 2;
      this.by = y + (h - size) / 2;
      Render2D.roundRect(ctx, this.bx - 3, this.by - 3, size + 6, size + 6, 4, Theme.CARD);
      int last = this.game.lastMove();
      int checkKing = this.game.inCheck() ? this.game.king(this.game.side()) : -1;
      int hover = this.myTurn() ? this.squareAt(mx, my) : -1;

      for (int s = 0; s < 64; s++) {
         int[] p = this.screenPos(s);
         boolean light = ((s & 7) + (s >> 3)) % 2 == 1;
         ctx.fill(p[0], p[1], p[0] + this.sq, p[1] + this.sq, light ? LIGHT : DARK);
         if (last >= 0 && (ChessGame.from(last) == s || ChessGame.to(last) == s)) {
            ctx.fill(p[0], p[1], p[0] + this.sq, p[1] + this.sq, 0x55FFE066);
         }

         if (s == this.selected) {
            ctx.fill(p[0], p[1], p[0] + this.sq, p[1] + this.sq, Render2D.alpha(ACCENT, 0.55F));
         } else if (s == hover && this.game.at(s) != 0 && (this.game.at(s) & ChessGame.BLACK) == (this.myColor == 1 ? ChessGame.BLACK : 0)) {
            ctx.fill(p[0], p[1], p[0] + this.sq, p[1] + this.sq, 0x33FFFFFF);
         }

         if (s == checkKing) {
            Render2D.circle(ctx, p[0] + this.sq / 2, p[1] + this.sq / 2, Math.min(64, this.sq / 2), 0xAAFF3B3B);
         }
      }

      // Koordinaten
      for (int i = 0; i < 8; i++) {
         int file = this.myColor == 0 ? i : 7 - i;
         int rank = this.myColor == 0 ? 7 - i : i;
         this.small(ctx, String.valueOf((char)('a' + file)), this.bx + i * this.sq + this.sq - 5, this.by + size - 6, i % 2 == 0 ? LIGHT : DARK);
         this.small(ctx, String.valueOf((char)('1' + rank)), this.bx + 1, this.by + i * this.sq + 1, i % 2 == 0 ? DARK : LIGHT);
      }

      for (int s = 0; s < 64; s++) {
         int piece = this.game.at(s);
         if (piece != 0) {
            int[] p = this.screenPos(s);
            this.drawPiece(ctx, piece, p[0] + this.sq / 2.0F, p[1] + this.sq / 2.0F);
         }
      }

      for (int m : this.targets) {
         int[] p = this.screenPos(ChessGame.to(m));
         boolean capture = this.game.at(ChessGame.to(m)) != 0;
         if (capture) {
            Render2D.ring(ctx, p[0] + this.sq / 2, p[1] + this.sq / 2, Math.min(64, this.sq / 2 - 1), 2, 0x99141414, 0);
         } else {
            Render2D.circle(ctx, p[0] + this.sq / 2, p[1] + this.sq / 2, Math.max(2, this.sq / 7), 0x77141414);
         }
      }

      if (this.promoChoice != null) {
         this.renderPromotion(ctx, mx, my);
      }

      this.renderSide(ctx, this.bx + size + 12, y, x + w - (this.bx + size + 12), h);
   }

   private void renderPromotion(GuiGraphics ctx, int mx, int my) {
      ctx.fill(this.bx, this.by, this.bx + this.sq * 8, this.by + this.sq * 8, 0x99000000);
      int bw = this.sq + 8;
      int total = bw * 4 + 12;
      int x0 = this.bx + (this.sq * 8 - total) / 2;
      int y0 = this.by + (this.sq * 8 - bw) / 2;
      int[] types = new int[]{ChessGame.QUEEN, ChessGame.ROOK, ChessGame.BISHOP, ChessGame.KNIGHT};

      for (int i = 0; i < 4; i++) {
         int x = x0 + i * (bw + 4);
         boolean hov = Render2D.inside(mx, my, x, y0, bw, bw);
         Render2D.roundRect(ctx, x, y0, bw, bw, 5, hov ? ACCENT : LIGHT);
         this.drawPiece(ctx, types[i] | (this.myColor == 1 ? ChessGame.BLACK : 0), x + bw / 2.0F, y0 + bw / 2.0F);
      }
   }

   private void renderSide(GuiGraphics ctx, int x, int y, int w, int h) {
      boolean whiteToMove = this.game.side() == 0;
      String meLabel = "Du (" + (this.myColor == 0 ? "Weiß" : "Schwarz") + ")";
      String themLabel = this.font.plainSubstrByWidth(this.opponentName(), w - 10);
      int topColor = 1 - this.myColor;
      this.playerTag(ctx, x, y, w, themLabel, topColor, this.game.side() == topColor && !this.finished());
      this.playerTag(ctx, x, y + h - 16, w, meLabel, this.myColor, this.game.side() == this.myColor && !this.finished());
      // Zugliste
      List<String> hist = this.game.history();
      int listY = y + 22;
      int rows = Math.max(1, (h - 44) / 9);
      int pairs = (hist.size() + 1) / 2;
      int start = Math.max(0, pairs - rows);

      for (int i = start; i < pairs; i++) {
         String wMove = fmt(hist.get(i * 2));
         String bMove = i * 2 + 1 < hist.size() ? fmt(hist.get(i * 2 + 1)) : "";
         int ry = listY + (i - start) * 9;
         this.small(ctx, (i + 1) + ".", x, ry + 1, Theme.TEXT_MUTED);
         ctx.drawString(this.font, wMove, x + 16, ry, Theme.TEXT, false);
         ctx.drawString(this.font, bMove, x + 16 + Math.max(36, (w - 16) / 2), ry, Theme.TEXT_DIM, false);
      }

      if (hist.isEmpty()) {
         ctx.drawString(this.font, "§8Noch keine Züge", x, listY, Theme.TEXT_MUTED, false);
      }

      if (this.thinking) {
         ctx.drawString(this.font, "§7Bot denkt" + ".".repeat((int)(System.currentTimeMillis() / 300L % 4L)), x, y + h - 30, Theme.TEXT_DIM, false);
      } else if (this.finished()) {
         String t = this.endText != null ? this.endText : this.result.text();
         ctx.drawString(this.font, this.font.plainSubstrByWidth(t, w), x, y + h - 30, ACCENT, false);
      } else if (whiteToMove == (this.myColor == 0) && this.game.inCheck()) {
         ctx.drawString(this.font, "§cSchach!", x, y + h - 30, Theme.TEXT, false);
      }
   }

   private static String fmt(String uci) {
      return uci.substring(0, 2) + "-" + uci.substring(2, 4) + (uci.length() > 4 ? "=" + Character.toUpperCase(uci.charAt(4)) : "");
   }

   private void playerTag(GuiGraphics ctx, int x, int y, int w, String label, int color, boolean toMove) {
      Render2D.roundRect(ctx, x, y, w, 16, 5, toMove ? Render2D.alpha(ACCENT, 0.35F) : Theme.CARD);
      Render2D.circle(ctx, x + 8, y + 8, 4, color == 0 ? -1 : -15132391);
      Render2D.ring(ctx, x + 8, y + 8, 4, 1, 0xFF808080, 0);
      ctx.drawString(this.font, label, x + 16, y + 4, Theme.TEXT, false);
   }

   private void small(GuiGraphics ctx, String text, int x, int y, int color) {
      ctx.pose().pushMatrix();
      ctx.pose().translate(x, y);
      ctx.pose().scale(0.6F, 0.6F);
      ctx.drawString(this.font, text, 0, 0, color, false);
      ctx.pose().popMatrix();
   }

   private void drawPiece(GuiGraphics ctx, int piece, float cx, float cy) {
      String g = String.valueOf(GLYPHS.charAt(ChessGame.type(piece)));
      boolean black = (piece & ChessGame.BLACK) != 0;
      int gw = this.font.width(g);
      float scale = this.sq * 0.82F / 9.0F;
      ctx.pose().pushMatrix();
      ctx.pose().translate(cx, cy);
      ctx.pose().scale(scale, scale);
      float ox = -gw / 2.0F;
      float oy = -4.0F;
      int outline = black ? 0xFFE6E0F0 : 0xFF1B1726;
      float o = 0.7F;

      for (float[] d : new float[][]{{-o, 0.0F}, {o, 0.0F}, {0.0F, -o}, {0.0F, o}}) {
         ctx.pose().pushMatrix();
         ctx.pose().translate(ox + d[0], oy + d[1]);
         ctx.drawString(this.font, g, 0, 0, outline, false);
         ctx.pose().popMatrix();
      }

      ctx.pose().pushMatrix();
      ctx.pose().translate(ox, oy);
      ctx.drawString(this.font, g, 0, 0, black ? 0xFF1E1A2A : 0xFFFBF8FF, false);
      ctx.pose().popMatrix();
      ctx.pose().popMatrix();
   }

   // ------------------------------------------------------------ Eingabe

   @Override
   protected boolean clickBoard(double mx, double my, int button) {
      if (this.promoChoice != null) {
         int bw = this.sq + 8;
         int total = bw * 4 + 12;
         int x0 = this.bx + (this.sq * 8 - total) / 2;
         int y0 = this.by + (this.sq * 8 - bw) / 2;
         char[] letters = new char[]{'q', 'r', 'b', 'n'};

         for (int i = 0; i < 4; i++) {
            if (Render2D.inside(mx, my, x0 + i * (bw + 4), y0, bw, bw)) {
               String uci = ChessGame.square(this.promoChoice[0]) + ChessGame.square(this.promoChoice[1]) + letters[i];
               this.promoChoice = null;
               this.playMine(uci);
               return true;
            }
         }

         this.promoChoice = null;
         return true;
      }

      if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
         this.selected = -1;
         this.targets = List.of();
         return true;
      } else if (button == InputConstants.MOUSE_BUTTON_LEFT && this.myTurn()) {
         int s = this.squareAt(mx, my);
         if (s < 0) {
            return false;
         }

         int piece = this.game.at(s);
         boolean own = piece != 0 && ((piece & ChessGame.BLACK) != 0) == (this.myColor == 1);
         if (this.selected >= 0) {
            List<Integer> hits = new ArrayList<>();

            for (int m : this.targets) {
               if (ChessGame.to(m) == s) {
                  hits.add(m);
               }
            }

            if (!hits.isEmpty()) {
               if (hits.size() > 1 || ChessGame.promo(hits.get(0)) != 0) {
                  this.promoChoice = new int[]{this.selected, s};
               } else {
                  this.playMine(ChessGame.uci(hits.get(0)));
               }

               return true;
            }
         }

         if (own) {
            this.selected = s;
            List<Integer> t = new ArrayList<>();

            for (int m : this.game.legalMoves()) {
               if (ChessGame.from(m) == s) {
                  t.add(m);
               }
            }

            this.targets = t;
            this.sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.6F);
         } else {
            this.selected = -1;
            this.targets = List.of();
         }

         return true;
      } else {
         return false;
      }
   }

   private void playMine(String uci) {
      if (this.game.playUci(uci)) {
         if (this.mode == BoardGameScreen.Mode.ONLINE && this.online != null) {
            GameNet.get().sendMove(this.online, uci, err -> this.say("§c" + err));
         }

         this.moved(false);
      }
   }
}
