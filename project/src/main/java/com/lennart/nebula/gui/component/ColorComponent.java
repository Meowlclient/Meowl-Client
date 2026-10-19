package com.lennart.nebula.gui.component;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.gui.widget.MeowlTextField;
import com.lennart.nebula.setting.ColorSetting;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Farbauswahl: Farbfeld (Sättigung/Helligkeit), Farbton-Leiste, Deckkraft-Leiste, Palette mit fertigen Farben,
 * zuletzt benutzte Farben und Hex-Eingabe. Vorschau oben rechts.
 */
public class ColorComponent extends SettingComponent<ColorSetting> {
   private static final int HEADER_H = 22;
   private static final int FIELD_H = 64;
   private static final int BAR_H = 8;
   private static final int SWATCH = 12;
   private static final int[] PALETTE = {
      0xFFFFFFFF, 0xFFC0C0C0, 0xFF808080, 0xFF303030, 0xFF000000, 0xFFFF3B3B, 0xFFFF8A1E, 0xFFFFD43B, 0xFF8BE04A, 0xFF2ECC71,
      0xFF1ABC9C, 0xFF3BC9FF, 0xFF3B7BFF, 0xFF8B5CF6, 0xFFD946EF, 0xFFFF6FB5, 0xFF8B5A2B, 0xFFF5DEB3
   };
   /** Zuletzt gewählte Farben (gemeinsam für alle Farbregler). */
   private static final List<Integer> RECENT = new ArrayList<>();
   private boolean open;
   /** 0 = Farbfeld, 1 = Farbton, 2 = Deckkraft. */
   private int dragging = -1;
   private float hue;
   private float sat;
   private float val;
   private int lastArgb = 1;
   private final MeowlTextField hex = new MeowlTextField(9);

   public ColorComponent(ColorSetting setting, Runnable onChange) {
      super(setting, onChange);
      this.hex.onSubmit(s -> {
         Integer parsed = ColorSetting.parseHex(s);
         if (parsed != null) {
            setting.set(parsed);
            remember(setting.get());
            onChange.run();
         }

         this.hex.setText(ColorSetting.toHex(setting.get()));
      });
   }

   @Override
   public int getHeight() {
      if (!this.open) {
         return HEADER_H;
      } else {
         int rows = (PALETTE.length + RECENT.size() + this.perRow() - 1) / this.perRow();
         return HEADER_H + FIELD_H + 6 + BAR_H + 6 + (this.setting.hasAlpha() ? BAR_H + 6 : 0) + rows * (SWATCH + 3) + 4 + 18 + 6;
      }
   }

   private int perRow() {
      return Math.max(6, (this.width - 20 + 3) / (SWATCH + 3));
   }

   private static void remember(int argb) {
      RECENT.remove(Integer.valueOf(argb));
      RECENT.add(0, argb);

      while (RECENT.size() > 6) {
         RECENT.remove(RECENT.size() - 1);
      }
   }

   /** HSV aus der aktuellen Farbe übernehmen (nur wenn sie sich von außen geändert hat). */
   private void syncHsv() {
      int argb = this.setting.get();
      if (argb != this.lastArgb) {
         float[] hsb = Color.RGBtoHSB(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, null);
         if (hsb[1] > 0.01F && hsb[2] > 0.01F) {
            this.hue = hsb[0];
         }

         this.sat = hsb[1];
         this.val = hsb[2];
         this.lastArgb = argb;
      }
   }

   private void applyHsv() {
      int a = this.setting.get() & 0xFF000000;
      int rgb = Color.HSBtoRGB(this.hue, this.sat, this.val) & 16777215;
      this.setting.set(a | rgb);
      this.lastArgb = this.setting.get();
   }

   private int fieldX() {
      return this.x + 10;
   }

   private int fieldW() {
      return this.width - 20;
   }

   private int fieldY() {
      return this.y + HEADER_H;
   }

   private int hueY() {
      return this.fieldY() + FIELD_H + 6;
   }

   private int alphaY() {
      return this.hueY() + BAR_H + 6;
   }

   private int swatchY() {
      return this.setting.hasAlpha() ? this.alphaY() + BAR_H + 6 : this.alphaY();
   }

