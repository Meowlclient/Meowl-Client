package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.games.GameNet;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.module.mods.Friends;
import com.lennart.nebula.net.MeowlService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;

/**
 * Grundgerüst für Brettspiele im Meowl-Look: Menü (Bot oder Freund), Online-Anbindung über {@link GameNet},
 * Kopfzeile, Statuszeile und Knöpfe. Unterklassen zeichnen nur das Brett.
 */
public abstract class BoardGameScreen extends Screen implements GameNet.Listener {
   protected static final int ACCENT = Section.GAMES.color();
   protected final Screen parent;
   protected final String gameKey;
   protected BoardGameScreen.Mode mode = BoardGameScreen.Mode.MENU;
   protected GameNet.Online online;
   protected int botLevel = 1;
   protected String status = "";
   protected long statusAt;
   private final List<BoardGameScreen.Btn> buttons = new ArrayList<>();
   private final Animation open = new Animation(12.0F, 0.0F);
   protected int px;
   protected int py;
   protected int pw;
   protected int ph;
   private String waitingFor;

   protected BoardGameScreen(Screen parent, String gameKey, String title) {
      super(Component.literal(title));
      this.parent = parent;
      this.gameKey = gameKey;
   }

   protected enum Mode {
      MENU,
      BOT,
      ONLINE
   }

   // ------------------------------------------------------------ Unterklassen

   /** Neues Spiel gegen den Bot (youFirst = du beginnst). */
   protected abstract void startBot(boolean youFirst);

   /** Neues Online-Spiel. */
   protected abstract void startOnline(GameNet.Online game);

   /** Zug des Gegners (online) anwenden; false = ungültig. */
   protected abstract boolean applyRemote(String move);

   protected abstract void renderBoard(GuiGraphics ctx, int x, int y, int w, int h, int mx, int my, float delta);

   protected abstract boolean clickBoard(double mx, double my, int button);

   /** Ist das Spiel vorbei (für Knopf "Nochmal")? */
   protected abstract boolean finished();

   protected int preferredWidth() {
      return 360;
   }

   protected int preferredHeight() {
      return 250;
   }

   // ------------------------------------------------------------ Lebenszyklus

   @Override
   protected void init() {
      GameNet.get().hook();
      GameNet.get().setListener(this.gameKey, this);
      if (Friends.get() != null) {
         Friends.get().refreshStatus();
      }

      GameNet.Online running = GameNet.get().active(this.gameKey);
      if (running != null && this.online == null) {
         this.onStart(running);
         String pending = running.pendingMoves().toString().trim();
         running.pendingMoves().setLength(0);
         if (!pending.isEmpty()) {
            for (String m : pending.split(" ")) {
               this.applyRemote(m);
            }
         }
      }
   }

