package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.widget.MeowlTextField;
import com.lennart.nebula.module.mods.Tpa;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * TPA-Menü: links deine Spieler (Klick = /tpa, kleiner Knopf = herholen, × = entfernen), unten hinzufügen (auch aus der
 * Tab-Liste), rechts eingehende Anfragen mit Annehmen/Ablehnen.
 */
public class TpaScreen extends Screen {
   private static final int ROW = 18;
   private final Tpa tpa;
   private final MeowlTextField input = new MeowlTextField(16).placeholder("Spieler hinzufügen …");
   private final List<Runnable> clicks = new ArrayList<>();
   private final List<int[]> rects = new ArrayList<>();
   private boolean showOnline;
   private float scrollLeft;

   public TpaScreen(Tpa tpa) {
      super(Component.literal("TPA"));
      this.tpa = tpa;
      this.input.onSubmit(s -> {
         this.tpa.addPlayer(s);
         this.input.setText("");
      });
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
   }

   private boolean button(GuiGraphics ctx, int x, int y, int w, int h, String text, int color, int hotColor, int mouseX, int mouseY, Runnable action) {
      boolean hot = Render2D.inside(mouseX, mouseY, x, y, w, h);
      Render2D.roundRect(ctx, x, y, w, h, 4, hot ? hotColor : color);
      ctx.drawCenteredString(this.font, text, x + w / 2, y + (h - 8) / 2, -1);
      this.rects.add(new int[]{x, y, w, h});
      this.clicks.add(action);
      return hot;
   }

   @Override
   public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
      this.rects.clear();
      this.clicks.clear();
      ctx.fill(0, 0, this.width, this.height, 0xB005060C);
      int pw = Math.min(460, this.width - 20);
      int ph = Math.min(280, this.height - 20);
      int px = (this.width - pw) / 2;
      int py = (this.height - ph) / 2;
      Render2D.roundRect(ctx, px, py, pw, ph, 8, 0xF0101420);
      ctx.drawString(this.font, Component.literal("TPA").withStyle(ChatFormatting.BOLD), px + 10, py + 8, -1, true);
      int colW = (pw - 30) / 2;
      int lx = px + 10;
      int rx = lx + colW + 10;
      int top = py + 24;
      // ---- links: Spieler
      ctx.drawString(this.font, this.showOnline ? "Online auf dem Server" : "Deine Spieler", lx, top, -3885571, false);
      this.button(ctx, lx + colW - 70, top - 3, 70, 13, this.showOnline ? "‹ Meine" : "+ Online", 0xE0262E46, 0xE08B5CF6, mouseX, mouseY, () -> this.showOnline = !this.showOnline);
      List<String> list = this.showOnline ? this.tpa.online() : this.tpa.players();
      int listTop = top + 14;
      int listBottom = py + ph - 34;
      ctx.enableScissor(lx, listTop, lx + colW, listBottom);
      if (list.isEmpty()) {
         ctx.drawString(this.font, this.showOnline ? "Niemand online" : "Noch keine Spieler – unten hinzufügen", lx + 4, listTop + 4, -7564628, false);
      }