   @Override
   protected void renderContent(GuiGraphics ctx, Font text, int mouseX, int mouseY, float delta) {
      this.syncHsv();
      int argb = this.setting.get();
      int sx = this.x + this.width - 30;
      int sy = this.y + 5;
      String hexText = this.setting.hasAlpha() ? ColorSetting.toHex(argb) : String.format("#%06X", argb & 16777215);
      int withHex = sx - 12 - text.width(hexText) - (this.x + 8);
      boolean showHex = text.width(this.setting.getDisplayName()) <= withHex;
      this.drawLabel(ctx, text, HEADER_H, showHex ? withHex : sx - 6 - (this.x + 8));
      checker(ctx, sx, sy, 24, 12);
      Render2D.roundRect(ctx, sx, sy, 24, 12, 3, argb);
      if (showHex) {
         ctx.drawString(text, hexText, sx - 6 - text.width(hexText), this.y + 7, -7564628, false);
      }

      if (this.open) {
         int fx = this.fieldX();
         int fy = this.fieldY();
         int fw = this.fieldW();
         // Farbfeld: Spalten von weiß→Farbton, jeweils nach unten zu schwarz
         int cols = Math.max(24, fw / 2);

         for (int i = 0; i < cols; i++) {
            float s = (float)i / (cols - 1);
            int top = 0xFF000000 | Color.HSBtoRGB(this.hue, s, 1.0F) & 16777215;
            int x0 = fx + fw * i / cols;
            int x1 = fx + fw * (i + 1) / cols;
            ctx.fillGradient(x0, fy, x1, fy + FIELD_H, top, 0xFF000000);
         }

         int mx = fx + Math.round(this.sat * (fw - 1));
         int my = fy + Math.round((1.0F - this.val) * (FIELD_H - 1));
         Render2D.ring(ctx, mx, my, 4, 1, 0xFFFFFFFF, 0);
         Render2D.ring(ctx, mx, my, 5, 1, 0xFF000000, 0);
         // Farbton-Leiste
         int hy = this.hueY();

         for (int i = 0; i < fw; i++) {
            ctx.fill(fx + i, hy, fx + i + 1, hy + BAR_H, 0xFF000000 | Color.HSBtoRGB((float)i / fw, 1.0F, 1.0F) & 16777215);
         }

         this.knob(ctx, fx + Math.round(this.hue * (fw - 1)), hy, 0xFF000000 | Color.HSBtoRGB(this.hue, 1.0F, 1.0F) & 16777215);
         // Deckkraft
         if (this.setting.hasAlpha()) {
            int ay = this.alphaY();
            checker(ctx, fx, ay, fw, BAR_H);
            ctx.fillGradient(fx, ay, fx + fw, ay + BAR_H, 0, 0);

            for (int i = 0; i < fw; i += 2) {
               int a = Math.round(255.0F * i / fw);
               ctx.fill(fx + i, ay, fx + i + 2, ay + BAR_H, a << 24 | argb & 16777215);
            }

            this.knob(ctx, fx + Math.round((argb >>> 24) / 255.0F * (fw - 1)), ay, argb | 0xFF000000);
         }

         // Palette + zuletzt benutzt
         int py = this.swatchY();
         int per = this.perRow();
         int i = 0;

         for (int c : this.swatches()) {
            int px = fx + i % per * (SWATCH + 3);
            int pyy = py + i / per * (SWATCH + 3);
            boolean hov = Render2D.inside(mouseX, mouseY, px, pyy, SWATCH, SWATCH);
            boolean recent = i >= PALETTE.length;
            Render2D.roundRect(ctx, px - 1, pyy - 1, SWATCH + 2, SWATCH + 2, 3, hov ? 0xFFFFFFFF : (recent ? 0xFF8B5CF6 : 0x40FFFFFF));
            Render2D.roundRect(ctx, px, pyy, SWATCH, SWATCH, 2, c | 0xFF000000);
            i++;
         }

         int hy2 = py + (i + per - 1) / per * (SWATCH + 3) + 4;
         if (!this.hex.isFocused()) {
            this.hex.setText(ColorSetting.toHex(argb));
         }

         this.hex.setBounds(fx, hy2, 70, 15);
         this.hex.render(ctx, text, false);
         ctx.drawString(text, "Hex (Enter)", fx + 76, hy2 + 4, -10656900, false);
      }
   }

