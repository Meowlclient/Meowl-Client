package com.lennart.nebula.gui.component;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.setting.HueSetting;
import java.awt.Color;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Farbton-Regler: Regenbogen-Leiste, darunter die Farben des Stils mit der aktuellen Verschiebung. Rechtsklick = Original. */
public class HueComponent extends SettingComponent<HueSetting> {
   private boolean dragging;

   public HueComponent(HueSetting setting, Runnable onChange) {
      super(setting, onChange);
   }

   @Override
   public int getHeight() {
      return 52;
   }

   private int trackX() {
      return this.x + 12;
   }

   private int trackW() {
      return this.width - 24;
   }

   private static int shift(int argb, float deg) {
      float[] hsb = Color.RGBtoHSB(argb >> 16 & 0xFF, argb >> 8 & 0xFF, argb & 0xFF, null);
      float h = hsb[0] + deg / 360.0F;
      h -= (float)Math.floor(h);
      return 0xFF000000 | Color.HSBtoRGB(h, hsb[1], hsb[2]) & 16777215;
   }

   @Override
   protected void renderContent(GuiGraphics ctx, Font text, int mouseX, int mouseY, float delta) {
      double v = this.setting.get();
      String value = v == 0.0 ? "Original" : (v > 0.0 ? "+" : "") + Math.round(v) + "°";
      int pillW = text.width(value) + 10;
      this.drawLabel(ctx, text, 20, this.width - 20 - pillW - 6);
      Render2D.pill(ctx, text, value, this.x + this.width - 10, this.y + 4, this.dragging ? 1082875126 : -14670534, -3885571);
      int tx = this.trackX();
      int tw = this.trackW();
      int ty = this.y + 21;
      // Leiste: Farbton von -180° bis +180° relativ zum Original (Mitte = Original)
      for (int i = 0; i < tw; i++) {
         float deg = -180.0F + 360.0F * i / tw;
         ctx.fill(tx + i, ty, tx + i + 1, ty + 7, shift(0xFFFF3030, deg + 0.0F));
      }

      int mid = tx + tw / 2;
      ctx.fill(mid, ty - 2, mid + 1, ty + 9, 0xA0FFFFFF);
      int kx = tx + (int)Math.round((v + 180.0) / 360.0 * tw);
      Render2D.roundRect(ctx, kx - 3, ty - 3, 6, 13, 2, 0xFFFFFFFF);
      Render2D.roundRect(ctx, kx - 2, ty - 2, 4, 11, 1, shift(0xFFFF3030, (float)v));
      // Vorschau: Farben des Stils, verschoben
      int[] colors = this.setting.previewColors();
      int py = this.y + 35;
      int sw = Math.min(16, colors.length == 0 ? 16 : (tw - 70) / Math.max(1, colors.length) - 3);
      ctx.drawString(text, "Vorschau", tx, py + 3, -10656900, false);

      for (int i = 0; i < colors.length; i++) {
         int px = tx + 48 + i * (sw + 3);
         Render2D.roundRect(ctx, px, py, sw, 12, 3, shift(colors[i], (float)v));
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.isHovered(mouseX, mouseY)) {
         return false;
      } else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
         this.setting.reset();
         this.onChange.run();
         return true;
      } else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
         this.dragging = true;
         this.update(mouseX);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button) {
      if (this.dragging) {
         this.update(mouseX);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.dragging) {
         this.dragging = false;
         this.onChange.run();
         return true;
      } else {
         return false;
      }
   }

   private void update(double mouseX) {
      double t = Math.max(0.0, Math.min(1.0, (mouseX - this.trackX()) / this.trackW()));
      double deg = -180.0 + t * 360.0;
      // in der Mitte einrasten
      if (Math.abs(deg) < 6.0) {
         deg = 0.0;
      }

      this.setting.set(deg);
   }
}
