package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.widget.MeowlTextField;
import com.lennart.nebula.module.sound.SoundController;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public class SoundListScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private int panelW = 440;
   private static final int HEADER_H = 30;
   private static final int ROW_H = 22;
   private static final int PAD = 12;
   private static final int SLIDER_W = 110;
   private final Screen parent;
   private final SoundController controller;
   private final MeowlTextField search = new MeowlTextField(64).placeholder("Sound suchen, z.B. \"explode\" oder \"hurt\"");
   private final List<Identifier> all = new ArrayList<>();
   private List<Identifier> filtered = new ArrayList<>();
   private boolean onlyChanged;
   private double scroll;
   private Identifier dragging;
   private int panelX;
   private int panelY;
   private int panelH;

   public SoundListScreen(Screen parent, SoundController controller) {
      super(Component.literal("Sounds"));
      this.parent = parent;
      this.controller = controller;

      for (Identifier id : BuiltInRegistries.SOUND_EVENT.keySet()) {
         this.all.add(id);
      }

      this.all.sort((a, b) -> a.toString().compareTo(b.toString()));
      this.search.onChange(s -> this.refilter());
      this.refilter();
   }

   private void refilter() {
      String q = this.search.getText().trim().toLowerCase(Locale.ROOT);
      List<Identifier> out = new ArrayList<>();

      for (Identifier id : this.all) {
         if ((q.isEmpty() || id.toString().contains(q)) && (!this.onlyChanged || this.controller.getOverrides().containsKey(id))) {
            out.add(id);
         }
      }

      this.filtered = out;
      this.scroll = 0.0;
   }

   @Override
   protected void init() {
      this.panelW = Math.min(440, this.width - 12);
      this.panelH = Math.max(220, Math.min(this.height - 20, 340));
      this.panelX = (this.width - this.panelW) / 2;
      this.panelY = (this.height - this.panelH) / 2;
      this.search.setFocused(true);
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   private int listTop() {
      return this.panelY + 30 + 30;
   }

   private int listBottom() {
      return this.panelY + this.panelH - 18;
   }

   private int rowX() {
      return this.panelX + 12;
   }

   private int rowW() {
      return this.panelW - 24;
   }

   private int sliderX() {
      return this.rowX() + this.rowW() - 110 - 78;
   }

   private void clampScroll() {
      int content = this.filtered.size() * 22;
      this.scroll = Math.max(0.0, Math.min((double)Math.max(0, content - (this.listBottom() - this.listTop())), this.scroll));
   }

   @Override
   public void renderBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
      context.fill(0, 0, this.width, this.height, -1728053248);
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
      super.render(context, mouseX, mouseY, deltaTicks);
      Render2D.roundRect(context, this.panelX, this.panelY, this.panelW, this.panelH, 6, -267250656);
      Render2D.roundRect(context, this.panelX, this.panelY, this.panelW, 30, 6, -15197398);
      context.fill(this.panelX, this.panelY + 30 - 6, this.panelX + this.panelW, this.panelY + 30, -15197398);
      context.fill(this.panelX, this.panelY + 30, this.panelX + this.panelW, this.panelY + 30 + 1, -14012603);
      Component title = Component.literal("Sound Controller › Sounds").withStyle(ChatFormatting.BOLD);
      int tw = this.font.width(title);
      Render2D.roundRect(context, this.panelX + (this.panelW - tw) / 2 - 10, this.panelY + 10, 4, 10, 2, -7643914);
      context.text(this.font, title, this.panelX + (this.panelW - tw) / 2, this.panelY + 11, -1578509, true);
      this.search.setBounds(this.rowX(), this.panelY + 30 + 8, this.rowW() - 110, 16);
      this.search.render(context, this.font, false);
      int fx = this.rowX() + this.rowW() - 104;
      boolean fHover = Render2D.inside(mouseX, mouseY, fx, this.panelY + 30 + 8, 104, 16);
      Render2D.roundRect(context, fx, this.panelY + 30 + 8, 104, 16, 3, this.onlyChanged ? 1082875126 : (fHover ? -14407358 : -14933965));
      String fl = "Nur geänderte";
      context.text(
         this.font, fl, fx + (104 - this.font.width(fl)) / 2, this.panelY + 30 + 12, this.onlyChanged ? -3885571 : -7564628, false
      );
      this.clampScroll();
      context.enableScissor(this.panelX, this.listTop(), this.panelX + this.panelW, this.listBottom());
      int first = Math.max(0, (int)(this.scroll / 22.0));
      int last = Math.min(this.filtered.size(), first + (this.listBottom() - this.listTop()) / 22 + 2);

      for (int i = first; i < last; i++) {
         this.renderRow(context, this.filtered.get(i), this.listTop() + i * 22 - (int)this.scroll, mouseX, mouseY);
      }

      context.disableScissor();
      if (this.filtered.isEmpty()) {
         context.text(this.font, "Keine Sounds gefunden", this.rowX(), this.listTop() + 4, -7564628, false);
      }

      String info = this.filtered.size() + " Sounds  ·  ▶ = Probehören  ·  ESC: zurück";
      context.text(this.font, info, this.panelX + 10, this.panelY + this.panelH - 13, -10656900, false);
   }

   private void renderRow(GuiGraphicsExtractor ctx, Identifier id, int y, int mouseX, int mouseY) {
      float factor = this.controller.getOverride(id);
      boolean changed = Math.abs(factor - 1.0F) > 0.005F;
      boolean hover = Render2D.inside(mouseX, mouseY, this.rowX(), y, this.rowW(), 20);
      Render2D.roundRect(ctx, this.rowX(), y, this.rowW(), 20, 3, hover ? -14407358 : -14933965);
      String name = id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
      ctx.text(
         this.font,
         this.font.plainSubstrByWidth(name, this.sliderX() - this.rowX() - 30),
         this.rowX() + 22,
         y + 6,
         changed ? -3885571 : -1578509,
         false
      );
      boolean playHover = Render2D.inside(mouseX, mouseY, this.rowX() + 4, y + 3, 14, 14);
      Render2D.roundRect(ctx, this.rowX() + 4, y + 3, 14, 14, 3, playHover ? -7643914 : -14012603);
      ctx.text(this.font, "▶", this.rowX() + 7, y + 6, -1578509, false);
      int sx = this.sliderX();
      int sy = y + 8;
      Render2D.roundRect(ctx, sx, sy, 110, 4, 2, -13288878);
      int filled = Math.round(110.0F * factor / 2.0F);
      Render2D.roundRect(ctx, sx, sy, Math.max(4, filled), 4, 2, factor <= 0.0F ? -1096636 : -7643914);
      ctx.fill(sx + 55, sy - 2, sx + 55 + 1, sy + 6, -10656900);
      Render2D.roundRect(ctx, Math.max(sx - 1, Math.min(sx + filled - 4, sx + 110 - 7)), sy - 3, 8, 10, 3, -1);
      String pct = Math.round(factor * 100.0F) + "%";
      ctx.text(this.font, pct, sx + 110 + 6, y + 6, factor <= 0.0F ? -1096636 : -7564628, false);
      int bx = this.rowX() + this.rowW() - 40;
      boolean bHover = Render2D.inside(mouseX, mouseY, bx, y + 3, 36, 14);
      Render2D.roundRect(ctx, bx, y + 3, 36, 14, 3, bHover ? -13288878 : -14012603);
      String b = changed ? "Reset" : "Stumm";
      ctx.text(this.font, b, bx + (36 - this.font.width(b)) / 2, y + 6, -7564628, false);
   }

   private Identifier rowAt(double my) {
      if (!(my < this.listTop()) && !(my >= this.listBottom())) {
         int i = (int)((my - this.listTop() + this.scroll) / 22.0);
         return i >= 0 && i < this.filtered.size() ? this.filtered.get(i) : null;
      } else {
         return null;
      }
   }

   private int rowTop(Identifier id) {
      return this.listTop() + this.filtered.indexOf(id) * 22 - (int)this.scroll;
   }

   private void setFromMouse(Identifier id, double mx) {
      double t = Math.max(0.0, Math.min(1.0, (mx - this.sliderX()) / 110.0));
      float factor = (float)Math.round(t * 40.0) * 0.05F;
      this.controller.setOverride(id, factor, false);
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      double mx = click.x();
      double my = click.y();
      if (this.search.mouseClicked(mx, my, this.font)) {
         return true;
      } else {
         int fx = this.rowX() + this.rowW() - 104;
         if (Render2D.inside(mx, my, fx, this.panelY + 30 + 8, 104, 16)) {
            this.onlyChanged = !this.onlyChanged;
            this.refilter();
            return true;
         } else {
            Identifier id = this.rowAt(my);
            if (id != null && click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
               int y = this.rowTop(id);
               if (Render2D.inside(mx, my, this.rowX() + 4, y + 3, 14, 14)) {
                  SoundEvent event = BuiltInRegistries.SOUND_EVENT.getValue(id);
                  if (event != null) {
                     this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0F, 1.0F));
                  }

                  return true;
               } else {
                  int bx = this.rowX() + this.rowW() - 40;
                  if (Render2D.inside(mx, my, bx, y + 3, 36, 14)) {
                     boolean changed = Math.abs(this.controller.getOverride(id) - 1.0F) > 0.005F;
                     this.controller.setOverride(id, changed ? 1.0F : 0.0F);
                     return true;
                  } else if (Render2D.inside(mx, my, this.sliderX() - 4, y, 118, 22)) {
                     this.dragging = id;
                     this.setFromMouse(id, mx);
                     return true;
                  } else {
                     return true;
                  }
               }
            } else {
               return super.mouseClicked(click, doubled);
            }
         }
      }
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
      if (this.dragging != null) {
         this.setFromMouse(this.dragging, click.x());
         return true;
      } else {
         return super.mouseDragged(click, offsetX, offsetY);
      }
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent click) {
      if (this.dragging != null) {
         this.controller.setOverride(this.dragging, this.controller.getOverride(this.dragging), true);
      }

      this.dragging = null;
      return super.mouseReleased(click);
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      this.scroll -= verticalAmount * 22.0 * 2.0;
      this.clampScroll();
      return true;
   }

   @Override
   public boolean charTyped(CharacterEvent input) {
      return this.search.charTyped(input.codepoint()) || super.charTyped(input);
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      return this.search.isFocused() && input.key() != InputConstants.KEY_ESCAPE ? this.search.keyPressed(input.key(), input.modifiers()) : super.keyPressed(input);
   }
}
