package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.games.GameNet;
import com.lennart.nebula.games.TicTacToeGame;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundEvents;

/** Tic-Tac-Toe gegen den Bot oder einen Freund. */
public class TicTacToeScreen extends BoardGameScreen {
   private static final int X_COLOR = -11549697;
   private static final int O_COLOR = 0xFFFF7EB6;
   private TicTacToeGame game = new TicTacToeGame();
   private char me = 'X';
   private long botAt;
   private final long[] placedAt = new long[9];
   private int bx;
   private int by;
   private int cell;
   private int wins;
   private int losses;
   private int draws;
   private boolean counted;

   public TicTacToeScreen(Screen parent) {
      super(parent, "ttt", "Tic-Tac-Toe");
   }

   @Override
   protected int preferredWidth() {
      return 300;
   }

   @Override
   protected int preferredHeight() {
      return 230;
   }

   private void reset(char me) {
      this.game = new TicTacToeGame();
      this.me = me;
      this.counted = false;
      java.util.Arrays.fill(this.placedAt, 0L);
   }

   @Override
   protected void startBot(boolean youFirst) {
      this.mode = BoardGameScreen.Mode.BOT;
      this.reset(youFirst ? 'X' : 'O');
      this.botAt = youFirst ? 0L : System.currentTimeMillis() + 450L;
   }

   @Override
   protected void startOnline(GameNet.Online g) {
      this.reset(g.first() ? 'X' : 'O');
   }

   @Override
   protected boolean applyRemote(String move) {
      int i = move.charAt(0) - '0';
      if (this.game.turn() == this.me || !this.game.play(i)) {
         return false;
      } else {
         this.placedAt[i] = System.currentTimeMillis();
         this.sound(SoundEvents.NOTE_BLOCK_HAT.value(), 0.8F);
         this.afterMove(true);
         return true;
      }
   }

   @Override
   protected boolean finished() {
      return this.game.winner() != ' ';
   }

   private void afterMove(boolean remote) {
      char w = this.game.winner();
      if (w != ' ' && !this.counted) {
         this.counted = true;
         if (w == 'D') {
            this.draws++;
            this.say("Unentschieden!");
            this.sound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F);
         } else if (w == this.me) {
            this.wins++;
            this.say("§aDu gewinnst!");
            this.sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.4F);
         } else {
            this.losses++;
            this.say("§c" + this.opponentName() + " gewinnt");
            this.sound(SoundEvents.VILLAGER_NO, 1.0F);
         }

