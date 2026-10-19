package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.module.mods.ShulkerTooltips;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Shulker-Kiste durchsehen (nur ansehen): oben das Raster, darunter alle Items mit Namen und Anzahl – gleiche Items
 * zusammengefasst, mit dem Mausrad scrollbar. Herausnehmen geht hier nicht.
 */
public class ShulkerViewScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private static final int ROW = 20;
   private final Screen parent;
   private final ItemStack box;
   private final List<ItemStack> slots;
   private final List<ItemStack> summary = new ArrayList<>();
   private float scroll;
   private int px;
   private int py;
   private int pw;
   private int ph;

   public ShulkerViewScreen(Screen parent, ItemStack box) {
      super(box.getHoverName());
      this.parent = parent;
      this.box = box.copy();
      List<ItemStack> items = ShulkerTooltips.contents(box);
      this.slots = items == null ? List.of() : items;
      // gleiche Items (mit gleichen Daten) zusammenzählen
      Map<String, ItemStack> merged = new LinkedHashMap<>();

      for (ItemStack s : this.slots) {
         if (!s.isEmpty()) {
            String key = s.getItem() + "|" + s.getComponentsPatch();
            ItemStack m = merged.get(key);
            if (m == null) {
               merged.put(key, s.copy());
            } else {
               m.setCount(m.getCount() + s.getCount());
            }
         }
      }

      this.summary.addAll(merged.values());
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void renderBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
   }

   private int gridRows() {
      return Math.max(1, (this.slots.size() + 8) / 9);
   }

   private void layout() {
      this.pw = Math.min(260, this.width - 20);
      this.ph = Math.min(this.height - 20, 40 + this.gridRows() * 18 + 12 + Math.max(3, this.summary.size()) * ROW + 26);
      this.px = (this.width - this.pw) / 2;
      this.py = (this.height - this.ph) / 2;
   }

   private int listTop() {
      return this.py + 28 + this.gridRows() * 18 + 12;
   }

   private int listBottom() {
      return this.py + this.ph - 22;
   }

   private float maxScroll() {
      return Math.max(0, this.summary.size() * ROW - (this.listBottom() - this.listTop()));
   }

   @Override
   public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      this.layout();
      ctx.fill(0, 0, this.width, this.height, 0xA005060C);
      Render2D.roundRect(ctx, this.px, this.py, this.pw, this.ph, 8, 0xF0101420);
      ctx.item(this.box, this.px + 8, this.py + 6);
      ctx.text(this.font, Component.literal(this.box.getHoverName().getString()).withStyle(ChatFormatting.BOLD), this.px + 28, this.py + 8, -1, true);
      int total = 0;

      for (ItemStack s : this.summary) {
         total += s.getCount();
      }

      String info = this.summary.size() + " Sorten · " + total + " Items · nur ansehen";
      ctx.text(this.font, info, this.px + 28, this.py + 18, -7564628, false);
      // Raster wie in der Kiste
      int gx = this.px + (this.pw - 162) / 2;
      int gy = this.py + 30;
      ItemStack hovered = null;

      for (int i = 0; i < this.gridRows() * 9; i++) {
         int sx = gx + i % 9 * 18;
         int sy = gy + i / 9 * 18;
         ctx.fill(sx, sy, sx + 18, sy + 18, -13158601);
         ctx.fill(sx + 1, sy + 1, sx + 17, sy + 17, 1619757963);
         ItemStack s = i < this.slots.size() ? this.slots.get(i) : ItemStack.EMPTY;
         if (!s.isEmpty()) {
            ctx.item(s, sx + 1, sy + 1);
            ctx.itemDecorations(this.font, s, sx + 1, sy + 1);
            if (Render2D.inside(mouseX, mouseY, sx, sy, 18, 18)) {
               hovered = s;
               ctx.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x60FFFFFF);
            }
         }
      }

      // Liste mit Namen
      int top = this.listTop();
      int bottom = this.listBottom();
      this.scroll = Math.max(0.0F, Math.min(this.maxScroll(), this.scroll));
      ctx.enableScissor(this.px, top, this.px + this.pw, bottom);

      for (int i = 0; i < this.summary.size(); i++) {
         ItemStack s = this.summary.get(i);
         int y = top + i * ROW - Math.round(this.scroll);
         if (y + ROW < top || y > bottom) {
            continue;
         }

         boolean hot = mouseY >= top && mouseY < bottom && Render2D.inside(mouseX, mouseY, this.px + 8, y, this.pw - 16, ROW - 2);
         Render2D.roundRect(ctx, this.px + 8, y, this.pw - 16, ROW - 2, 4, hot ? 0xC0303A58 : 0x801C2234);
         ctx.item(s, this.px + 10, y + 1);
         String name = trim(s.getHoverName().getString(), this.pw - 80);
         ctx.text(this.font, name, this.px + 30, y + 5, -1, false);
         String count = "×" + s.getCount();
         ctx.text(this.font, count, this.px + this.pw - 12 - this.font.width(count), y + 5, -3885571, false);
         if (hot) {
            hovered = s;
         }
      }

      ctx.disableScissor();
      if (this.maxScroll() > 0.0F) {
         int track = bottom - top;
         int bar = Math.max(16, Math.round(track * track / (track + this.maxScroll())));
         int by = top + Math.round((track - bar) * this.scroll / this.maxScroll());
         Render2D.roundRect(ctx, this.px + this.pw - 5, by, 3, bar, 1, 0x80FFFFFF);
      }

      ctx.centeredText(this.font, "Mausrad: scrollen · Esc / Mittelklick: zurück", this.width / 2, this.py + this.ph - 14, -10656900);
      if (hovered != null) {
         ctx.setTooltipForNextFrame(this.font, hovered, mouseX, mouseY);
      }
   }

   private String trim(String s, int max) {
      return this.font.width(s) <= max ? s : this.font.plainSubstrByWidth(s, max - this.font.width("…")) + "…";
   }

   @Override
   public boolean mouseScrolled(double mx, double my, double h, double v) {
      this.scroll = Math.max(0.0F, Math.min(this.maxScroll(), this.scroll - (float)v * ROW));
      return true;
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (click.button() == InputConstants.MOUSE_BUTTON_MIDDLE) {
         this.onClose();
      }

      // nur ansehen: Klicks bewirken nichts
      return true;
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      if (this.minecraft != null && this.minecraft.options.keyInventory.matches(input)) {
         this.onClose();
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }
}
