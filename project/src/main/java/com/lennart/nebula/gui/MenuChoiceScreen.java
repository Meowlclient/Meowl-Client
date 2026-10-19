package com.lennart.nebula.gui;

import com.lennart.nebula.module.mods.ClientSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Erster Start: Frage, ob man das normale Minecraft-Hauptmenü oder das Meowl-Hauptmenü (mit Meowl-Serverliste) will.
 * Klick auf eine Karte oder Taste 1/2. Später umschaltbar (Knopf im Menü bzw. Client-Einstellungen).
 */
public final class MenuChoiceScreen extends Screen {
   /** Klicks notfalls selbst auslösen, falls eine andere Mod sie im Menü abfängt. */
   private final ClickFallback clickFallback = new ClickFallback();

   /** 26.x: keine Texteingabe nötig – sonst nimmt die Windows-Texteingabe Minecraft Fokus und Klicks weg. */
   protected boolean textInput() {
      return false;
   }
   private static final int ACCENT = 0xFFB57BFF;
   private final long opened = System.currentTimeMillis();
   private int cardW;
   private int cardH;
   private int leftX;
   private int rightX;
   private int cardY;

   public MenuChoiceScreen() {
      super(Component.literal("Hauptmenü wählen"));
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public boolean shouldCloseOnEsc() {
      return false;
   }

   @Override
   public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
   }

   private void layout() {
      this.cardW = Math.min(200, (this.width - 60) / 2);
      this.cardH = Math.min(150, this.height - 120);
      int gap = 20;
      this.leftX = this.width / 2 - this.cardW - gap / 2;
      this.rightX = this.width / 2 + gap / 2;
      this.cardY = Math.max(70, this.height / 2 - this.cardH / 2 + 10);
   }

   private static boolean inside(double mx, double my, int x, int y, int w, int h) {
      return mx >= x && my >= y && mx < x + w && my < y + h;
   }

   @Override
   public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
      this.clickFallback.pump(this, this.minecraft);
      this.layout();
      float t = (System.currentTimeMillis() - this.opened) / 1000.0F;
      float in = MeowlTitleScreen.ease(t / 0.6F);
      MeowlSpace.draw(ctx, this.width, this.height, mouseX, mouseY);
      int titleY = this.cardY - 52;
      ctx.drawCenteredString(this.font, "§lWillkommen beim Meowl Client!", this.width / 2, titleY, MeowlSpace.withAlpha(0xFFFFFF, in));
      ctx.drawCenteredString(this.font, "Welches Hauptmenü möchtest du?", this.width / 2, titleY + 16, MeowlSpace.withAlpha(0xC9B8F0, in));
      this.card(ctx, this.leftX, "1", "Minecraft-Menü", new String[]{"Das normale Hauptmenü", "und die normale", "Serverliste"}, false, mouseX, mouseY, in);
      this.card(ctx, this.rightX, "2", "Meowl-Menü", new String[]{"Weltraum-Hauptmenü", "mit eigener Serverliste,", "Server-Icons & Drag"}, true, mouseX, mouseY, in);
      ctx.drawCenteredString(
         this.font, "Du kannst das später jederzeit umschalten", this.width / 2, this.cardY + this.cardH + 16, MeowlSpace.withAlpha(0x9A8CC0, in)
      );
   }

   private void card(GuiGraphics ctx, int x, String key, String title, String[] lines, boolean meowl, int mx, int my, float in) {
      boolean hov = inside(mx, my, x, this.cardY, this.cardW, this.cardH);
      int y = this.cardY + Math.round((1.0F - in) * 20.0F) - (hov ? 2 : 0);
      int bg = meowl ? 0xE0221640 : 0xE01E1E24;
      ctx.fill(x, y, x + this.cardW, y + this.cardH, bg);
      int border = hov ? ACCENT : (meowl ? 0xFF6A4FA8 : 0xFF55555E);
      ctx.fill(x, y, x + this.cardW, y + 1, border);
      ctx.fill(x, y + this.cardH - 1, x + this.cardW, y + this.cardH, border);
      ctx.fill(x, y, x + 1, y + this.cardH, border);
      ctx.fill(x + this.cardW - 1, y, x + this.cardW, y + this.cardH, border);
      // kleines Vorschaubild oben
      int px = x + 12;
      int py = y + 12;
      int pw = this.cardW - 24;
      int ph = Math.max(30, this.cardH / 2 - 20);
      if (meowl) {
         ctx.fillGradient(px, py, px + pw, py + ph, 0xFF120A26, 0xFF2A1A52);
         for (int i = 0; i < 14; i++) {
            int sx = px + (i * 37 + 11) % pw;
            int sy = py + (i * 23 + 7) % ph;
            ctx.fill(sx, sy, sx + 1, sy + 1, 0xFFE8DDFF);
         }

         MeowlSpace.disc(ctx, px + pw * 0.72F, py + ph * 0.55F, ph * 0.32F, 0xFF4B37A0);
         MeowlSpace.disc(ctx, px + pw * 0.68F, py + ph * 0.5F, ph * 0.2F, 0xFF6048C0);
         for (int i = 0; i < 3; i++) {
            ctx.fill(px + 8, py + 10 + i * 9, px + 8 + pw / 3, py + 16 + i * 9, 0xAA8B6BD8);
         }
      } else {
         ctx.fillGradient(px, py, px + pw, py + ph, 0xFF6FA8DC, 0xFF3E6E3A);
         ctx.fill(px, py + ph * 2 / 3, px + pw, py + ph, 0xFF5C8A3A);
         for (int i = 0; i < 3; i++) {
            int bw = pw / 2;
            ctx.fill(px + (pw - bw) / 2, py + 8 + i * 9, px + (pw + bw) / 2, py + 14 + i * 9, 0xCC6E6E6E);
         }
      }

      int ty = py + ph + 10;
      ctx.drawCenteredString(this.font, "§l" + title, x + this.cardW / 2, ty, meowl ? 0xFFE3D7FF : 0xFFFFFFFF);
      for (int i = 0; i < lines.length; i++) {
         ctx.drawCenteredString(this.font, lines[i], x + this.cardW / 2, ty + 14 + i * 10, 0xFFA9A0C0);
      }

      ctx.drawString(this.font, "[" + key + "]", x + 6, y + this.cardH - 12, hov ? ACCENT : 0xFF6E6688, false);
   }

   private void choose(boolean meowl) {
      ClientSettings.chooseMenu(meowl);
      // TitleScreen wird über setScreen je nach Wahl durch das Meowl-Menü ersetzt
      this.minecraft.setScreen(new TitleScreen());
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      this.clickFallback.onEvent();
      if (click.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
         this.layout();
         if (inside(click.x(), click.y(), this.leftX, this.cardY, this.cardW, this.cardH)) {
            this.choose(false);
            return true;
         }

         if (inside(click.x(), click.y(), this.rightX, this.cardY, this.cardW, this.cardH)) {
            this.choose(true);
            return true;
         }
      }

      return super.mouseClicked(click, doubled);
   }

   @Override
   public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
      // Tasten 1/2 (falls eine andere Mod Mausklicks abfängt)
      if (input.key() == 49) {
         this.choose(false);
         return true;
      } else if (input.key() == 50) {
         this.choose(true);
         return true;
      }

      return super.keyPressed(input);
   }
}