      for (int i = 0; i < list.size(); i++) {
         String n = list.get(i);
         int y = listTop + i * ROW - Math.round(this.scrollLeft);
         if (y + ROW < listTop || y > listBottom) {
            continue;
         }

         boolean saved = this.tpa.players().contains(n);
         boolean hot = mouseY >= listTop && mouseY < listBottom && Render2D.inside(mouseX, mouseY, lx, y, colW - 60, ROW - 2);
         Render2D.roundRect(ctx, lx, y, colW, ROW - 2, 4, hot ? 0xE0303A58 : 0xC01C2234);
         ctx.drawString(this.font, n, lx + 6, y + 4, -1, false);
         if (mouseY >= listTop && mouseY < listBottom) {
            this.rects.add(new int[]{lx, y, colW - 60, ROW - 2});
            this.clicks.add(() -> this.tpa.sendTpa(n));
         }

         int bx = lx + colW - 58;
         if (mouseY >= listTop && mouseY < listBottom) {
            this.button(ctx, bx, y + 1, 30, ROW - 4, "↙", 0xE0262E46, 0xE04FA3FF, mouseX, mouseY, () -> this.tpa.sendHere(n));
            if (this.showOnline) {
               this.button(ctx, bx + 32, y + 1, 26, ROW - 4, saved ? "✔" : "+", 0xE0262E46, 0xE05BE38A, mouseX, mouseY, () -> this.tpa.addPlayer(n));
            } else {
               this.button(ctx, bx + 32, y + 1, 26, ROW - 4, "×", 0xE0262E46, 0xE0FF5F5F, mouseX, mouseY, () -> this.tpa.removePlayer(n));
            }
         }
      }

      ctx.disableScissor();
      this.input.setBounds(lx, py + ph - 28, colW, 16);
      this.input.render(ctx, this.font, false);
      // ---- rechts: Anfragen
      ctx.drawString(this.font, "Anfragen an dich", rx, top, -3885571, false);
      List<String> req = this.tpa.requests();
      if (req.isEmpty()) {
         ctx.drawString(this.font, "Keine offenen Anfragen", rx + 4, top + 18, -7564628, false);
      }

      for (int i = 0; i < req.size(); i++) {
         String n = req.get(i);
         int y = top + 14 + i * (ROW + 4);
         Render2D.roundRect(ctx, rx, y, colW, ROW + 2, 4, 0xC01C2234);
         boolean here = this.tpa.isHereRequest(n);
         Render2D.roundRect(ctx, rx, y, 3, ROW + 2, 1, here ? 0xFF4FA3FF : 0xFFB57BFF);
         ctx.drawString(this.font, n, rx + 7, y + 2, -1, false);
         ctx.drawString(this.font, here ? "holt dich zu sich" : "will zu dir", rx + 7, y + 11, here ? 0xFF7FBFFF : 0xFFC9A6FF, false);
         String age = this.tpa.requestAge(n) + " s";
         ctx.drawString(this.font, age, rx + colW - 112 - this.font.width(age), y + 6, -7564628, false);
         this.button(ctx, rx + colW - 106, y + 2, 56, ROW - 2, "Annehmen", 0xE0306A40, 0xE05BE38A, mouseX, mouseY, () -> this.tpa.accept(n));
         this.button(ctx, rx + colW - 48, y + 2, 46, ROW - 2, "Ablehnen", 0xE06A3030, 0xE0FF5F5F, mouseX, mouseY, () -> this.tpa.deny(n));
      }

      ctx.drawString(this.font, "Klick = /tpa · ↙ = herholen", rx, py + ph - 24, -10656900, false);
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (this.input.mouseClicked(click.x(), click.y(), this.font)) {
         return true;
      } else if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
         // von hinten prüfen: Knöpfe liegen über den Zeilen
         for (int i = this.rects.size() - 1; i >= 0; i--) {
            int[] r = this.rects.get(i);
            if (Render2D.inside(click.x(), click.y(), r[0], r[1], r[2], r[3])) {
               this.clicks.get(i).run();
               return true;
            }
         }
      }

      return super.mouseClicked(click, doubled);
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double h, double v) {
      this.scrollLeft = Math.max(0.0F, this.scrollLeft - (float)v * ROW);
      return true;
   }

   @Override
   public boolean charTyped(CharacterEvent input) {
      if (!this.input.isFocused() && input.codepoint() > 32) {
         this.input.setFocused(true);
      }

      return this.input.charTyped(input.codepoint()) || super.charTyped(input);
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      if (this.input.isFocused() && input.key() != InputConstants.KEY_ESCAPE && this.input.keyPressed(input.key(), input.modifiers())) {
         return true;
      } else {
         return super.keyPressed(input);
      }
   }
}
