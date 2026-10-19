package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.blackjack.BlackjackGame;
import com.lennart.nebula.module.mods.Blackjack;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BlackjackScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private static final int CARD_W = 30;
   private static final int CARD_H = 42;
   private static final int GAP = 6;
   private final Screen parent;
   private final Blackjack module;
   private BlackjackGame game;
   private int bet;
   private final List<BlackjackScreen.VisualCard> visuals = new ArrayList<>();
   private long nextDealAt;
   private long lastDealerStep;
   private boolean settled = true;
   private boolean holeWasHidden = true;
   private BlackjackGame.Outcome shownOutcome = BlackjackGame.Outcome.NONE;
   private long outcomeAt;
   private int lastDelta;
   private final Animation open = new Animation(12.0F, 0.0F);
   private final List<float[]> confetti = new ArrayList<>();
   private final Random random = new Random();
   private long lastFrame = System.nanoTime();
   private final List<Object[]> buttons = new ArrayList<>();

   public BlackjackScreen(Screen parent, Blackjack module) {
      super(Component.literal("Meowl Blackjack"));
      this.parent = parent;
      this.module = module;
      this.game = new BlackjackGame(module.dealerHitsSoft17());
      if (module.chips() <= 0) {
         module.setChips(module.startChips());
      }

      this.bet = Math.max(10, Math.min(module.lastBet(), module.chips()));
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   private long interval() {
      return Math.round(320.0F / this.module.tempo());
   }

   private long flight() {
      return Math.round(260.0F / this.module.tempo());
   }

   private boolean canAct() {
      return this.game.phase() == BlackjackGame.Phase.PLAYER && this.allLanded();
   }

   private boolean allLanded() {
      long now = System.currentTimeMillis();

      for (BlackjackScreen.VisualCard v : this.visuals) {
         if (now < v.dealtAt + this.flight()) {
            return false;
         }
      }

      return true;
   }

   private void deal() {
      if (this.game.phase() != BlackjackGame.Phase.PLAYER && this.game.phase() != BlackjackGame.Phase.DEALER && this.settled) {
         if (this.module.chips() < this.bet) {
            if (this.module.chips() <= 0) {
               this.module.setChips(this.module.startChips());
               this.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F);
            }

            this.bet = Math.max(10, Math.min(this.bet, this.module.chips()));
         } else {
            this.module.setChips(this.module.chips() - this.bet);
            this.module.setLastBet(this.bet);
            this.game = new BlackjackGame(this.module.dealerHitsSoft17());
            this.visuals.clear();
            this.confetti.clear();
            this.shownOutcome = BlackjackGame.Outcome.NONE;
            this.settled = false;
            this.holeWasHidden = true;
            this.nextDealAt = System.currentTimeMillis();
            this.game.deal(this.bet);
            this.syncVisuals();
         }
      }
   }

   private void hit() {
      if (this.canAct()) {
         this.game.hit();
         this.syncVisuals();
      }
   }

   private void stand() {
      if (this.canAct()) {
         this.game.stand();
         this.lastDealerStep = System.currentTimeMillis();
         this.syncVisuals();
      }
   }

   private void split() {
      if (this.canAct() && this.game.canSplit() && this.module.chips() >= this.game.activeBet()) {
         this.module.setChips(this.module.chips() - this.game.activeBet());
         this.game.split();
         // zweite Karte wandert auf die neue Hand
         for (BlackjackScreen.VisualCard v : this.visuals) {
            if (!v.dealer && v.hand == 0 && v.index == 1) {
               v.hand = 1;
               v.index = 0;
               break;
            }
         }

         this.lastDealerStep = System.currentTimeMillis();
         this.syncVisuals();
      }
   }

   private void doubleDown() {
      if (this.canAct() && this.game.canDouble() && this.module.chips() >= this.game.activeBet()) {
         this.module.setChips(this.module.chips() - this.game.activeBet());
         this.game.doubleDown();
         this.lastDealerStep = System.currentTimeMillis();
         this.syncVisuals();
      }
   }

   private int visualCount(boolean dealer, int hand) {
      int n = 0;

      for (BlackjackScreen.VisualCard v : this.visuals) {
         if (v.dealer == dealer && (dealer || v.hand == hand)) {
            n++;
         }
      }

      return n;
   }

   private void syncVisuals() {
      long now = System.currentTimeMillis();
      List<BlackjackGame.Hand> hands = this.game.hands();

      while (true) {
         int d = this.visualCount(true, 0);
         int playerShown = 0;
         int missingHand = -1;

         for (int h = 0; h < hands.size(); h++) {
            int shown = this.visualCount(false, h);
            playerShown += shown;
            if (missingHand < 0 && shown < hands.get(h).cards().size()) {
               missingHand = h;
            }
         }

         boolean dealerMissing = d < this.game.dealer().size();
         if (!dealerMissing && missingHand < 0) {
            break;
         }

         this.nextDealAt = Math.max(this.nextDealAt, now);
         if (dealerMissing && (missingHand < 0 || d < playerShown)) {
            this.visuals.add(new BlackjackScreen.VisualCard(this.game.dealer().get(d), true, 0, d, this.nextDealAt));
         } else {
            int idx = this.visualCount(false, missingHand);
            this.visuals.add(new BlackjackScreen.VisualCard(hands.get(missingHand).cards().get(idx), false, missingHand, idx, this.nextDealAt));
         }

         this.nextDealAt = this.nextDealAt + this.interval();
      }
      if (this.holeWasHidden && !this.game.holeHidden()) {
         this.holeWasHidden = false;

         for (BlackjackScreen.VisualCard v : this.visuals) {
            if (v.dealer && v.index == 1) {
               v.flipAt = Math.max(now, v.dealtAt + this.flight());
               this.nextDealAt = Math.max(this.nextDealAt, v.flipAt + this.interval());
            }
         }
      }
   }

   private void update() {
      long now = System.currentTimeMillis();
      if (this.game.phase() == BlackjackGame.Phase.DEALER && this.allLanded() && now - this.lastDealerStep > Math.round(560.0F / this.module.tempo())) {
         this.lastDealerStep = now;
         this.game.dealerStep();
         this.syncVisuals();
      }

      if (this.game.phase() == BlackjackGame.Phase.DONE && !this.settled && this.allLanded() && this.flipsDone(now)) {
         this.settled = true;
         this.settle();
      }
   }

   private boolean flipsDone(long now) {
      for (BlackjackScreen.VisualCard v : this.visuals) {
         if (v.flipAt >= 0L && now < v.flipAt + this.flight()) {
            return false;
         }
      }

      return true;
   }

   private void settle() {
      BlackjackGame.Outcome o = this.game.outcome();
      int payout = this.game.payout();
      this.module.setChips(this.module.chips() + payout);
      this.lastDelta = payout - this.game.bet();
      this.shownOutcome = o;
      this.outcomeAt = System.currentTimeMillis();

      // Statistik je Hand (nach dem Teilen zählen beide)
      for (BlackjackGame.Hand h : this.game.hands()) {
         BlackjackGame.Outcome ho = h.outcome();
         boolean win = ho == BlackjackGame.Outcome.WIN || ho == BlackjackGame.Outcome.BLACKJACK || ho == BlackjackGame.Outcome.DEALER_BUST;
         this.module.record(win, ho == BlackjackGame.Outcome.PUSH, ho == BlackjackGame.Outcome.BLACKJACK);
      }
      switch (o) {
         case BLACKJACK:
            this.sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.2F);
            this.burst(140);
            break;
         case WIN:
         case DEALER_BUST:
            this.sound(SoundEvents.PLAYER_LEVELUP, 1.4F);
            this.burst(70);
            break;
         case PUSH:
            this.sound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F);
            break;
         default:
            this.sound(SoundEvents.VILLAGER_NO, 1.0F);
      }

      if (this.bet > this.module.chips() && this.module.chips() > 0) {
         this.bet = Math.max(10, this.module.chips());
      }
   }

   private void burst(int count) {
      if (this.module.confetti()) {
         int[] colors = new int[]{-4160260, -7643914, -278748, -11870592, -10443270, -757066};

         for (int i = 0; i < count; i++) {
            float x = this.width / 2.0F + (this.random.nextFloat() - 0.5F) * 60.0F;
            float y = this.height / 2.0F;
            float a = (float)(this.random.nextFloat() * Math.PI * 2.0);
            float sp = 80.0F + this.random.nextFloat() * 180.0F;
            this.confetti
               .add(
                  new float[]{
                     x,
                     y,
                     (float)Math.cos(a) * sp,
                     (float)Math.sin(a) * sp - 120.0F,
                     colors[this.random.nextInt(colors.length)],
                     this.random.nextFloat() * 6.0F,
                     (this.random.nextFloat() - 0.5F) * 12.0F
                  }
               );
         }
      }
   }

   private void sound(SoundEvent sound, float pitch) {
      if (this.module.sounds()) {
         this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.6F));
      }
   }

   @Override
   public void renderBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
   }

   private int tableW() {
      return Math.min(this.width - 16, 440);
   }

   private int tableH() {
      return Math.min(this.height - 16, 260);
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
      long nanos = System.nanoTime();
      float dt = Math.min(0.05F, (float)(nanos - this.lastFrame) / 1.0E9F);
      this.lastFrame = nanos;
      this.update();
      float a = this.open.update(1.0F);
      context.fill(0, 0, this.width, this.height, Render2D.alpha(-804911863, a));
      this.buttons.clear();
      int tw = this.tableW();
      int th = this.tableH();
      int tx = (this.width - tw) / 2;
      int ty = (this.height - th) / 2;
      Render2D.roundRect(context, tx - 3, ty - 3, tw + 6, th + 6, 16, -14016699);
      Render2D.roundRect(context, tx, ty, tw, th, 14, -15265498);
      Render2D.roundRect(context, tx + 6, ty + 6, tw - 12, th - 12, 11, -14739661);
      String title = "MEOWL BLACKJACK";
      context.text(
         this.font,
         Component.literal(title).withStyle(ChatFormatting.BOLD),
         tx + (tw - this.font.width(Component.literal(title).withStyle(ChatFormatting.BOLD))) / 2,
         ty + 10,
         -3885571,
         false
      );
      this.small(
         context,
         "Dealer zieht bis 17" + (this.module.dealerHitsSoft17() ? " (auch Soft 17)" : "") + " · Blackjack zahlt 3:2",
         tx + tw / 2,
         ty + 21,
         -7569488,
         true
      );
      int sx = tx + 14;
      int sy = ty + 38;
      this.statLine(context, sx, sy, "Chips", String.valueOf(this.module.chips()), -278748);
      this.statLine(
         context,
         sx,
         sy + 12,
         "Einsatz",
         String.valueOf(this.game.phase() != BlackjackGame.Phase.BETTING && !this.settled ? this.game.bet() : this.bet),
         -1578509
      );
      this.statLine(context, sx, sy + 30, "Siege", String.valueOf(this.module.wins()), -11870592);
      this.statLine(context, sx, sy + 42, "Niederl.", String.valueOf(this.module.losses()), -495247);
      this.statLine(context, sx, sy + 54, "Unentsch.", String.valueOf(this.module.pushes()), -7035976);
      this.statLine(context, sx, sy + 66, "Blackjacks", String.valueOf(this.module.blackjacks()), -4160260);
      this.statLine(context, sx, sy + 84, "Serie", String.valueOf(this.module.streak()), -1578509);
      this.statLine(context, sx, sy + 96, "Beste", String.valueOf(this.module.bestStreak()), -1578509);
      int deckX = tx + tw - 30 - 18;
      int deckY = ty + 34;

      for (int i = 3; i >= 0; i--) {
         this.drawBack(context, deckX + i, deckY - i, 1.0F);
      }

      int centerX = tx + tw / 2 + 20;
      int dealerY = ty + 40;
      int playerY = ty + th - 42 - 52;
      long now = System.currentTimeMillis();

      int handCount = Math.max(1, this.game.hands().size());

      for (BlackjackScreen.VisualCard v : this.visuals) {
         if (now >= v.dealtAt) {
            int count = (int)this.visuals.stream().filter(o -> o.dealer == v.dealer && (o.dealer || o.hand == v.hand) && now >= o.dealtAt).count();
            int split = v.dealer ? 1 : handCount;
            float stepX = handStep(count, tw / split + (split > 1 ? 60 : 0));
            float handW = 30.0F + (count - 1) * stepX;
            float handX = v.dealer ? centerX : this.handCenter(centerX, v.hand, handCount);
            float targetX = handX - handW / 2.0F + v.index * stepX;
            float targetY = v.dealer ? dealerY : playerY;
            float p = Math.min(1.0F, (float)(now - v.dealtAt) / (float)this.flight());
            float e = 1.0F - (1.0F - p) * (1.0F - p) * (1.0F - p);
            float x = deckX + (targetX - deckX) * e;
            float y = deckY + (targetY - deckY) * e;
            float rot = (1.0F - e) * -0.5F;
            boolean faceUp = !v.dealer || v.index != 1 || v.flipAt >= 0L && now >= v.flipAt + this.flight() / 2L;
            float scaleX = 1.0F;
            if (v.flipAt >= 0L && now >= v.flipAt && now < v.flipAt + this.flight()) {
               float f = (float)(now - v.flipAt) / (float)this.flight();
               scaleX = Math.abs((float)Math.cos(f * Math.PI));
            }

            context.pose().pushMatrix();
            context.pose().translate(x + 15.0F, y + 21.0F);
            context.pose().rotate(rot);
            context.pose().scale(Math.max(0.02F, scaleX), 1.0F);
            context.pose().translate(-15.0F, -21.0F);
            Render2D.roundRect(context, 1, 2, 30, 42, 4, 1342177280);
            if (faceUp) {
               this.drawFace(context, v.card);
            } else {
               this.drawBack(context, 0, 0, 1.0F);
            }

            context.pose().popMatrix();
         }
      }

      this.handScore(context, centerX, dealerY + 21 - 5, true, 0, now, tw);

      for (int h = 0; h < handCount; h++) {
         int hx = Math.round(this.handCenter(centerX, h, handCount));
         this.handScore(context, hx, playerY + 21 - 5, false, h, now, handCount > 1 ? tw / 2 + 60 : tw);
         boolean act = handCount > 1 && this.game.phase() == BlackjackGame.Phase.PLAYER && this.game.activeHand() == h;
         this.small(context, handCount > 1 ? (act ? "▶ HAND " + (h + 1) : "HAND " + (h + 1)) : "DU", hx, playerY + 42 + 4, act ? -278748 : -7569488, true);
      }

      this.small(context, "DEALER", centerX, dealerY - 9, -7569488, true);
      int by = ty + th - 34;
      if (this.game.phase() == BlackjackGame.Phase.PLAYER) {
         int bw = 64;
         int bx = centerX - (bw * 4 + 12) / 2;
         this.button(context, mouseX, mouseY, bx, by, bw, 18, "Karte  [H]", this.canAct(), -8635667, this::hit);
         this.button(context, mouseX, mouseY, bx + bw + 4, by, bw, 18, "Halten  [S]", this.canAct(), -13418155, this::stand);
         this.button(
            context,
            mouseX,
            mouseY,
            bx + (bw + 4) * 2,
            by,
            bw,
            18,
            "Verdoppeln [D]",
            this.canAct() && this.game.canDouble() && this.module.chips() >= this.game.activeBet(),
            -15763858,
            this::doubleDown
         );
         this.button(
            context,
            mouseX,
            mouseY,
            bx + (bw + 4) * 3,
            by,
            bw,
            18,
            "Teilen  [P]",
            this.canAct() && this.game.canSplit() && this.module.chips() >= this.game.activeBet(),
            -6331607,
            this::split
         );
      } else if (this.game.phase() == BlackjackGame.Phase.DEALER) {
         this.small(context, "Dealer ist dran …", centerX, by + 5, -3885571, true);
      } else if (this.settled) {
         int bx = centerX - 120;
         this.button(context, mouseX, mouseY, bx, by, 18, 18, "−", this.bet > 10, -13753017, () -> this.bet = Math.max(10, this.bet - this.step()));
         String b = this.bet + " Chips";
         context.text(this.font, b, bx + 22 + (60 - this.font.width(b)) / 2, by + 5, -278748, false);
         this.button(
            context,
            mouseX,
            mouseY,
            bx + 84,
            by,
            18,
            18,
            "+",
            this.bet < this.module.chips(),
            -13753017,
            () -> this.bet = Math.min(Math.max(10, this.module.chips()), this.bet + this.step())
         );
         String label = this.module.chips() <= 0 ? "Chips auffüllen" : (this.visuals.isEmpty() ? "Austeilen  [Leertaste]" : "Neue Runde  [Leertaste]");
         this.button(context, mouseX, mouseY, bx + 110, by, 130, 18, label, true, -8635667, this::deal);
      }

      this.small(context, "Nur Spielgeld – keine Items, kein echtes Geld.", tx + tw / 2, ty + th - 13, -10660736, true);
      this.renderBanner(context, now);
      this.renderConfetti(context, dt);
      super.render(context, mouseX, mouseY, deltaTicks);
   }

   private int step() {
      return this.bet < 100 ? 10 : (this.bet < 1000 ? 50 : 250);
   }

   private static float handStep(int count, int tableW) {
      float maxHand = tableW - 260;
      return count <= 1 ? 36.0F : Math.min(36.0F, (maxHand - 30.0F) / (count - 1));
   }

   private float handCenter(int centerX, int hand, int hands) {
      return hands <= 1 ? centerX : centerX + (hand == 0 ? -64.0F : 64.0F);
   }

   private void handScore(GuiGraphicsExtractor ctx, int cx, int y, boolean dealer, int hand, long now, int tableW) {
      int count = (int)this.visuals.stream().filter(o -> o.dealer == dealer && (dealer || o.hand == hand) && now >= o.dealtAt).count();
      float handW = 30.0F + Math.max(0, count - 1) * handStep(count, tableW);
      List<BlackjackGame.Card> landed = new ArrayList<>();

      for (BlackjackScreen.VisualCard v : this.visuals) {
         if (v.dealer == dealer && (dealer || v.hand == hand) && now >= v.dealtAt + this.flight() && (!dealer || v.index != 1 || v.flipAt >= 0L && now >= v.flipAt + this.flight() / 2L)) {
            landed.add(v.card);
         }
      }

      if (!landed.isEmpty()) {
         int s = BlackjackGame.score(landed);
         String text = !BlackjackGame.blackjack(landed) || dealer && this.game.holeHidden()
            ? (BlackjackGame.soft(landed) && s < 21 ? "Soft " : "") + s
            : "Blackjack!";
         int color = s > 21 ? -495247 : (s == 21 ? -278748 : -1578509);
         int w = this.font.width(text) + 10;
         int x = (int)(cx + handW / 2.0F) + 8;
         Render2D.roundRect(ctx, x, y, w, 11, 5, -1072558816);
         ctx.text(this.font, text, x + 5, y + 2, color, false);
      }
   }

   private void renderBanner(GuiGraphicsExtractor ctx, long now) {
      if (this.shownOutcome != BlackjackGame.Outcome.NONE) {
         float t = (float)(now - this.outcomeAt) / 1000.0F;
         String title;
         int color;
         switch (this.shownOutcome) {
            case BLACKJACK:
               title = "BLACKJACK!";
               color = -278748;
               break;
            case WIN:
               title = "GEWONNEN";
               color = -11870592;
               break;
            case DEALER_BUST:
               title = "DEALER BUST";
               color = -11870592;
               break;
            case PUSH:
               title = "UNENTSCHIEDEN";
               color = -3418655;
               break;
            case BUST:
               title = "BUST";
               color = -495247;
               break;
            default:
               title = "VERLOREN";
               color = -495247;
         }

         float pop = Math.min(1.0F, t / 0.25F);
         float scale = 2.2F * (pop < 1.0F ? (1.0F - (float)Math.pow(1.0F - pop, 3.0)) * 1.1F : 1.0F);
         boolean lost = this.shownOutcome == BlackjackGame.Outcome.BUST || this.shownOutcome == BlackjackGame.Outcome.LOSE;
         float shake = lost && t < 0.5F ? (float)Math.sin(t * 60.0F) * 4.0F * (0.5F - t) * 2.0F : 0.0F;
         String sub = (this.lastDelta >= 0 ? "+" : "") + this.lastDelta + " Chips";
         int cy = this.height / 2 - 8;
         int bw = Math.round(this.font.width(title) * 2.2F) + 30;
         Render2D.roundRect(ctx, this.width / 2 - bw / 2, cy - 14, bw, 40, 10, -535687904);
         Render2D.roundRect(ctx, this.width / 2 - bw / 2, cy + 24, bw, 2, 1, color);
         ctx.pose().pushMatrix();
         ctx.pose().translate(this.width / 2.0F + shake, cy);
         ctx.pose().scale(scale, scale);
         ctx.text(
            this.font,
            Component.literal(title).withStyle(ChatFormatting.BOLD),
            -this.font.width(Component.literal(title).withStyle(ChatFormatting.BOLD)) / 2,
            -4,
            color,
            true
         );
         ctx.pose().popMatrix();
         this.small(ctx, sub, this.width / 2, cy + 14, this.lastDelta > 0 ? -278748 : -7035976, true);
      }
   }

   private void renderConfetti(GuiGraphicsExtractor ctx, float dt) {
      for (int i = this.confetti.size() - 1; i >= 0; i--) {
         float[] c = this.confetti.get(i);
         c[0] += c[2] * dt;
         c[1] += c[3] * dt;
         c[3] += 260.0F * dt;
         c[2] *= 1.0F - 1.2F * dt;
         c[5] += c[6] * dt;
         if (c[1] > this.height + 10) {
            this.confetti.remove(i);
         } else {
            ctx.pose().pushMatrix();
            ctx.pose().translate(c[0], c[1]);
            ctx.pose().rotate(c[5]);
            ctx.fill(-2, -1, 2, 1, (int)c[4]);
            ctx.pose().popMatrix();
         }
      }
   }

   private void drawFace(GuiGraphicsExtractor ctx, BlackjackGame.Card c) {
      String design = this.module.design();
      boolean nether = "Nether".equals(design);
      int bg = nether ? -14020589 : -527105;
      int border = nether ? -4959479 : -2568971;
      int ink = nether ? (c.red() ? -429290 : -137590) : (c.red() ? -2024120 : -14804178);
      Render2D.roundRect(ctx, 0, 0, 30, 42, 4, border);
      Render2D.roundRect(ctx, 1, 1, 28, 40, 3, bg);
      ctx.text(this.font, c.label(), 3, 3, ink, false);
      ctx.pose().pushMatrix();
      ctx.pose().translate(27.0F, 39.0F);
      ctx.pose().rotate((float) Math.PI);
      ctx.text(this.font, c.label(), 0, 0, ink, false);
      ctx.pose().popMatrix();
      if ("Klassisch".equals(design)) {
         String suit = switch (c.suit()) {
            case HEARTS -> "♥";
            case DIAMONDS -> "♦";
            case SPADES -> "♠";
            case CLUBS -> "♣";
         };
         ctx.pose().pushMatrix();
         ctx.pose().translate(15.0F, 21.0F);
         ctx.pose().scale(2.2F, 2.2F);
         ctx.text(this.font, suit, -this.font.width(suit) / 2, -4, ink, false);
         ctx.pose().popMatrix();
      } else if (c.suit() == BlackjackGame.Suit.HEARTS && !nether) {
         ctx.pose().pushMatrix();
         ctx.pose().translate(8.0F, 14.0F);
         ctx.pose().scale(1.6F, 1.6F);
         ctx.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace("hud/heart/full"), 0, 0, 9, 9);
         ctx.pose().popMatrix();
      } else {
         ItemStack icon = com.lennart.nebula.util.SafeStack.of(switch (c.suit()) {
            case HEARTS -> Items.NETHER_WART;
            case DIAMONDS -> nether ? Items.GOLD_INGOT : Items.DIAMOND;
            case SPADES -> nether ? Items.NETHERITE_SWORD : Items.IRON_SWORD;
            case CLUBS -> nether ? Items.BLAZE_POWDER : Items.EMERALD;
         });
         ctx.item(icon, 7, 13);
      }
   }

   private void drawBack(GuiGraphicsExtractor ctx, int x, int y, float alpha) {
      String design = this.module.design();
      int base;
      int line;
      switch (design) {
         case "Klassisch":
            base = -14796150;
            line = -12887079;
            break;
         case "Nether":
            base = -12907251;
            line = -4959479;
            break;
         default:
            base = -11788907;
            line = -8635667;
      }

      Render2D.roundRect(ctx, x, y, 30, 42, 4, -1449217);
      Render2D.roundRect(ctx, x + 2, y + 2, 26, 38, 3, base);

      for (int i = 0; i < 34; i += 6) {
         ctx.fill(x + 4, y + 4 + i, x + 30 - 4, y + 5 + i, Render2D.alpha(line, 0.35F));
      }

      if ("Meowl".equals(design)) {
         int px = 1;
         int size = 16;
         int ox = x + (30 - size) / 2;
         int oy = y + (42 - size) / 2;

         for (int r = 0; r < size; r++) {
            String row = MeowlSprite.ROWS[r];

            for (int col = 0; col < size; col++) {
               int c = MeowlSprite.color(row.charAt(col));
               if (c != 0) {
                  ctx.fill(ox + col * px, oy + r * px, ox + col * px + px, oy + r * px + px, c);
               }
            }
         }
      } else {
         Render2D.circle(ctx, x + 15, y + 21, 6, line);
      }
   }

   private void statLine(GuiGraphicsExtractor ctx, int x, int y, String label, String value, int color) {
      this.small(ctx, label, x, y + 1, -7569488, false);
      ctx.text(this.font, value, x + 48, y, color, false);
   }

   private void button(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int x, int y, int w, int h, String label, boolean enabled, int color, Runnable action) {
      boolean hov = enabled && Render2D.inside(mouseX, mouseY, x, y, w, h);
      int bg = !enabled ? -14410442 : (hov ? Theme.lerp(color, -1, 0.15F) : color);
      Render2D.roundRect(ctx, x, y + (hov ? -1 : 0), w, h, 6, bg);
      int tw = this.font.width(label);
      if (tw > w - 6) {
         this.small(ctx, label, x + w / 2, y + 6, enabled ? -1 : -10660736, true);
      } else {
         ctx.text(this.font, label, x + (w - tw) / 2, y + (h - 8) / 2 + (hov ? -1 : 0), enabled ? -1 : -10660736, false);
      }

      if (enabled) {
         this.buttons.add(new Object[]{x, y, w, h, action});
      }
   }

   private void small(GuiGraphicsExtractor ctx, String text, int x, int y, int color, boolean centered) {
      ctx.pose().pushMatrix();
      float w = this.font.width(text) * 0.75F;
      ctx.pose().translate(centered ? x - w / 2.0F : x, y);
      ctx.pose().scale(0.75F, 0.75F);
      ctx.text(this.font, text, 0, 0, color, false);
      ctx.pose().popMatrix();
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
         for (Object[] b : List.copyOf(this.buttons)) {
            if (Render2D.inside(click.x(), click.y(), (Integer)b[0], (Integer)b[1], (Integer)b[2], (Integer)b[3])) {
               this.sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.4F);
               ((Runnable)b[4]).run();
               return true;
            }
         }
      }

      return super.mouseClicked(click, doubled);
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      switch (input.key()) {
         case InputConstants.KEY_SPACE:
         case InputConstants.KEY_RETURN:
            this.deal();
            break;
         case InputConstants.KEY_D:
            this.doubleDown();
            break;
         case InputConstants.KEY_H:
            this.hit();
            break;
         case InputConstants.KEY_S:
            this.stand();
            break;
         case InputConstants.KEY_P:
            this.split();
            break;
         default:
            return super.keyPressed(input);
      }

      return true;
   }

   @Override
   public void tick() {
      long now = System.currentTimeMillis();

      for (BlackjackScreen.VisualCard v : this.visuals) {
         long land = v.dealtAt + this.flight();
         if (now >= land && now - land < 50L) {
            this.sound(SoundEvents.BOOK_PAGE_TURN, 1.3F + this.random.nextFloat() * 0.2F);
         }
      }
   }

   @Override
   public void onClose() {
      if (this.settled || this.game.phase() != BlackjackGame.Phase.PLAYER && this.game.phase() != BlackjackGame.Phase.DEALER) {
         if (!this.settled && this.game.phase() == BlackjackGame.Phase.DONE) {
            this.settle();
         }
      } else {
         this.module.setChips(this.module.chips() + this.game.bet());
      }

      this.minecraft.gui.setScreen(this.parent);
   }

   private static final class VisualCard {
      final BlackjackGame.Card card;
      final boolean dealer;
      int hand;
      int index;
      final long dealtAt;
      long flipAt = -1L;

      VisualCard(BlackjackGame.Card card, boolean dealer, int hand, int index, long dealtAt) {
         this.card = card;
         this.dealer = dealer;
         this.hand = hand;
         this.index = index;
         this.dealtAt = dealtAt;
      }
   }
}
