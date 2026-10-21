package com.lennart.nebula.gui;

import com.lennart.nebula.update.UpdateInfo;
import com.lennart.nebula.update.UpdateManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Fragt, ob ein neues Meowl-Update installiert werden soll – nichts wird ohne Zustimmung geladen oder installiert.
 * Zeigt die Neuerungen, dann den Download-Fortschritt und am Ende, ob Minecraft jetzt zum Installieren beendet werden soll.
 */
public final class UpdatePromptScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private static final int ACCENT = 0xFFB57BFF;
   private final Screen parent;
   private final UpdateManager updates;
   private final ClickFallback clickFallback = new ClickFallback();
   private final long opened = System.currentTimeMillis();
   private int boxX;
   private int boxY;
   private int boxW;
   private int boxH;
   private int[] yes = new int[4];
   private int[] no = new int[4];

   public UpdatePromptScreen(Screen parent, UpdateManager updates) {
      super(Component.literal("Meowl-Update"));
      this.parent = parent;
      this.updates = updates;
   }

   /** 26.x: keine Texteingabe nötig. */
   protected boolean textInput() {
      return false;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void renderBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
   }

   @Override
   public void onClose() {
      this.later();
   }

   private void later() {
      this.updates.declineCurrent();
      this.minecraft.gui.setScreen(this.parent);
   }

   private List<FormattedCharSequence> notes(int width) {
      UpdateInfo info = this.updates.latest();
      List<FormattedCharSequence> out = new ArrayList<>();
      if (info == null || info.changelog() == null || info.changelog().isBlank()) {
         return out;
      }

      for (String line : info.changelog().split("\\R")) {
         if (!line.isBlank() && !line.startsWith("Meowl Client")) {
            out.addAll(this.font.split(Component.literal(line.strip()), width));
         }

         if (out.size() > 14) {
            break;
         }
      }

      return out.subList(0, Math.min(out.size(), 14));
   }

   private static boolean inside(double mx, double my, int[] r) {
      return mx >= r[0] && my >= r[1] && mx < r[0] + r[2] && my < r[1] + r[3];
   }

   @Override
   public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      this.clickFallback.pump(this, this.minecraft);
      MeowlSpace.draw(ctx, this.width, this.height, mouseX, mouseY);
      float in = MeowlTitleScreen.ease((System.currentTimeMillis() - this.opened) / 500.0F);
      UpdateManager.State state = this.updates.state();
      UpdateInfo info = this.updates.latest();
      this.boxW = Math.min(320, this.width - 40);
      List<FormattedCharSequence> notes = this.notes(this.boxW - 24);
      this.boxH = 96 + notes.size() * 10;
      this.boxX = (this.width - this.boxW) / 2;
      this.boxY = Math.max(10, (this.height - this.boxH) / 2) + Math.round((1.0F - in) * 16.0F);
      ctx.fill(this.boxX, this.boxY, this.boxX + this.boxW, this.boxY + this.boxH, 0xE81A1430);
      ctx.fill(this.boxX, this.boxY, this.boxX + this.boxW, this.boxY + 1, ACCENT);
      int cx = this.width / 2;
      int y = this.boxY + 12;
      String version = info == null ? "?" : info.version().toString();
      ctx.centeredText(this.font, "§lUpdate verfügbar", cx, y, 0xFFFFFFFF);
      y += 14;
      ctx.centeredText(this.font, "Meowl Client " + this.updates.currentVersion() + " → §d" + version, cx, y, 0xFFC9B8F0);
      y += 18;
      for (FormattedCharSequence line : notes) {
         ctx.text(this.font, line, this.boxX + 12, y, 0xFFB8B0D0, false);
         y += 10;
      }

      y = this.boxY + this.boxH - 32;
      String yesText;
      String noText;
      switch (state) {
         case DOWNLOADING -> {
            int bw = this.boxW - 24;
            ctx.fill(this.boxX + 12, y + 6, this.boxX + 12 + bw, y + 10, 0xFF2C2440);
            ctx.fill(this.boxX + 12, y + 6, this.boxX + 12 + Math.round(bw * this.updates.progress()), y + 10, ACCENT);
            ctx.centeredText(this.font, "Lädt … " + Math.round(this.updates.progress() * 100.0F) + " %", cx, y + 14, 0xFFC9B8F0);
            this.yes = new int[4];
            this.no = new int[4];
            return;
         }
         case READY -> {
            yesText = "Minecraft beenden & installieren";
            noText = "Beim Beenden installieren";
         }
         case FAILED -> {
            yesText = "Nochmal versuchen";
            noText = "Später";
         }
         default -> {
            yesText = "Jetzt aktualisieren";
            noText = "Später";
         }
      }

      int bw = (this.boxW - 36) / 2;
      this.yes = new int[]{this.boxX + 12, y, bw, 20};
      this.no = new int[]{this.boxX + 24 + bw, y, bw, 20};
      this.button(ctx, this.yes, yesText, true, mouseX, mouseY);
      this.button(ctx, this.no, noText, false, mouseX, mouseY);
      if (state == UpdateManager.State.FAILED) {
         ctx.centeredText(this.font, this.updates.message(), cx, y - 12, 0xFFFF8080);
      }
   }

   private void button(GuiGraphicsExtractor ctx, int[] r, String text, boolean main, int mx, int my) {
      boolean hov = inside(mx, my, r);
      int bg = main ? (hov ? 0xFF7A52C8 : 0xFF5E3FA6) : (hov ? 0xFF3A3150 : 0xFF2A2340);
      ctx.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], bg);
      String t = this.font.plainSubstrByWidth(text, r[2] - 6);
      ctx.centeredText(this.font, t, r[0] + r[2] / 2, r[1] + 6, 0xFFFFFFFF);
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      this.clickFallback.onEvent();
      if (click.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
         UpdateManager.State state = this.updates.state();
         if (inside(click.x(), click.y(), this.yes)) {
            if (state == UpdateManager.State.READY) {
               this.minecraft.stop();
            } else {
               this.updates.download();
            }

            return true;
         }

         if (inside(click.x(), click.y(), this.no)) {
            this.later();
            return true;
         }
      }

      return super.mouseClicked(click, doubled);
   }
}
