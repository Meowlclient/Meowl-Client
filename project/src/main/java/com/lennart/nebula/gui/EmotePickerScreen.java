package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.emote.EmoteState;
import com.lennart.nebula.emote.EmoteType;
import com.lennart.nebula.module.mods.Emotes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/**
 * Emote-Auswahl: alle Emotes als Karten mit Live-Vorschau am eigenen Charakter. Emote anklicken und dann einen der
 * 8 Plätze im Rad (oder Karte direkt auf einen Platz ziehen). Doppelklick spielt den Emote sofort ab.
 */
public class EmotePickerScreen extends Screen {
   private static final int ACCENT = 0xFF8B5CF6;
   private static final int PANEL = 0xD0101420;
   private static final int CARD = 0xC01A2032;
   private static final int CARD_HOT = 0xE0283050;
   private static final int CW = 66;
   private static final int CH = 50;
   private static final int GAP = 6;
   private final Emotes module;
   private final EmoteType[] slots;
   private EmoteType selected;
   private EmoteType hoveredCard;
   private int hoveredSlot = -1;
   private EmoteType dragging;
   private double dragX;
   private double dragY;
   private boolean dragMoved;
   private int dragFromSlot = -1;
   private float scroll;
   private long opened = System.currentTimeMillis();
   // Layout
   private int gx;
   private int gy;
   private int gw;
   private int gh;
   private int cols;
   private int px;
   private int py;
   private int pw;
   private int ph;
   private int wheelCx;
   private int wheelCy;
   private int wheelR;

   public EmotePickerScreen(Emotes module) {
      super(Component.literal("Emote-Auswahl"));
      this.module = module;
      this.slots = module.loadout();
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
   }

   private void layout() {
      int margin = 14;
      int top = 34;
      int bottom = this.height - 34;
      this.pw = Math.max(170, Math.min(240, this.width / 3));
      this.px = this.width - margin - this.pw;
      this.py = top;
      this.ph = bottom - top;
      this.gx = margin;
      this.gy = top;
      this.gw = this.px - margin - this.gx;
      this.gh = bottom - top;
      this.cols = Math.max(1, (this.gw - 12 + GAP) / (CW + GAP));
      this.wheelR = Math.min(this.pw / 2 - 26, 62);
      this.wheelCx = this.px + this.pw / 2;
      this.wheelCy = this.py + this.ph - this.wheelR - 34;
   }

   private int cardX(int i) {
      int used = this.cols * (CW + GAP) - GAP;
      return this.gx + (this.gw - used) / 2 + i % this.cols * (CW + GAP);
   }

   private int cardY(int i) {
      return this.gy + 10 + i / this.cols * (CH + GAP) - Math.round(this.scroll);
   }

   private float maxScroll() {
      int rows = (EmoteType.listed().length + this.cols - 1) / this.cols;
      return Math.max(0, rows * (CH + GAP) + 14 - this.gh);
   }

   private EmoteType cardAt(double mx, double my) {
      if (Render2D.inside(mx, my, this.gx, this.gy, this.gw, this.gh)) {
         EmoteType[] all = EmoteType.listed();

         for (int i = 0; i < all.length; i++) {
            if (Render2D.inside(mx, my, this.cardX(i), this.cardY(i), CW, CH)) {
               return all[i];
            }
         }
      }

      return null;
   }

   private float slotX(int i) {
      return this.wheelCx + (float)Math.cos(-Math.PI / 2.0 + i * Math.PI * 2.0 / Emotes.SLOTS) * this.wheelR;
   }

   private float slotY(int i) {
      return this.wheelCy + (float)Math.sin(-Math.PI / 2.0 + i * Math.PI * 2.0 / Emotes.SLOTS) * this.wheelR;
   }

   private int slotAt(double mx, double my) {
      for (int i = 0; i < Emotes.SLOTS; i++) {
         double dx = mx - this.slotX(i);
         double dy = my - this.slotY(i);
         if (dx * dx + dy * dy <= 15.0 * 15.0) {
            return i;
         }
      }

      return -1;
   }

