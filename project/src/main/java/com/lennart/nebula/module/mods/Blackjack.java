package com.lennart.nebula.module.mods;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.gui.BlackjackScreen;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.util.ChatUtil;
import net.minecraft.world.item.Items;

public class Blackjack extends Module {
   public static final String DESIGN_MEOWL = "Meowl";
   public static final String DESIGN_CLASSIC = "Klassisch";
   public static final String DESIGN_NETHER = "Nether";
   private final KeybindSetting openKey = this.add(new KeybindSetting("Öffnen-Taste", "Öffnet den Blackjack-Tisch", Bind.NONE));
   private final ModeSetting design = this.add(new ModeSetting("Kartendesign", "Aussehen der Karten", "Meowl", "Meowl", "Klassisch", "Nether"));
   private final BooleanSetting soft17 = this.add(new BooleanSetting("Dealer zieht bei Soft 17", "Dealer nimmt bei 17 mit Ass (als 11) noch eine Karte", true));
   private final NumberSetting tempo = this.add(new NumberSetting("Tempo", "Geschwindigkeit von Austeilen und Dealer", 1.0, 0.5, 2.5, 0.1, "x"));
   private final BooleanSetting sounds = this.add(new BooleanSetting("Sounds", "Karten- und Gewinn-Geräusche", true));
   private final BooleanSetting confetti = this.add(new BooleanSetting("Konfetti", "Kleine Feier bei Sieg und Blackjack", true));
   private final NumberSetting startChips = this.add(
      new NumberSetting("Start-Chips", "Spielgeld zu Beginn bzw. beim Auffüllen", 1000.0, 100.0, 10000.0, 100.0, "")
   );
   final NumberSetting chips = this.hiddenStat("Chips", 1000.0);
   final NumberSetting wins = this.hiddenStat("Siege", 0.0);
   final NumberSetting losses = this.hiddenStat("Niederlagen", 0.0);
   final NumberSetting pushes = this.hiddenStat("Unentschieden", 0.0);
   final NumberSetting blackjacks = this.hiddenStat("Blackjacks", 0.0);
   final NumberSetting streak = this.hiddenStat("Serie", 0.0);
   final NumberSetting bestStreak = this.hiddenStat("Beste Serie", 0.0);
   final NumberSetting lastBet = this.hiddenStat("Letzter Einsatz", 50.0);
   private boolean keyDown;

   public Blackjack() {
      super("Meowl Blackjack", "Blackjack gegen den Meowl-Dealer – nur mit Spielgeld, mit Statistik und Animationen.", Category.MEOWL);
      this.section(Section.GAMES);
      this.icon(Items.PAPER);
      this.add(new ActionSetting("Tisch", "Blackjack-Tisch öffnen", "Öffnen", this::open));
      this.add(new ActionSetting("Statistik", "Siege, Niederlagen, Serie und Chips zurücksetzen", "Zurücksetzen", this::resetStats));
      this.group("Spiel", new Setting[]{this.design, this.soft17, this.tempo, this.sounds, this.confetti, this.startChips});
   }

   private NumberSetting hiddenStat(String name, double def) {
      NumberSetting s = this.add(new NumberSetting("Statistik: " + name, "", def, -1.0E9, 1.0E9, 1.0, ""));
      s.setVisibility(() -> false);
      return s;
   }

   public void open() {
      this.mc.schedule(() -> this.mc.setScreen(new BlackjackScreen(this.mc.screen, this)));
   }

   private void resetStats() {
      for (NumberSetting s : new NumberSetting[]{this.wins, this.losses, this.pushes, this.blackjacks, this.streak, this.bestStreak}) {
         s.set(0.0);
      }

      this.chips.set(this.startChips.get());
      this.save();
      ChatUtil.chat("§dBlackjack-Statistik zurückgesetzt.");
   }

   @Override
   public void onTick() {
      boolean down = this.openKey.get().isPressed();
      if (down && !this.keyDown && this.mc.screen == null) {
         this.open();
      }

      this.keyDown = down;
   }

   public void save() {
      NebulaClient.getInstance().getModuleManager().notifyChanged();
   }

   public String design() {
      return this.design.get();
   }

   public boolean dealerHitsSoft17() {
      return this.soft17.get();
   }

   public float tempo() {
      return (float)this.tempo.get().doubleValue();
   }

   public boolean sounds() {
      return this.sounds.get();
   }

   public boolean confetti() {
      return this.confetti.get();
   }

   public int startChips() {
      return this.startChips.getInt();
   }

   public int stat(NumberSetting s) {
      return s.getInt();
   }

   public NumberSetting chipsSetting() {
      return this.chips;
   }

   public void record(boolean win, boolean push, boolean blackjack) {
      if (push) {
         this.pushes.set(this.pushes.get() + 1.0);
      } else if (win) {
         this.wins.set(this.wins.get() + 1.0);
         this.streak.set(Math.max(0.0, this.streak.get()) + 1.0);
         this.bestStreak.set(Math.max(this.bestStreak.get(), this.streak.get()));
      } else {
         this.losses.set(this.losses.get() + 1.0);
         this.streak.set(0.0);
      }

      if (blackjack) {
         this.blackjacks.set(this.blackjacks.get() + 1.0);
      }

      this.save();
   }

   public int wins() {
      return this.wins.getInt();
   }

   public int losses() {
      return this.losses.getInt();
   }

   public int pushes() {
      return this.pushes.getInt();
   }

   public int blackjacks() {
      return this.blackjacks.getInt();
   }

   public int streak() {
      return this.streak.getInt();
   }

   public int bestStreak() {
      return this.bestStreak.getInt();
   }

   public int chips() {
      return this.chips.getInt();
   }

   public void setChips(int value) {
      this.chips.set((double)Math.max(0, value));
      this.save();
   }

   public int lastBet() {
      return this.lastBet.getInt();
   }

   public void setLastBet(int bet) {
      this.lastBet.set((double)bet);
   }
}
