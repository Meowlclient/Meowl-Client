package com.lennart.nebula.gui.component;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.setting.ModeSetting;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ModeComponent extends SettingComponent<ModeSetting> {
   private static final int HEADER_H = 24;
   private static final int ENTRY_H = 16;
   private static final int SEG_H = 16;
   private boolean open;

   public ModeComponent(ModeSetting setting, Runnable onChange) {
      super(setting, onChange);
   }

   private boolean segmented() {
      List<String> modes = this.setting.getModes();
      if (modes.size() <= 4 && this.width > 0) {
         Font tr = Minecraft.getInstance().font;
         int cell = (this.width - 20) / modes.size();

         for (String m : modes) {
            if (tr.width(m) + 8 > cell) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   @Override
   public int getHeight() {
      return this.segmented() ? 42 : 24 + (this.open ? this.setting.getModes().size() * 16 + 6 : 0);
   }

   private int buttonW() {
      return Math.min(130, Math.max(80, (this.width - 20) / 2));
   }

   private int buttonX() {
      return this.x + this.width - 10 - this.buttonW();
   }

   @Override
   protected void renderContent(GuiGraphicsExtractor ctx, Font text, int mouseX, int mouseY, float delta) {
      List<String> modes = this.setting.getModes();
      if (this.segmented()) {
         this.drawLabel(ctx, text, 20);
         int sx = this.x + 10;
         int sw = this.width - 20;
         int sy = this.y + 20;
         Render2D.roundRect(ctx, sx, sy, sw, 16, 5, -15065554);
         int cell = sw / modes.size();

         for (int i = 0; i < modes.size(); i++) {
            int cx = sx + i * cell;
            int cw = i == modes.size() - 1 ? sw - i * cell : cell;
            boolean sel = this.setting.is(modes.get(i));
            boolean hov = Render2D.inside(mouseX, mouseY, cx, sy, cw, 16);
            if (sel) {
               Render2D.roundRect(ctx, cx + 1, sy + 1, cw - 2, 14, 4, -7643914);
            } else if (hov) {
               Render2D.roundRect(ctx, cx + 1, sy + 1, cw - 2, 14, 4, -14275776);
            }

            String m = modes.get(i);
            ctx.text(text, m, cx + (cw - text.width(m)) / 2, sy + 4, sel ? -1 : -7564628, false);
         }
      } else {
         int bw = this.buttonW();
         int bx = this.buttonX();
         int by = this.y + 5;
         this.drawLabel(ctx, text, 24, bx - 6 - (this.x + 10));
         boolean hovered = Render2D.inside(mouseX, mouseY, bx, by, bw, 14);
         Render2D.roundRect(ctx, bx, by, bw, 14, 4, this.open ? 1082875126 : (hovered ? -14012344 : -14670534));
         ctx.text(text, trim(text, this.setting.get(), bw - 18), bx + 6, by + 3, -3885571, false);
         ctx.text(text, this.open ? "▴" : "▾", bx + bw - 10, by + 3, -7564628, false);
         if (this.open) {
            int lx = this.x + 10;
            int lw = this.width - 20;
            int ly = this.y + 24;
            Render2D.roundRect(ctx, lx, ly, lw, modes.size() * 16 + 2, 5, -15065554);

            for (int i = 0; i < modes.size(); i++) {
               int ey = ly + 1 + i * 16;
               boolean entryHovered = Render2D.inside(mouseX, mouseY, lx, ey, lw, 16);
               boolean selected = this.setting.is(modes.get(i));
               if (selected || entryHovered) {
                  Render2D.roundRect(ctx, lx + 2, ey, lw - 4, 16, 4, selected ? 1082875126 : -14275776);
               }

               ctx.text(text, trim(text, modes.get(i), lw - 26), lx + 8, ey + 4, selected ? -3885571 : -1578509, false);
               if (selected) {
                  ctx.text(text, "✔", lx + lw - 14, ey + 4, -3885571, false);
               }
            }
         }
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      List<String> modes = this.setting.getModes();
      if (this.segmented()) {
         int sx = this.x + 10;
         int sw = this.width - 20;
         int cell = sw / modes.size();
         if (button == InputConstants.MOUSE_BUTTON_LEFT && Render2D.inside(mouseX, mouseY, sx, this.y + 20, sw, 16)) {
            int i = Math.min(modes.size() - 1, (int)((mouseX - sx) / cell));
            this.setting.set(modes.get(i));
            this.onChange.run();
            return true;
         } else {
            return false;
         }
      } else if (Render2D.inside(mouseX, mouseY, this.x, this.y, this.width, 24)) {
         if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            this.setting.set(modes.get((this.setting.getIndex() + 1) % modes.size()));
            this.onChange.run();
         } else if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            this.open = !this.open;
         }

         return true;
      } else {
         if (this.open && button == InputConstants.MOUSE_BUTTON_LEFT) {
            int lx = this.x + 10;
            int lw = this.width - 20;

            for (int i = 0; i < modes.size(); i++) {
               if (Render2D.inside(mouseX, mouseY, lx, this.y + 24 + 1 + i * 16, lw, 16)) {
                  this.setting.set(modes.get(i));
                  this.onChange.run();
                  this.open = false;
                  return true;
               }
            }
         }

         return false;
      }
   }
}