   private int countInWheel(EmoteType t) {
      int n = 0;

      for (EmoteType s : this.slots) {
         if (s == t) {
            n++;
         }
      }

      return n;
   }

   @Override
   public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
      this.layout();
      this.scroll = Math.max(0.0F, Math.min(this.maxScroll(), this.scroll));
      float e = Math.min(1.0F, (System.currentTimeMillis() - this.opened) / 160.0F);
      ctx.fill(0, 0, this.width, this.height, Math.round(150.0F * e) << 24 | 0x05060C);
      this.hoveredCard = this.dragging == null ? this.cardAt(mouseX, mouseY) : null;
      this.hoveredSlot = this.slotAt(mouseX, mouseY);
      // Titel
      Matrix3x2fStack m = ctx.pose();
      m.pushMatrix();
      m.translate(this.gx, 10.0F);
      m.scale(1.4F, 1.4F);
      ctx.drawString(this.font, Component.literal("Emote-Auswahl").withStyle(ChatFormatting.BOLD), 0, 0, -1, true);
      m.popMatrix();
      ctx.drawString(this.font, "Emote wählen · dann Platz im Rad anklicken (oder hinziehen) · Doppelklick spielt ab", this.gx + 120, 14, -7564628, false);
      // Karten
      Render2D.roundRect(ctx, this.gx, this.gy, this.gw, this.gh, 8, PANEL);
      ctx.enableScissor(this.gx, this.gy + 2, this.gx + this.gw, this.gy + this.gh - 2);
      EmoteType[] all = EmoteType.listed();

      for (int i = 0; i < all.length; i++) {
         this.card(ctx, all[i], this.cardX(i), this.cardY(i));
      }

      ctx.disableScissor();
      if (this.maxScroll() > 0.0F) {
         int track = this.gh - 16;
         int bar = Math.max(20, Math.round(track * this.gh / (this.gh + this.maxScroll())));
         int by = this.gy + 8 + Math.round((track - bar) * this.scroll / this.maxScroll());
         Render2D.roundRect(ctx, this.gx + this.gw - 6, by, 3, bar, 1, 0x80FFFFFF);
      }

      // Vorschau und Rad
      Render2D.roundRect(ctx, this.px, this.py, this.pw, this.ph, 8, PANEL);
      EmoteType show = this.dragging != null ? this.dragging : (this.hoveredCard != null ? this.hoveredCard : (this.hoveredSlot >= 0 && this.slots[this.hoveredSlot] != null ? this.slots[this.hoveredSlot] : this.selected));
      EmoteState.preview(show);
      String name = show == null ? "Kein Emote gewählt" : show.label;
      ctx.drawCenteredString(this.font, Component.literal(name).withStyle(ChatFormatting.BOLD), this.px + this.pw / 2, this.py + 8, show == null ? -7564628 : -1);
      if (show != null) {
         ctx.drawCenteredString(this.font, show.loops() ? "Dauer-Emote · läuft bis du dich bewegst" : String.format("%.1f s", show.duration), this.px + this.pw / 2, this.py + 20, -7564628);
      }

      int top = this.py + 32;
      int bottom = this.wheelCy - this.wheelR - 22;
      if (this.minecraft.player != null && bottom - top > 40) {
         int size = Math.max(20, Math.round((bottom - top) * 0.42F));
         InventoryScreen.renderEntityInInventoryFollowsMouse(ctx, this.px + 8, top, this.px + this.pw - 8, bottom, size, 0.0625F, mouseX, mouseY, this.minecraft.player);
      }

      ctx.drawCenteredString(this.font, "Dein Emote-Rad", this.wheelCx, this.wheelCy - this.wheelR - 18, -3885571);
      Render2D.ring(ctx, this.wheelCx, this.wheelCy, this.wheelR + 17, 2, 0x40FFFFFF, 0);
      Render2D.circle(ctx, this.wheelCx, this.wheelCy, 13, 0x60202638);
      ctx.drawCenteredString(this.font, "✦", this.wheelCx, this.wheelCy - 4, -7564628);

