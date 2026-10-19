package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.widget.MeowlTextField;
import com.lennart.nebula.module.mods.Waypoints;
import com.lennart.nebula.util.MarkerRender;
import com.lennart.nebula.waypoints.Waypoint;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class WaypointsScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private static final int ROW_H = 22;
   private final Screen parent;
   private final Waypoints module;
   private final MeowlTextField newName = new MeowlTextField(32).placeholder("Name für neuen Waypoint …");
   private final MeowlTextField rename = new MeowlTextField(32);
   private Waypoint renaming;
   private int scroll;
   private int px;
   private int py;
   private int pw;
   private int ph;

   public WaypointsScreen(Screen parent, Waypoints module) {
      super(Component.literal("Waypoints"));
      this.parent = parent;
      this.module = module;
      this.newName.onSubmit(s -> this.add());
      this.rename.onSubmit(s -> this.finishRename());
   }

   private void add() {
      Waypoint w = this.module.addHere(this.newName.getText());
      if (w != null) {
         this.newName.setText("");
         this.scroll = Math.max(0, this.module.list().size() * 22 - this.listHeight());
      }
   }

   private void finishRename() {
      if (this.renaming != null && !this.rename.getText().isBlank()) {
         this.renaming.name = this.rename.getText().trim();
         this.module.save();
      }

      this.renaming = null;
      this.rename.setFocused(false);
   }

   private int listHeight() {
      return this.ph - 74;
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void onClose() {
      this.finishRename();
      this.module.save();
      this.minecraft.gui.setScreen(this.parent);
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
      context.fill(0, 0, this.width, this.height, -1610612736);
      this.pw = Math.min(380, this.width - 20);
      this.ph = Math.min(300, this.height - 20);
      this.px = (this.width - this.pw) / 2;
      this.py = (this.height - this.ph) / 2;
      Render2D.glow(context, this.px, this.py, this.pw, this.ph, 8, -7643914, 0.35F);
      Render2D.roundRect(context, this.px, this.py, this.pw, this.ph, 8, -15855591);
      Render2D.roundRect(context, this.px, this.py, this.pw, 26, 8, -15065296);
      context.text(this.font, Component.literal("⚑ Waypoints").withStyle(ChatFormatting.BOLD), this.px + 10, this.py + 9, -1578509, false);
      String dim = this.module.dimension().replace("minecraft:", "");
      int dw = this.font.width(dim);
      context.text(this.font, dim, this.px + this.pw - dw - 10, this.py + 9, -10656900, false);
      int fy = this.py + 32;
      this.newName.setBounds(this.px + 10, fy, this.pw - 100, 18);
      this.newName.render(context, this.font, false);
      boolean addHov = Render2D.inside(mouseX, mouseY, this.px + this.pw - 84, fy, 74, 18);
      Render2D.roundRect(context, this.px + this.pw - 84, fy, 74, 18, 6, addHov ? -7575312 : -7643914);
      context.text(this.font, "+ Hier", this.px + this.pw - 66, fy + 5, -1, false);
      List<Waypoint> list = this.module.list();
      int top = this.py + 58;
      int lh = this.listHeight();
      this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, list.size() * 22 - lh)));
      context.enableScissor(this.px, top, this.px + this.pw, top + lh);
      if (list.isEmpty()) {
         context.text(this.font, "Noch keine Waypoints in dieser Welt.", this.px + 12, top + 8, -7564628, false);
      }

      String curDim = this.module.dimension();

      for (int i = 0; i < list.size(); i++) {
         Waypoint w = list.get(i);
         int ry = top + i * 22 - this.scroll;
         if (ry + 22 >= top && ry <= top + lh) {
            boolean hov = Render2D.inside(mouseX, mouseY, this.px + 6, ry, this.pw - 12, 20) && mouseY >= top && mouseY <= top + lh;
            Render2D.roundRect(context, this.px + 6, ry, this.pw - 12, 20, 5, hov ? -14407358 : -15394521);
            int x = this.px + 12;
            Render2D.toggleSwitch(context, x, ry + 3, w.visible ? 1.0F : 0.0F);
            x += 32;
            Render2D.roundRect(context, x, ry + 4, 12, 12, 3, 0xFF000000 | w.color);
            x += 18;
            Render2D.roundRect(context, x, ry + 3, 16, 14, 3, -14012603);
            context.text(this.font, w.symbol, x + 8 - this.font.width(w.symbol) / 2, ry + 6, 0xFF000000 | w.color, false);
            x += 22;
            int nameW = this.pw - (x - this.px) - 96;
            if (w == this.renaming) {
               this.rename.setBounds(x, ry + 2, nameW, 16);
               this.rename.render(context, this.font, false);
            } else {
               String n = this.font.plainSubstrByWidth(w.name, nameW);
               context.text(this.font, n, x, ry + 6, w.dim.equals(curDim) ? -1578509 : -10656900, false);
            }

            String info = !w.dim.equals(curDim)
               ? w.dim.replace("minecraft:", "").replace("the_", "")
               : (this.minecraft.player != null ? MarkerRender.distance(Math.sqrt(this.minecraft.player.distanceToSqr(w.x, w.y, w.z))) : "");
            int iw = this.font.width(info);
            context.text(this.font, info, this.px + this.pw - 44 - iw, ry + 6, -7564628, false);
            boolean delHov = Render2D.inside(mouseX, mouseY, this.px + this.pw - 34, ry + 3, 22, 14);
            Render2D.roundRect(context, this.px + this.pw - 34, ry + 3, 22, 14, 4, delHov ? -8446691 : -12968155);
            context.text(this.font, "✕", this.px + this.pw - 26, ry + 6, -495247, false);
         }
      }

      context.disableScissor();
      context.text(
         this.font, "Klick: Schalter = sichtbar · Farbe/Symbol = wechseln · Name = umbenennen", this.px + 10, this.py + this.ph - 12, -10656900, false
      );
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      double mx = click.x();
      double my = click.y();
      if (this.renaming != null && !this.rename.isHovered(mx, my)) {
         this.finishRename();
      }

      if (!this.rename.mouseClicked(mx, my, this.font) && !this.newName.mouseClicked(mx, my, this.font)) {
         if (Render2D.inside(mx, my, this.px + this.pw - 84, this.py + 32, 74, 18)) {
            this.add();
            return true;
         } else {
            List<Waypoint> list = this.module.list();
            int top = this.py + 58;
            int lh = this.listHeight();
            if (my >= top && my <= top + lh) {
               int i = (int)((my - top + this.scroll) / 22.0);
               if (i >= 0 && i < list.size()) {
                  Waypoint w = list.get(i);
                  int ry = top + i * 22 - this.scroll;
                  int x = this.px + 12;
                  if (Render2D.inside(mx, my, x, ry, 28, 22)) {
                     w.visible = !w.visible;
                  } else if (Render2D.inside(mx, my, x + 32, ry, 16, 22)) {
                     w.nextColor();
                  } else if (Render2D.inside(mx, my, x + 50, ry, 20, 22)) {
                     w.nextSymbol();
                  } else if (Render2D.inside(mx, my, this.px + this.pw - 34, ry + 3, 22, 14)) {
                     list.remove(i);
                  } else if (mx >= x + 72 && mx < this.px + this.pw - 96) {
                     this.renaming = w;
                     this.rename.setText(w.name);
                     this.rename.setFocused(true);
                  }

                  this.module.save();
                  return true;
               }
            }

            return super.mouseClicked(click, doubled);
         }
      } else {
         return true;
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      this.scroll -= (int)(verticalAmount * 22.0);
      return true;
   }

   @Override
   public boolean charTyped(CharacterEvent input) {
      return this.rename.isFocused() ? this.rename.charTyped(input.codepoint()) : this.newName.charTyped(input.codepoint()) || super.charTyped(input);
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      if (this.rename.isFocused()) {
         if (input.key() == InputConstants.KEY_ESCAPE) {
            this.renaming = null;
            this.rename.setFocused(false);
            return true;
         } else {
            return this.rename.keyPressed(input.key(), input.modifiers());
         }
      } else {
         return this.newName.isFocused() && input.key() != InputConstants.KEY_ESCAPE && this.newName.keyPressed(input.key(), input.modifiers()) ? true : super.keyPressed(input);
      }
   }
}
