package com.lennart.nebula.gui.component;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.setting.ActionSetting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class ActionComponent extends SettingComponent<ActionSetting> {
   private static final int HEIGHT = 22;
   private static final int BUTTON_W = 96;

   public ActionComponent(ActionSetting setting, Runnable onChange) {
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
      boolean hovered = Render2D.inside(mouseX, mouseY, this.buttonX(), this.y + 4, 96, 14);
      Render2D.roundRect(ctx, this.buttonX(), this.y + 4, 96, 14, 3, hovered ? -7643914 : 1082875126);
      String label = text.plainSubstrByWidth(this.setting.getButtonLabel(), 88);
      ctx.drawString(text, label, this.buttonX() + (96 - text.width(label)) / 2, this.y + 7, -1578509, false);
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == InputConstants.MOUSE_BUTTON_LEFT && Render2D.inside(mouseX, mouseY, this.buttonX(), this.y + 4, 96, 14)) {
         this.setting.run();
         return true;
      } else {
         return false;
      }
   }
}
