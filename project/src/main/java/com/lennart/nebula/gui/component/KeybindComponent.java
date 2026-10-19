package com.lennart.nebula.gui.component;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.util.KeybindConflicts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class KeybindComponent extends SettingComponent<KeybindSetting> {
   private static final int HEIGHT = 22;
   private static final int BUTTON_W = 96;
   private boolean listening;

   public KeybindComponent(KeybindSetting setting, Runnable onChange) {
      super(setting, onChange);
   }

   @Override
   public int getHeight() {
      return 22;
   }

   private int buttonX() {
      return this.x + this.width - 96 - 10;
   }

   @Override
   protected void renderContent(GuiGraphics ctx, Font text, int mouseX, int mouseY, float delta) {
      this.drawLabel(ctx, text, 22);
      int bx = this.buttonX();
      int by = this.y + 4;
      boolean hovered = Render2D.inside(mouseX, mouseY, bx, by, 96, 14);
      int bg = this.listening ? 1082875126 : (hovered ? -14012344 : -14670534);
      Render2D.roundRect(ctx, bx, by, 96, 14, 3, bg);
      String label = this.listening ? "Taste drücken…" : this.setting.get().getDisplayName();
      boolean conflict = !this.listening && !KeybindConflicts.find(this.setting.get(), this.setting).isEmpty();
      if (conflict) {
         label = "⚠ " + label;
      }

      label = text.plainSubstrByWidth(label, 88);
      int color = !this.listening && !conflict ? (this.setting.get().isNone() ? -7564628 : -3885571) : -278748;
      ctx.drawString(text, label, bx + (96 - text.width(label)) / 2, by + 3, color, false);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.listening) {
         if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            this.setting.set(Bind.mouse(button));
            this.onChange.run();
         }

         this.listening = false;
         return true;
      } else if (button == InputConstants.MOUSE_BUTTON_LEFT && Render2D.inside(mouseX, mouseY, this.buttonX(), this.y + 4, 96, 14)) {
         this.listening = true;
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (!this.listening) {
         return false;
      } else {
         switch (keyCode) {
            case InputConstants.KEY_ESCAPE:
               break;
            case InputConstants.KEY_BACKSPACE:
            case InputConstants.KEY_DELETE:
               this.setting.set(Bind.NONE);
               this.onChange.run();
               break;
            default:
               this.setting.set(Bind.keyboard(keyCode));
               this.onChange.run();
         }

         this.listening = false;
         return true;
      }
   }

   @Override
   public boolean isCapturingInput() {
      return this.listening;
   }
}