      for (int i = 0; i < Emotes.SLOTS; i++) {
         int sx = Math.round(this.slotX(i));
         int sy = Math.round(this.slotY(i));
         boolean hot = i == this.hoveredSlot;
         boolean target = hot && (this.dragging != null || this.selected != null);
         EmoteType t = this.slots[i];
         Render2D.circle(ctx, sx, sy, 15, target ? ACCENT : (hot ? 0xE0303A58 : 0xE01C2234));
         Render2D.ring(ctx, sx, sy, 15, 1, t != null && t == show ? ACCENT : 0x50FFFFFF, 0);
         if (t != null) {
            m.pushMatrix();
            m.translate(sx, sy - 7);
            m.scale(1.4F, 1.4F);
            ctx.drawCenteredString(this.font, t.icon, 0, 0, -1);
            m.popMatrix();
            m.pushMatrix();
            m.translate(sx, sy + 5);
            m.scale(0.55F, 0.55F);
            ctx.drawCenteredString(this.font, t.label, 0, 0, -1578509);
            m.popMatrix();
         } else {
            ctx.drawCenteredString(this.font, "+", sx, sy - 4, -10656900);
         }

         m.pushMatrix();
         m.translate(sx + 12, sy - 15);
         m.scale(0.6F, 0.6F);
         ctx.drawString(this.font, String.valueOf(i + 1), 0, 0, -7564628, false);
         m.popMatrix();
      }

