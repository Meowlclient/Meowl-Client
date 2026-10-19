package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.widget.MeowlTextField;
import com.lennart.nebula.tools.BlockPalette;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Block-Palette: Farben eingeben, Länge wählen – zeigt den Verlauf aus passenden Minecraft-Blöcken. */
public class BlockPaletteScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private static final String[][] PRESETS = {
      {"Sonnenuntergang", "FFB347", "E0405A", "3A1A6A"},
      {"Ozean", "E8FFFF", "40C0E0", "0A2A6A"},
      {"Wald", "D8E8A0", "4A8A3A", "2A1A0A"},
      {"Nether", "FFD040", "C02010", "200808"},
      {"Grau", "FFFFFF", "808080", "101010"}
   };
   private static final String[] SAVED = new String[]{"FFB347", "E0405A", "", ""};
   private static int savedLength = 8;
   private static boolean savedNoRepeat = true;
   private static boolean loaded;
   private final Screen parent;
   private final MeowlTextField[] fields = new MeowlTextField[4];
   private final List<int[]> rects = new ArrayList<>();
   private final List<Runnable> clicks = new ArrayList<>();
   private List<BlockPalette.Entry> result = List.of();
   private String key = "";
   private String note = "";
   /** Farbwähler offen für Feld Nr. (−1 = zu). */
   private int picking = -1;
   private float[] hsv = new float[]{0.0F, 1.0F, 1.0F};
   private int pickX;
   private int pickY;
   private int dragPart;
   private static final int[] SWATCHES = {
      0xF9FFFE, 0x9D9D97, 0x474F52, 0x1D1D21, 0xB02E26, 0xF9801D, 0xFED83D, 0x80C71F,
      0x5E7C16, 0x169C9C, 0x3AB3DA, 0x3C44AA, 0x8932B8, 0xC74EBD, 0xF38BAA, 0x835432
   };

   public BlockPaletteScreen(Screen parent) {
      super(Component.literal("Block-Palette"));
      this.parent = parent;
      if (!loaded) {
         // gemerkte Farben vom letzten Mal (auch nach Neustart)
         loaded = true;
         com.google.gson.JsonObject st = ToolState.get("block_palette");

         for (int i = 0; i < 4; i++) {
            SAVED[i] = ToolState.getString(st, "c" + i, SAVED[i]);
         }

         savedLength = Math.max(2, Math.min(48, ToolState.getInt(st, "length", savedLength)));
         savedNoRepeat = ToolState.getBool(st, "noRepeat", savedNoRepeat);
      }

      for (int i = 0; i < 4; i++) {
         this.fields[i] = new MeowlTextField(7).placeholder(i < 2 ? "#Farbe" : "(optional)");
         this.fields[i].setText(SAVED[i]);
      }
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   @Override
   public void removed() {
      com.google.gson.JsonObject st = new com.google.gson.JsonObject();

      for (int i = 0; i < 4; i++) {
         SAVED[i] = this.fields[i].getText();
         st.addProperty("c" + i, SAVED[i]);
      }

      st.addProperty("length", savedLength);
      st.addProperty("noRepeat", savedNoRepeat);
      ToolState.put("block_palette", st);
      super.removed();
   }

   @Override
   public void renderBackground(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
   }

   private static Integer parse(String s) {
      String h = s.trim().replace("#", "");
      if (h.matches("[0-9A-Fa-f]{6}")) {
         return Integer.parseInt(h, 16);
      } else if (h.matches("[0-9A-Fa-f]{3}")) {
         int r = Integer.parseInt(h.substring(0, 1), 16) * 17;
         int g = Integer.parseInt(h.substring(1, 2), 16) * 17;
         int b = Integer.parseInt(h.substring(2, 3), 16) * 17;
         return r << 16 | g << 8 | b;
      } else {
         return null;
      }
   }

   private List<Integer> colors() {
      List<Integer> out = new ArrayList<>();

      for (MeowlTextField f : this.fields) {
         Integer c = parse(f.getText());
         if (c != null) {
            out.add(c);
         }
      }

      return out;
   }

   private void recompute() {
      List<Integer> cols = this.colors();
      String k = cols + "|" + savedLength + "|" + savedNoRepeat;
      if (!k.equals(this.key)) {
         this.key = k;
         this.result = cols.isEmpty() ? List.of() : BlockPalette.gradient(cols, savedLength, savedNoRepeat);
      }
   }

   private void button(GuiGraphicsExtractor ctx, int x, int y, int w, String text, int color, int mouseX, int mouseY, Runnable action) {
      boolean hot = Render2D.inside(mouseX, mouseY, x, y, w, 14);
      Render2D.roundRect(ctx, x, y, w, 14, 4, hot ? 0xE08B5CF6 : color);
      ctx.centeredText(this.font, text, x + w / 2, y + 3, -1);
      this.rects.add(new int[]{x, y, w, 14});
      this.clicks.add(action);
   }

   @Override
   public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
      this.rects.clear();
      this.clicks.clear();
      this.recompute();
      ctx.fill(0, 0, this.width, this.height, 0xB005060C);
      int pw = Math.min(480, this.width - 20);
      int ph = Math.min(290, this.height - 20);
      int px = (this.width - pw) / 2;
      int py = (this.height - ph) / 2;
      Render2D.roundRect(ctx, px, py, pw, ph, 8, 0xF0101420);
      ctx.text(this.font, Component.literal("Block-Palette").withStyle(ChatFormatting.BOLD), px + 10, py + 8, -1, true);
      ctx.text(this.font, "Farbfeld anklicken zum Auswählen – oder Hex eingeben (#FF8800), 2 bis 4 Farben", px + 10, py + 20, -7564628, false);
      // Farbfelder mit Vorschau
      int fy = py + 34;
      int fw = (pw - 20 - 3 * 8) / 4;

      for (int i = 0; i < 4; i++) {
         int fx = px + 10 + i * (fw + 8);
         Integer c = parse(this.fields[i].getText());
         ctx.fill(fx - 1, fy - 1, fx + 15, fy + 15, this.picking == i ? 0xFFFFFFFF : 0x60FFFFFF);
         ctx.fill(fx, fy, fx + 14, fy + 14, c == null ? 0xFF303040 : 0xFF000000 | c);
         if (c == null) {
            ctx.centeredText(this.font, "+", fx + 7, fy + 3, -1);
         }

         int idx = i;
         this.rects.add(new int[]{fx, fy, 14, 14});
         this.clicks.add(() -> this.openPicker(idx, fx, fy + 18));
         this.fields[i].setBounds(fx + 17, fy, fw - 17, 14);
         this.fields[i].render(ctx, this.font, !this.fields[i].getText().isBlank() && c == null);
      }

      // Länge und Optionen
      int oy = fy + 22;
      ctx.text(this.font, "Länge:", px + 10, oy + 3, -3885571, false);
      this.button(ctx, px + 48, oy, 16, "−", 0xE0262E46, mouseX, mouseY, () -> savedLength = Math.max(2, savedLength - 1));
      ctx.centeredText(this.font, savedLength + " Blöcke", px + 98, oy + 3, -1);
      this.button(ctx, px + 132, oy, 16, "+", 0xE0262E46, mouseX, mouseY, () -> savedLength = Math.min(48, savedLength + 1));
      this.button(ctx, px + 156, oy, 120, savedNoRepeat ? "✔ Keine Doppelten" : "Doppelte erlaubt", 0xE0262E46, mouseX, mouseY, () -> savedNoRepeat = !savedNoRepeat);
      this.button(ctx, px + pw - 110, oy, 100, "Liste kopieren", 0xE0306A40, mouseX, mouseY, this::copy);
      // Vorlagen
      int ty = oy + 20;
      int x = px + 10;

      for (String[] p : PRESETS) {
         int bw = this.font.width(p[0]) + 10;
         this.button(ctx, x, ty, bw, p[0], 0xE01C2234, mouseX, mouseY, () -> {
            for (int i = 0; i < 4; i++) {
               this.fields[i].setText(i + 1 < p.length ? p[i + 1] : "");
            }
         });
         x += bw + 4;
      }

      // Farbverlauf als Leiste
      int gy = ty + 22;
      List<Integer> cols = this.colors();
      int gw = pw - 20;
      if (!cols.isEmpty()) {
         for (int i = 0; i < gw; i++) {
            ctx.fill(px + 10 + i, gy, px + 11 + i, gy + 6, 0xFF000000 | BlockPalette.sample(cols, (float)i / (gw - 1)));
         }
      }

      // Ergebnis: Blöcke als Icons
      int iy = gy + 14;
      int cell = 22;
      int perRow = Math.max(1, gw / cell);
      String hover = null;

      for (int i = 0; i < this.result.size(); i++) {
         BlockPalette.Entry e = this.result.get(i);
         int cx = px + 10 + i % perRow * cell;
         int cy = iy + i / perRow * (cell + 6);
         ctx.fill(cx, cy, cx + cell - 2, cy + cell - 2, 0xC01C2234);
         ctx.item(com.lennart.nebula.util.SafeStack.of(e.item()), cx + 2, cy + 2);
         ctx.fill(cx, cy + cell - 1, cx + cell - 2, cy + cell + 2, 0xFF000000 | e.rgb());
         if (Render2D.inside(mouseX, mouseY, cx, cy, cell - 2, cell + 2)) {
            hover = (i + 1) + ". " + com.lennart.nebula.util.SafeStack.of(e.item()).getHoverName().getString();
         }
      }

      if (this.result.isEmpty()) {
         ctx.text(this.font, "Gib mindestens eine gültige Farbe ein", px + 10, iy + 4, -7564628, false);
      }

      ctx.text(this.font, this.note, px + 10, py + ph - 16, -7564628, false);
      if (this.picking >= 0) {
         ctx.nextStratum();
         this.drawPicker(ctx, mouseX, mouseY);
      }

      if (hover != null) {
         ctx.setTooltipForNextFrame(this.font, Component.literal(hover), mouseX, mouseY);
      }
   }

   private void openPicker(int field, int x, int y) {
      if (this.picking == field) {
         this.picking = -1;
         return;
      }

      this.picking = field;
      this.pickX = Math.min(x, this.width - 176);
      this.pickY = y;
      Integer c = parse(this.fields[field].getText());
      int rgb = c == null ? 0xFF4080 : c;
      java.awt.Color.RGBtoHSB(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, this.hsv);
   }

   private void setFromHsv() {
      int rgb = java.awt.Color.HSBtoRGB(this.hsv[0], this.hsv[1], this.hsv[2]) & 16777215;
      this.fields[this.picking].setText(String.format("%06X", rgb));
   }

   /** Farbfeld (Sättigung/Helligkeit), Farbton-Leiste und Minecraft-Farbstoff-Farben. */
   private void drawPicker(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
      int x = this.pickX;
      int y = this.pickY;
      Render2D.roundRect(ctx, x - 4, y - 4, 176, 128, 6, 0xF8181C2A);
      // Sättigung (x) / Helligkeit (y)
      int sw = 120;
      int sh = 80;

      for (int i = 0; i < sw; i += 2) {
         float s = (float)i / (sw - 1);
         ctx.fillGradient(x + i, y, x + i + 2, y + sh, 0xFF000000 | java.awt.Color.HSBtoRGB(this.hsv[0], s, 1.0F), 0xFF000000);
      }

      int cx = x + Math.round(this.hsv[1] * (sw - 1));
      int cy = y + Math.round((1.0F - this.hsv[2]) * (sh - 1));
      ctx.fill(cx - 3, cy - 3, cx + 4, cy - 2, -1);
      ctx.fill(cx - 3, cy + 3, cx + 4, cy + 4, -1);
      ctx.fill(cx - 3, cy - 3, cx - 2, cy + 4, -1);
      ctx.fill(cx + 3, cy - 3, cx + 4, cy + 4, -1);
      // Farbton
      int hx = x + sw + 6;

      for (int i = 0; i < sh; i++) {
         ctx.fill(hx, y + i, hx + 12, y + i + 1, 0xFF000000 | java.awt.Color.HSBtoRGB((float)i / sh, 1.0F, 1.0F));
      }

      int hy = y + Math.round(this.hsv[0] * (sh - 1));
      ctx.fill(hx - 2, hy - 1, hx + 14, hy + 2, -1);
      // aktuelle Farbe
      ctx.fill(hx + 18, y, hx + 32, y + 30, 0xFF000000 | java.awt.Color.HSBtoRGB(this.hsv[0], this.hsv[1], this.hsv[2]));
      // Farbstoff-Farben aus Minecraft
      int ty = y + sh + 6;

      for (int i = 0; i < SWATCHES.length; i++) {
         int sx = x + (i % 8) * 21;
         int sy = ty + (i / 8) * 17;
         boolean hot = Render2D.inside(mouseX, mouseY, sx, sy, 18, 14);
         ctx.fill(sx - 1, sy - 1, sx + 19, sy + 15, hot ? -1 : 0x40FFFFFF);
         ctx.fill(sx, sy, sx + 18, sy + 14, 0xFF000000 | SWATCHES[i]);
      }
   }

   /** Klick/Ziehen im Farbwähler. true = verbraucht. */
   private boolean pickerInput(double mx, double my, boolean drag) {
      int x = this.pickX;
      int y = this.pickY;
      int sw = 120;
      int sh = 80;
      int hx = x + sw + 6;
      if (!drag) {
         this.dragPart = 0;
         if (Render2D.inside(mx, my, x, y, sw, sh)) {
            this.dragPart = 1;
         } else if (Render2D.inside(mx, my, hx - 2, y, 16, sh)) {
            this.dragPart = 2;
         } else {
            int ty = y + sh + 6;

            for (int i = 0; i < SWATCHES.length; i++) {
               if (Render2D.inside(mx, my, x + (i % 8) * 21, ty + (i / 8) * 17, 18, 14)) {
                  int c = SWATCHES[i];
                  java.awt.Color.RGBtoHSB(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, this.hsv);
                  this.setFromHsv();
                  return true;
               }
            }

            return Render2D.inside(mx, my, x - 4, y - 4, 176, 128);
         }
      }

      if (this.dragPart == 1) {
         this.hsv[1] = (float)Math.max(0.0, Math.min(1.0, (mx - x) / (sw - 1)));
         this.hsv[2] = 1.0F - (float)Math.max(0.0, Math.min(1.0, (my - y) / (sh - 1)));
         this.setFromHsv();
         return true;
      } else if (this.dragPart == 2) {
         this.hsv[0] = (float)Math.max(0.0, Math.min(0.999, (my - y) / sh));
         this.setFromHsv();
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
      if (this.picking >= 0 && this.dragPart != 0) {
         return this.pickerInput(click.x(), click.y(), true);
      } else {
         return super.mouseDragged(click, dx, dy);
      }
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent click) {
      this.dragPart = 0;
      return super.mouseReleased(click);
   }

   private void copy() {
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < this.result.size(); i++) {
         sb.append(i > 0 ? ", " : "").append(com.lennart.nebula.util.SafeStack.of(this.result.get(i).item()).getHoverName().getString());
      }

      this.minecraft.keyboardHandler.setClipboard(sb.toString());
      this.note = "Blockliste in die Zwischenablage kopiert";
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (this.picking >= 0) {
         if (this.pickerInput(click.x(), click.y(), false)) {
            return true;
         }

         this.picking = -1;
      }

      for (MeowlTextField f : this.fields) {
         f.setFocused(false);
      }

      for (MeowlTextField f : this.fields) {
         if (f.mouseClicked(click.x(), click.y(), this.font)) {
            return true;
         }
      }

      if (click.button() == InputConstants.MOUSE_BUTTON_LEFT) {
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
      savedLength = Math.max(2, Math.min(48, savedLength + (v > 0 ? 1 : -1)));
      return true;
   }

   @Override
   public boolean charTyped(CharacterEvent input) {
      for (MeowlTextField f : this.fields) {
         if (f.isFocused()) {
            return f.charTyped(input.codepoint());
         }
      }

      return super.charTyped(input);
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      for (int i = 0; i < 4; i++) {
         MeowlTextField f = this.fields[i];
         if (f.isFocused()) {
            if (input.key() == InputConstants.KEY_TAB) {
               f.setFocused(false);
               this.fields[(i + 1) % 4].setFocused(true);
               return true;
            }

            if (input.key() != InputConstants.KEY_ESCAPE && f.keyPressed(input.key(), input.modifiers())) {
               return true;
            }
         }
      }

      return super.keyPressed(input);
   }
}
