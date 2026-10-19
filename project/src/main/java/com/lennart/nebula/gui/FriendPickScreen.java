package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.module.mods.Friends;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Friend Locator: Freunde ankreuzen, die deinen Standort (oder Notruf) sehen. Anklickbar sind nur gegenseitige
 * Freunde, die gerade auf demselben Server online sind.
 */
public class FriendPickScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private static final int ROW = 18;
   private final Consumer<List<String>> onPick;
   private final boolean panic;
   private final int accent;
   private final List<Friends.Info> friends = new ArrayList<>();
   private final Set<String> chosen = new LinkedHashSet<>();
   private final long opened = System.currentTimeMillis();
   private int px;
   private int py;
   private int pw;
   private int ph;
   private float scroll;

   /** @param onPick bekommt die angekreuzten Namen */
   public FriendPickScreen(boolean panic, Consumer<List<String>> onPick) {
      super(Component.literal(panic ? "Notruf senden an …" : "Standort teilen mit …"));
      this.onPick = onPick;
      this.panic = panic;
      this.accent = panic ? 0xFFFF4D4D : 0xFF4FA3FF;
   }

   @Override
   protected void init() {
      this.friends.clear();
      Friends f = Friends.get();
      if (f != null) {
         f.refreshStatus();
         this.friends.addAll(f.infos());
         this.friends.sort((a, b) -> Boolean.compare(reachable(b), reachable(a)));
      }

      // beim Notruf sind alle erreichbaren Freunde schon angekreuzt
      if (this.panic && this.chosen.isEmpty()) {
         for (Friends.Info i : this.friends) {
            if (reachable(i)) {
               this.chosen.add(i.name());
            }
         }
      }
   }

   private static boolean reachable(Friends.Info i) {
      return i.mutual() && i.online() && i.sameServer();
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void renderBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
   }

   private void layout() {
      this.pw = Math.min(250, this.width - 40);
      this.ph = Math.min(this.height - 40, 80 + Math.max(1, this.friends.size()) * ROW + 30);
      this.px = (this.width - this.pw) / 2;
      this.py = (this.height - this.ph) / 2;
   }

   private int listTop() {
      return this.py + 52;
   }

   private int listBottom() {
      return this.py + this.ph - 30;
   }

   private int rowY(int i) {
      return this.listTop() + i * ROW - Math.round(this.scroll);
   }

   private int reachableCount() {
      int n = 0;

      for (Friends.Info i : this.friends) {
         if (reachable(i)) {
            n++;
         }
      }

      return n;
   }

   @Override
   public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      Friends f = Friends.get();
      if (f != null && f.infos().size() != this.friends.size()) {
         this.init();
      }

      this.layout();
      float e = Math.min(1.0F, (System.currentTimeMillis() - this.opened) / 160.0F);
      ctx.fill(0, 0, this.width, this.height, Math.round(140.0F * e) << 24 | 0x05060C);
      Render2D.roundRect(ctx, this.px, this.py, this.pw, this.ph, 8, 0xE0101420);
      Render2D.roundRect(ctx, this.px, this.py, this.pw, 3, 2, this.accent);
      ctx.centeredText(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), this.width / 2, this.py + 9, -1);
      ctx.centeredText(this.font, "Freunde ankreuzen – nur Freunde auf diesem Server", this.width / 2, this.py + 21, -7564628);
      int ax = this.px + 10;
      int aw = this.pw - 20;
      boolean all = this.reachableCount() > 0 && this.chosen.size() >= this.reachableCount();
      boolean allHot = Render2D.inside(mouseX, mouseY, ax, this.py + 33, aw, 14);
      Render2D.roundRect(ctx, ax, this.py + 33, aw, 14, 4, allHot ? 0xE0303A58 : 0xC01C2234);
      ctx.centeredText(this.font, all ? "Keinen auswählen" : "Alle Freunde hier auswählen", this.width / 2, this.py + 36, -1);
      int top = this.listTop();
      int bottom = this.listBottom();
      ctx.enableScissor(this.px, top, this.px + this.pw, bottom);
      if (this.friends.isEmpty()) {
         ctx.centeredText(this.font, "Noch keine Freunde – Modul „Freunde“", this.width / 2, top + 6, -7564628);
      }

      for (int i = 0; i < this.friends.size(); i++) {
         Friends.Info info = this.friends.get(i);
         int y = this.rowY(i);
         boolean ok = reachable(info);
         boolean on = this.chosen.contains(info.name());
         boolean hot = ok && mouseY >= top && mouseY < bottom && Render2D.inside(mouseX, mouseY, ax, y, aw, ROW - 2);
         Render2D.roundRect(ctx, ax, y, aw, ROW - 2, 5, hot ? 0xE0303A58 : (ok ? 0xC01C2234 : 0x60181C28));
         // Kästchen
         Render2D.roundRect(ctx, ax + 5, y + 3, 10, 10, 2, ok ? (on ? this.accent : 0xFF2A3045) : 0xFF20242E);
         if (on) {
            ctx.text(this.font, "✔", ax + 6, y + 4, -1, false);
         }

         ctx.text(this.font, info.name(), ax + 21, y + 4, ok ? -1 : -10656900, false);
         String state = !info.mutual() ? "nicht gegenseitig" : (!info.online() ? "offline" : (!info.sameServer() ? "anderer Server" : "hier"));
         ctx.text(this.font, state, ax + aw - 6 - this.font.width(state), y + 4, ok ? -5000269 : -10656900, false);
      }

      ctx.disableScissor();
      int by = this.py + this.ph - 24;
      int half = (aw - 6) / 2;
      boolean can = !this.chosen.isEmpty();
      boolean goHot = can && Render2D.inside(mouseX, mouseY, ax, by, half, 16);
      boolean cancelHot = Render2D.inside(mouseX, mouseY, ax + half + 6, by, half, 16);
      Render2D.roundRect(ctx, ax, by, half, 16, 5, !can ? 0x60202430 : (goHot ? this.accent : (this.accent & 16777215 | 0xB0000000)));
      String go = (this.panic ? "Notruf senden" : "Teilen") + (can ? " (" + this.chosen.size() + ")" : "");
      ctx.centeredText(this.font, go, ax + half / 2, by + 4, can ? -1 : -10656900);
      Render2D.roundRect(ctx, ax + half + 6, by, half, 16, 5, cancelHot ? 0xE0303A58 : 0xE0262E46);
      ctx.centeredText(this.font, "Abbrechen", ax + half + 6 + half / 2, by + 4, -1);
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) {
         return super.mouseClicked(click, doubled);
      }

      double mx = click.x();
      double my = click.y();
      int ax = this.px + 10;
      int aw = this.pw - 20;
      if (Render2D.inside(mx, my, ax, this.py + 33, aw, 14)) {
         boolean all = this.reachableCount() > 0 && this.chosen.size() >= this.reachableCount();
         this.chosen.clear();
         if (!all) {
            for (Friends.Info i : this.friends) {
               if (reachable(i)) {
                  this.chosen.add(i.name());
               }
            }
         }

         return true;
      }

      int by = this.py + this.ph - 24;
      int half = (aw - 6) / 2;
      if (Render2D.inside(mx, my, ax, by, half, 16)) {
         if (!this.chosen.isEmpty()) {
            List<String> names = new ArrayList<>(this.chosen);
            this.onClose();
            this.onPick.accept(names);
         }

         return true;
      }

      if (Render2D.inside(mx, my, ax + half + 6, by, half, 16)) {
         this.onClose();
         return true;
      }

      if (my >= this.listTop() && my < this.listBottom()) {
         for (int i = 0; i < this.friends.size(); i++) {
            Friends.Info info = this.friends.get(i);
            if (reachable(info) && Render2D.inside(mx, my, ax, this.rowY(i), aw, ROW - 2)) {
               if (!this.chosen.remove(info.name())) {
                  this.chosen.add(info.name());
               }

               return true;
            }
         }
      }

      return true;
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double h, double v) {
      float max = Math.max(0, this.friends.size() * ROW - (this.listBottom() - this.listTop()));
      this.scroll = Math.max(0.0F, Math.min(max, this.scroll - (float)v * ROW));
      return true;
   }
}