   private List<Integer> swatches() {
      List<Integer> out = new ArrayList<>(PALETTE.length + RECENT.size());

      for (int c : PALETTE) {
         out.add(c);
      }

      out.addAll(RECENT);
      return out;
   }

   private void knob(GuiGraphics ctx, int x, int y, int color) {
      Render2D.roundRect(ctx, x - 3, y - 2, 6, BAR_H + 4, 2, 0xFFFFFFFF);
      Render2D.roundRect(ctx, x - 2, y - 1, 4, BAR_H + 2, 1, color);
   }

   private static void checker(GuiGraphics ctx, int x, int y, int w, int h) {
      for (int i = 0; i < w; i += 4) {
         for (int j = 0; j < h; j += 4) {
            ctx.fill(x + i, y + j, Math.min(x + w, x + i + 4), Math.min(y + h, y + j + 4), (i / 4 + j / 4) % 2 == 0 ? -6511697 : -1710101);
         }
      }
   }

   private int partAt(double mx, double my) {
      int fx = this.fieldX();
      int fw = this.fieldW();
      if (Render2D.inside(mx, my, fx - 2, this.fieldY(), fw + 4, FIELD_H)) {
         return 0;
      } else if (Render2D.inside(mx, my, fx - 2, this.hueY() - 2, fw + 4, BAR_H + 4)) {
         return 1;
      } else {
         return this.setting.hasAlpha() && Render2D.inside(mx, my, fx - 2, this.alphaY() - 2, fw + 4, BAR_H + 4) ? 2 : -1;
      }
   }

   private void drag(int part, double mx, double my) {
      float tx = (float)Math.max(0.0, Math.min(1.0, (mx - this.fieldX()) / (this.fieldW() - 1)));
      switch (part) {
         case 0:
            this.sat = tx;
            this.val = 1.0F - (float)Math.max(0.0, Math.min(1.0, (my - this.fieldY()) / (FIELD_H - 1)));
            this.applyHsv();
            break;
         case 1:
            this.hue = Math.min(0.999F, tx);
            if (this.sat < 0.05F && this.val > 0.95F) {
               this.sat = 1.0F;
            }

            this.applyHsv();
            break;
         case 2:
            int a = Math.round(tx * 255.0F);
            this.setting.set(a << 24 | this.setting.get() & 16777215);
            this.lastArgb = this.setting.get();
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.isHovered(mouseX, mouseY)) {
         if (this.hex.isFocused()) {
            this.hex.setFocused(false);
         }

         return false;
      } else if (mouseY < this.y + HEADER_H) {
         this.open = !this.open;
         return true;
      } else if (this.open && this.hex.mouseClicked(mouseX, mouseY, Minecraft.getInstance().font)) {
         return true;
      } else {
         int part = this.partAt(mouseX, mouseY);
         if (part >= 0 && button == InputConstants.MOUSE_BUTTON_LEFT) {
            this.dragging = part;
            this.drag(part, mouseX, mouseY);
            return true;
         } else {
            int per = this.perRow();
            List<Integer> sw = this.swatches();

            for (int i = 0; i < sw.size(); i++) {
               int px = this.fieldX() + i % per * (SWATCH + 3);
               int py = this.swatchY() + i / per * (SWATCH + 3);
               if (Render2D.inside(mouseX, mouseY, px, py, SWATCH, SWATCH)) {
                  int keepAlpha = this.setting.hasAlpha() ? this.setting.get() & 0xFF000000 : 0xFF000000;
                  this.setting.set(keepAlpha | sw.get(i) & 16777215);
                  remember(this.setting.get());
                  this.onChange.run();
                  return true;
               }
            }

            return true;
         }
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button) {
      if (this.dragging >= 0) {
         this.drag(this.dragging, mouseX, mouseY);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.dragging >= 0) {
         this.dragging = -1;
         remember(this.setting.get());
         this.onChange.run();
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      return this.hex.keyPressed(keyCode, modifiers);
   }

   @Override
   public boolean charTyped(int codepoint) {
      return this.hex.charTyped(codepoint);
   }

   @Override
   public boolean isCapturingInput() {
      return this.hex.isFocused();
   }
}