         if (this.mode == BoardGameScreen.Mode.ONLINE && this.online != null) {
            // nur wer den letzten Zug bekommt, meldet das Ende – sonst könnte der Zug selbst verloren gehen
            if (remote) {
               GameNet.get().end(this.online, "over");
            } else {
               GameNet.get().forget(this.online);
            }

            this.online = null;
         }
      } else if (w == ' ') {
         this.say(this.game.turn() == this.me ? "Du bist dran (" + this.me + ")" : this.opponentName() + " ist dran …");
      }
   }

   private void tickBot() {
      if (this.mode == BoardGameScreen.Mode.BOT && !this.finished() && this.game.turn() != this.me) {
         long now = System.currentTimeMillis();
         if (this.botAt == 0L) {
            this.botAt = now + 380L;
         } else if (now >= this.botAt) {
            this.botAt = 0L;
            int i = this.game.botMove(this.botLevel);
            if (i >= 0 && this.game.play(i)) {
               this.placedAt[i] = now;
               this.sound(SoundEvents.NOTE_BLOCK_HAT.value(), 0.8F);
               this.afterMove(false);
            }
         }
      }
   }

   @Override
   protected void renderBoard(GuiGraphics ctx, int x, int y, int w, int h, int mx, int my, float delta) {
      this.tickBot();
      long now = System.currentTimeMillis();
      int size = Math.min(h - 4, w - 100);
      this.cell = size / 3;
      size = this.cell * 3;
      this.bx = x + 4;
      this.by = y + (h - size) / 2;
      Render2D.roundRect(ctx, this.bx - 4, this.by - 4, size + 8, size + 8, 6, Theme.CARD);
      int[] line = this.game.winLine();

      for (int i = 0; i < 9; i++) {
         int cx = this.bx + i % 3 * this.cell;
         int cy = this.by + i / 3 * this.cell;
         boolean hov = this.game.canPlay(i) && this.game.turn() == this.me && Render2D.inside(mx, my, cx, cy, this.cell, this.cell) && this.canClick();
         boolean win = line != null && (line[0] == i || line[1] == i || line[2] == i);
         Render2D.roundRect(ctx, cx + 2, cy + 2, this.cell - 4, this.cell - 4, 5, win ? Render2D.alpha(ACCENT, 0.35F) : (hov ? Theme.CARD_HOVER : Theme.GROUP_CARD));
         char c = this.game.at(i);
         if (c != ' ' || hov) {
            float t = c == ' ' ? 1.0F : Math.min(1.0F, (now - this.placedAt[i]) / 180.0F);
            float s = (this.cell - 16) * (0.5F + 0.5F * t);
            int color = c == ' ' ? Render2D.alpha(this.me == 'X' ? X_COLOR : O_COLOR, 0.3F) : (c == 'X' ? X_COLOR : O_COLOR);
            this.drawMark(ctx, c == ' ' ? this.me : c, cx + this.cell / 2.0F, cy + this.cell / 2.0F, s, color);
         }
      }

      // rechts: Info
      int ix = this.bx + size + 14;
      int iw = x + w - ix;
      ctx.drawString(this.font, "Du: ", ix, y + 6, Theme.TEXT_DIM, false);
      this.drawMark(ctx, this.me, ix + this.font.width("Du: ") + 5, y + 9.5F, 8.0F, this.me == 'X' ? X_COLOR : O_COLOR);
      ctx.drawString(this.font, this.font.plainSubstrByWidth(this.opponentName(), iw), ix, y + 22, Theme.TEXT_DIM, false);
      ctx.drawString(this.font, "§aSiege §f" + this.wins, ix, y + 44, Theme.TEXT, false);
      ctx.drawString(this.font, "§cNiederlagen §f" + this.losses, ix, y + 56, Theme.TEXT, false);
      ctx.drawString(this.font, "§7Remis §f" + this.draws, ix, y + 68, Theme.TEXT, false);
      if (!this.finished()) {
         boolean mine = this.game.turn() == this.me;
         String t = mine ? "Du bist dran" : "Gegner denkt …";
         Render2D.roundRect(ctx, ix, y + 90, Math.min(iw, this.font.width(t) + 10), 14, 7, mine ? Render2D.alpha(ACCENT, 0.4F) : Theme.CARD);
         ctx.drawString(this.font, t, ix + 5, y + 93, Theme.TEXT, false);
      } else if (this.mode == BoardGameScreen.Mode.ONLINE || this.online == null) {
         ctx.drawString(this.font, "§8Neues Spiel: ‹ Menü", ix, y + 93, Theme.TEXT_MUTED, false);
      }
   }

   private void drawMark(GuiGraphics ctx, char c, float cx, float cy, float size, int color) {
      int half = Math.max(2, Math.round(size / 2.0F));
      int th = Math.max(2, Math.round(size / 7.0F));
      if (c == 'O') {
         Render2D.ring(ctx, Math.round(cx), Math.round(cy), Math.min(64, half), th, color, 0);
      } else {
         // X aus kleinen Quadraten entlang der Diagonalen
         for (int i = -half; i <= half; i++) {
            ctx.fill(Math.round(cx + i - th / 2.0F), Math.round(cy + i - th / 2.0F), Math.round(cx + i + th / 2.0F), Math.round(cy + i + th / 2.0F), color);
            ctx.fill(Math.round(cx + i - th / 2.0F), Math.round(cy - i - th / 2.0F), Math.round(cx + i + th / 2.0F), Math.round(cy - i + th / 2.0F), color);
         }
      }
   }

   private boolean canClick() {
      return !this.finished() && this.game.turn() == this.me && (this.mode == BoardGameScreen.Mode.BOT || this.online != null);
   }

   @Override
   protected boolean clickBoard(double mx, double my, int button) {
      if (button != InputConstants.MOUSE_BUTTON_LEFT || !this.canClick() || this.cell <= 0) {
         return false;
      }

      if (mx < this.bx || my < this.by || mx >= this.bx + this.cell * 3 || my >= this.by + this.cell * 3) {
         return false;
      }

      int i = (int)((my - this.by) / this.cell) * 3 + (int)((mx - this.bx) / this.cell);
      if (!this.game.play(i)) {
         return false;
      } else {
         this.placedAt[i] = System.currentTimeMillis();
         this.sound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.2F);
         if (this.mode == BoardGameScreen.Mode.ONLINE && this.online != null) {
            GameNet.get().sendMove(this.online, String.valueOf(i), err -> this.say("§c" + err));
         }

         this.afterMove(false);
         return true;
      }
   }
}