   @Override
   public void removed() {
      GameNet.get().setListener(this.gameKey, null);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void onClose() {
      if (this.mode != BoardGameScreen.Mode.MENU && !this.finished() && this.mode == BoardGameScreen.Mode.ONLINE) {
         this.leaveOnline("leave");
      }

      this.minecraft.setScreen(this.parent);
   }

   protected void leaveOnline(String reason) {
      if (this.online != null) {
         GameNet.get().end(this.online, reason);
      }
   }

   protected void say(String text) {
      this.status = text;
      this.statusAt = System.currentTimeMillis();
   }

   protected void sound(SoundEvent sound, float pitch) {
      this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, 0.6F));
   }

   protected String opponentName() {
      return this.mode == BoardGameScreen.Mode.ONLINE && this.online != null ? this.online.opponent() : "Meowl-Bot (" + levelName(this.botLevel) + ")";
   }

   protected static String levelName(int level) {
      return switch (level) {
         case 0 -> "Leicht";
         case 2 -> "Schwer";
         default -> "Mittel";
      };
   }

   // ------------------------------------------------------------ GameNet.Listener

   @Override
   public void onStart(GameNet.Online game) {
      this.online = game;
      this.mode = BoardGameScreen.Mode.ONLINE;
      this.waitingFor = null;
      this.startOnline(game);
      this.say("Gegen " + game.opponent() + " – " + (game.first() ? "du beginnst" : game.opponent() + " beginnt"));
      this.sound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4F);
   }

   @Override
   public void onRemoteMove(GameNet.Online game, String move) {
      if (this.online != null && game.id().equals(this.online.id())) {
         if (!this.applyRemote(move)) {
            this.say("§cUngültiger Zug vom Gegner – Spiel beendet");
            GameNet.get().end(game, "desync");
         }
      }
   }

   @Override
   public void onRemoteEnd(GameNet.Online game, String reason, String by) {
      if (this.online != null && game.id().equals(this.online.id())) {
         this.onlineEnded(reason, by);
      }
   }

   /** Gegner hat aufgegeben/verlassen. */
   protected void onlineEnded(String reason, String by) {
      this.say((reason.equals("resign") ? "§a" : "§e") + GameNet.reasonText(reason, by) + (reason.equals("resign") ? " – du gewinnst!" : ""));
      this.online = null;
   }

   @Override
   public void onInviteDeclined(String by) {
      this.waitingFor = null;
      this.say("§c" + by + " hat abgelehnt");
   }

   // ------------------------------------------------------------ Zeichnen

   @Override
   public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
      ctx.fill(0, 0, this.width, this.height, Theme.OVERLAY);
   }

   @Override
   public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
      this.renderBackground(ctx, mouseX, mouseY, delta);
      float o = this.open.update(1.0F);
      this.buttons.clear();
      this.pw = Math.min(this.width - 16, this.preferredWidth());
      this.ph = Math.min(this.height - 16, this.preferredHeight());
      this.px = (this.width - this.pw) / 2;
      this.py = (this.height - this.ph) / 2 + Math.round((1.0F - o) * 8.0F);
      Render2D.glow(ctx, this.px, this.py, this.pw, this.ph, 8, ACCENT, 0.45F * o);
      Render2D.roundRect(ctx, this.px, this.py, this.pw, this.ph, 8, Theme.PANEL_BG);
      Render2D.roundRect(ctx, this.px, this.py, this.pw, 24, 8, Theme.HEADER);
      ctx.fill(this.px, this.py + 16, this.px + this.pw, this.py + 24, Theme.HEADER);
      ctx.fill(this.px, this.py + 24, this.px + this.pw, this.py + 25, Render2D.alpha(ACCENT, 0.7F));
      ctx.drawString(this.font, Component.literal(this.title.getString()).withStyle(ChatFormatting.BOLD), this.px + 10, this.py + 8, Theme.TEXT, false);
      String sub = this.mode == BoardGameScreen.Mode.MENU ? "Wähle deinen Gegner" : "gegen " + this.opponentName();
      int sw = this.font.width(sub);
      ctx.drawString(this.font, sub, this.px + this.pw - sw - 26, this.py + 8, Theme.TEXT_DIM, false);
      this.button(ctx, this.px + this.pw - 20, this.py + 5, 14, 14, "×", mouseX, mouseY, this::onClose);
      int cy = this.py + 30;
      int ch = this.ph - 30 - 22;
      if (this.mode == BoardGameScreen.Mode.MENU) {
         this.renderMenu(ctx, this.px + 10, cy, this.pw - 20, ch, mouseX, mouseY);
      } else {
         this.renderBoard(ctx, this.px + 10, cy, this.pw - 20, ch, mouseX, mouseY, delta);
      }

      this.renderFooter(ctx, mouseX, mouseY);
   }

   private void renderFooter(GuiGraphics ctx, int mx, int my) {
      int y = this.py + this.ph - 19;
      int x = this.px + 10;
      if (this.mode != BoardGameScreen.Mode.MENU) {
         x += this.button(ctx, x, y, 0, 14, "‹ Menü", mx, my, () -> {
            if (this.mode == BoardGameScreen.Mode.ONLINE && !this.finished()) {
               this.leaveOnline("resign");
            }

            this.online = null;
            this.mode = BoardGameScreen.Mode.MENU;
            this.say("");
         }) + 4;
         if (this.finished() && this.mode == BoardGameScreen.Mode.BOT) {
            x += this.button(ctx, x, y, 0, 14, "↻ Nochmal", mx, my, () -> this.startBot(true)) + 4;
         } else if (!this.finished() && this.mode == BoardGameScreen.Mode.ONLINE) {
            x += this.button(ctx, x, y, 0, 14, "⚑ Aufgeben", mx, my, () -> {
               this.leaveOnline("resign");
               this.resigned();
            }) + 4;
         }
      }

      if (!this.status.isEmpty()) {
         int maxW = this.px + this.pw - 10 - x - 4;
         String s = this.font.plainSubstrByWidth(this.status, Math.max(10, maxW));
         ctx.drawString(this.font, s, this.px + this.pw - 10 - this.font.width(s), y + 3, Theme.TEXT_DIM, false);
      }
   }

   /** Du hast aufgegeben. */
   protected void resigned() {
      this.say("§7Du hast aufgegeben");
      this.online = null;
   }

   private void renderMenu(GuiGraphics ctx, int x, int y, int w, int h, int mx, int my) {
      int col = (w - 10) / 2;
      // links: Bot
      this.section(ctx, x, y, col, "Gegen den Bot");
      int by = y + 16;
      for (int lvl = 0; lvl < 3; lvl++) {
         int level = lvl;
         boolean sel = this.botLevel == lvl;
         this.button(ctx, x + lvl * (col / 3), by, col / 3 - 3, 16, (sel ? "● " : "") + levelName(lvl), mx, my, () -> this.botLevel = level);
      }

      by += 22;
      this.bigButton(ctx, x, by, col, 22, "▶ Spielen (du beginnst)", mx, my, () -> {
         this.mode = BoardGameScreen.Mode.BOT;
         this.startBot(true);
         this.say("Viel Glück!");
      });
      by += 26;
      this.button(ctx, x, by, col, 16, "Bot beginnt", mx, my, () -> {
         this.mode = BoardGameScreen.Mode.BOT;
         this.startBot(false);
         this.say("Der Bot beginnt");
      });
      // rechts: Freunde
      int rx = x + col + 10;
      this.section(ctx, rx, y, col, "Gegen Freunde");
      int ry = y + 16;
      if (!MeowlService.get().isConnected()) {
         ctx.drawString(this.font, "§7Nicht mit dem Meowl Server", rx, ry, Theme.TEXT_DIM, false);
         ctx.drawString(this.font, "§7verbunden (Mods → Meowl Server)", rx, ry + 10, Theme.TEXT_DIM, false);
         return;
      }

      long now = System.currentTimeMillis();
      for (GameNet.Invite inv : List.copyOf(GameNet.get().invites().values())) {
         if (inv.game().equals(this.gameKey) && inv.expires() > now && ry < y + h - 18) {
            Render2D.roundRect(ctx, rx, ry, col, 18, 4, Render2D.alpha(ACCENT, 0.18F));
            ctx.drawString(this.font, this.font.plainSubstrByWidth(inv.from() + " lädt ein", col - 70), rx + 5, ry + 5, Theme.TEXT, false);
            this.button(ctx, rx + col - 62, ry + 2, 60, 14, "Annehmen", mx, my, () -> GameNet.get().respond(inv.id(), true));
            ry += 21;
         }
      }

      List<Friends.Info> online = new ArrayList<>();
      if (Friends.get() != null) {
         for (Friends.Info f : Friends.get().infos()) {
            if (f.online()) {
               online.add(f);
            }
         }
      }

      if (online.isEmpty()) {
         ctx.drawString(this.font, "§7Gerade ist kein Freund online.", rx, ry + 2, Theme.TEXT_DIM, false);
         ctx.drawString(this.font, "§8Freunde: Planet „Freunde“", rx, ry + 12, Theme.TEXT_MUTED, false);
      }

      for (Friends.Info f : online) {
         if (ry >= y + h - 18) {
            break;
         }

         boolean waiting = f.name().equals(this.waitingFor);
         Render2D.roundRect(ctx, rx, ry, col, 18, 4, Theme.CARD);
         Render2D.circle(ctx, rx + 7, ry + 9, 2, f.sameServer() ? Theme.SUCCESS : -11549697);
         ctx.drawString(this.font, this.font.plainSubstrByWidth(f.name(), col - 76), rx + 13, ry + 5, Theme.TEXT, false);
         this.button(ctx, rx + col - 62, ry + 2, 60, 14, waiting ? "wartet …" : "Einladen", mx, my, () -> {
            this.waitingFor = f.name();
            GameNet.get().invite(this.gameKey, f.name(), this::say);
         });
         ry += 21;
      }
   }

   private void section(GuiGraphics ctx, int x, int y, int w, String title) {
      ctx.drawString(this.font, Component.literal(title).withStyle(ChatFormatting.BOLD), x, y + 2, ACCENT, false);
      ctx.fill(x, y + 12, x + w, y + 13, Theme.DIVIDER);
   }

   // ------------------------------------------------------------ Knöpfe

   /** Zeichnet einen Knopf (w = 0: Breite aus Text) und gibt die Breite zurück. */
   protected int button(GuiGraphics ctx, int x, int y, int w, int h, String label, int mx, int my, Runnable action) {
      if (w <= 0) {
         w = this.font.width(label) + 12;
      }

      boolean hov = Render2D.inside(mx, my, x, y, w, h);
      Render2D.roundRect(ctx, x, y, w, h, Math.min(6, h / 2), hov ? Theme.CARD_HOVER : Theme.CARD);
      ctx.drawString(this.font, label, x + (w - this.font.width(label)) / 2, y + (h - 8) / 2, hov ? Theme.TEXT : Theme.TEXT_DIM, false);
      this.buttons.add(new BoardGameScreen.Btn(x, y, w, h, action));
      return w;
   }

   protected void bigButton(GuiGraphics ctx, int x, int y, int w, int h, String label, int mx, int my, Runnable action) {
      boolean hov = Render2D.inside(mx, my, x, y, w, h);
      Render2D.roundRect(ctx, x, y, w, h, 6, hov ? ACCENT : Render2D.alpha(ACCENT, 0.75F));
      ctx.drawString(this.font, Component.literal(label).withStyle(ChatFormatting.BOLD), x + (w - this.font.width(Component.literal(label).withStyle(ChatFormatting.BOLD))) / 2, y + (h - 8) / 2, -15724528, false);
      this.buttons.add(new BoardGameScreen.Btn(x, y, w, h, action));
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      double mx = click.x();
      double my = click.y();

      for (BoardGameScreen.Btn b : List.copyOf(this.buttons)) {
         if (click.button() == InputConstants.MOUSE_BUTTON_LEFT && Render2D.inside(mx, my, b.x, b.y, b.w, b.h)) {
            this.sound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F);
            b.action.run();
            return true;
         }
      }

      if (this.mode != BoardGameScreen.Mode.MENU && this.clickBoard(mx, my, click.button())) {
         return true;
      } else {
         return super.mouseClicked(click, doubled);
      }
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      return super.keyPressed(input);
   }

   private record Btn(int x, int y, int w, int h, Runnable action) {
   }
}