      ctx.drawCenteredString(this.font, "Rechtsklick auf Platz = leeren", this.wheelCx, this.py + this.ph - 14, -10656900);
      // Knöpfe
      this.button(ctx, "Abspielen", this.width / 2 - 150, this.height - 26, 90, this.selected != null, mouseX, mouseY);
      this.button(ctx, "Standard", this.width / 2 - 45, this.height - 26, 90, true, mouseX, mouseY);
      this.button(ctx, "Fertig", this.width / 2 + 60, this.height - 26, 90, true, mouseX, mouseY);
      // gezogene Karte
      if (this.dragging != null && this.dragMoved) {
         this.card(ctx, this.dragging, (int)this.dragX - CW / 2, (int)this.dragY - CH / 2);
      }
   }

   private void card(GuiGraphics ctx, EmoteType t, int x, int y) {
      boolean hot = t == this.hoveredCard;
      boolean sel = t == this.selected;
      if (sel) {
         Render2D.glow(ctx, x, y, CW, CH, 6, ACCENT & 16777215, 0.6F);
      }

      Render2D.roundRect(ctx, x, y, CW, CH, 6, hot ? CARD_HOT : CARD);
      if (sel) {
         Render2D.roundRect(ctx, x, y + CH - 2, CW, 2, 1, ACCENT);
      }

      Matrix3x2fStack m = ctx.pose();
      float bob = hot ? (float)Math.sin(System.currentTimeMillis() / 140.0) * 1.5F : 0.0F;
      m.pushMatrix();
      m.translate(x + CW / 2.0F, y + 7 + bob);
      m.scale(2.0F, 2.0F);
      ctx.drawCenteredString(this.font, t.icon, 0, 0, hot || sel ? -1 : -3885571);
      m.popMatrix();
      String label = t.label;
      int lw = this.font.width(label);
      m.pushMatrix();
      m.translate(x + CW / 2.0F, y + 30);
      if (lw > CW - 6) {
         float s = (CW - 6) / (float)lw;
         m.scale(s, s);
      }

      ctx.drawCenteredString(this.font, label, 0, 0, -1);
      m.popMatrix();
      int in = this.countInWheel(t);
      m.pushMatrix();
      m.translate(x + CW / 2.0F, y + 41);
      m.scale(0.6F, 0.6F);
      ctx.drawCenteredString(this.font, in > 0 ? "im Rad" : (t.loops() ? "Dauer-Emote" : ""), 0, 0, in > 0 ? -11870592 : -7564628);
      m.popMatrix();
   }

   private void button(GuiGraphics ctx, String text, int x, int y, int w, boolean on, int mx, int my) {
      boolean hot = on && Render2D.inside(mx, my, x, y, w, 18);
      Render2D.roundRect(ctx, x, y, w, 18, 5, !on ? 0x60202430 : (hot ? ACCENT : 0xE0262E46));
      ctx.drawCenteredString(this.font, text, x + w / 2, y + 5, on ? -1 : -10656900);
   }

   private boolean buttonHit(double mx, double my, int index) {
      int x = this.width / 2 - 150 + index * 105;
      return Render2D.inside(mx, my, x, this.height - 26, 90, 18);
   }

   private void assign(int slot, EmoteType t) {
      if (slot >= 0 && slot < Emotes.SLOTS) {
         this.slots[slot] = t;
         this.module.setLoadout(this.slots);
      }
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      double mx = click.x();
      double my = click.y();
      int slot = this.slotAt(mx, my);
      if (click.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
         if (slot >= 0) {
            this.assign(slot, null);
         }

         return true;
      } else if (click.button() != InputConstants.MOUSE_BUTTON_LEFT) {
         return super.mouseClicked(click, doubled);
      } else if (this.buttonHit(mx, my, 0)) {
         if (this.selected != null) {
            EmoteType t = this.selected;
            this.onClose();
            this.module.play(t);
         }

         return true;
      } else if (this.buttonHit(mx, my, 1)) {
         this.module.resetLoadout();
         System.arraycopy(this.module.loadout(), 0, this.slots, 0, Emotes.SLOTS);
         return true;
      } else if (this.buttonHit(mx, my, 2)) {
         this.onClose();
         return true;
      } else {
         EmoteType card = this.cardAt(mx, my);
         if (card != null) {
            if (doubled) {
               this.onClose();
               this.module.play(card);
               return true;
            }

            this.selected = card;
            this.dragging = card;
            this.dragFromSlot = -1;
            this.dragMoved = false;
            this.dragX = mx;
            this.dragY = my;
            return true;
         } else if (slot >= 0) {
            if (this.selected != null) {
               this.assign(slot, this.selected);
            } else if (this.slots[slot] != null) {
               // Platz-Inhalt verschieben
               this.dragging = this.slots[slot];
               this.dragFromSlot = slot;
               this.dragMoved = false;
               this.dragX = mx;
               this.dragY = my;
            }

            return true;
         } else {
            this.selected = null;
            return true;
         }
      }
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
      if (this.dragging != null) {
         this.dragX = click.x();
         this.dragY = click.y();
         this.dragMoved = true;
         return true;
      } else {
         return super.mouseDragged(click, dx, dy);
      }
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent click) {
      if (this.dragging != null) {
         if (this.dragMoved) {
            int slot = this.slotAt(click.x(), click.y());
            if (slot >= 0) {
               if (this.dragFromSlot >= 0) {
                  // tauschen
                  EmoteType other = this.slots[slot];
                  this.slots[this.dragFromSlot] = other;
               }

               this.assign(slot, this.dragging);
            } else if (this.dragFromSlot >= 0) {
               this.assign(this.dragFromSlot, null);
            }
         }

         this.dragging = null;
         this.dragFromSlot = -1;
         return true;
      } else {
         return super.mouseReleased(click);
      }
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double h, double v) {
      this.scroll = Math.max(0.0F, Math.min(this.maxScroll(), this.scroll - (float)v * 24.0F));
      return true;
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      int k = input.key();
      if (k >= InputConstants.KEY_1 && k <= InputConstants.KEY_8 && this.selected != null) {
         this.assign(k - InputConstants.KEY_1, this.selected);
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   @Override
   public void onClose() {
      EmoteState.preview(null);
      super.onClose();
   }

   @Override
   public void removed() {
      EmoteState.preview(null);
      super.removed();
   }
}
