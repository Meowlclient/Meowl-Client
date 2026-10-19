package com.lennart.nebula.gui.component;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.Animation;
import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.gui.Theme;
import com.lennart.nebula.setting.BooleanSetting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class ToggleComponent extends SettingComponent<BooleanSetting> {
   private static final int HEIGHT = 24;
   private final Animation progress;

   public ToggleComponent(BooleanSetting setting, Runnable onChange) {
      super(setting, onChange);
      this.progress = new Animation(16.0F, setting.get() ? 1.0F : 0.0F);
   }

   @Override
   public int getHeight() {
      return 24;
   }

   @Override
   protected int labelColor() {
      return this.setting.get() ? -1578509 : Theme.lerp(-1578509, -7564628, 0.45F);
   }

   @Override
   protected void renderContent(GuiGraphics ctx, Font text, int mouseX, int mouseY, float delta) {
      float p = this.progress.update(this.setting.get() ? 1.0F : 0.0F);
      String state = this.setting.get() ? "AN" : "AUS";
      int stateW = text.width("AUS");
      int switchX = this.x + this.width - 10 - 26;
      this.drawLabel(ctx, text, 24, switchX - 6 - stateW - 6 - (this.x + 10));
      ctx.drawString(text, state, switchX - 6 - text.width(state), this.y + 8, Theme.lerp(-10656900, -3885571, p), false);
      Render2D.toggleSwitch(ctx, switchX, this.y + 5, p);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == InputConstants.MOUSE_BUTTON_LEFT && this.isHovered(mouseX, mouseY)) {
         this.setting.toggle();
         this.onChange.run();
         return true;
      } else {
         return false;
      }
   }
}
