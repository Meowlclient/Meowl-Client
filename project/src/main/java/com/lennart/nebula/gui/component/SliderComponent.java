package com.lennart.nebula.gui.component;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.Animation;
import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.setting.NumberSetting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class SliderComponent extends SettingComponent<NumberSetting> {
   private static final int HEIGHT = 34;
   private final Animation fill = new Animation(18.0F, this.ratio());
   private final Animation grab = new Animation(16.0F, 0.0F);
   private boolean dragging;

   public SliderComponent(NumberSetting setting, Runnable onChange) {
      super(setting, onChange);
   }

   @Override
   public int getHeight() {
      return 34;
   }

   private float ratio() {
      double range = this.setting.getMax() - this.setting.getMin();
      return range <= 0.0 ? 0.0F : (float)((this.setting.get() - this.setting.getMin()) / range);
   }

   private int trackX() {
      return this.x + 10 + 2;
   }

   private int trackW() {
      return this.width - 20 - 4;
   }

   @Override
   protected void renderContent(GuiGraphicsExtractor ctx, Font text, int mouseX, int mouseY, float delta) {
      String value = this.setting.format();
      int pillW = text.width(value) + 10;
      this.drawLabel(ctx, text, 20, this.width - 20 - pillW - 6);
      boolean active = this.dragging || this.isHovered(mouseX, mouseY);
      Render2D.pill(ctx, text, value, this.x + this.width - 10, this.y + 4, active ? 1082875126 : -14670534, -3885571);
      int tx = this.trackX();
      int tw = this.trackW();
      int ty = this.y + 23;
      float r = this.fill.update(this.ratio());
      int filled = Math.round(tw * r);
      Render2D.roundRect(ctx, tx, ty, tw, 4, 2, -14012603);
      if (filled > 0) {
         Render2D.roundRect(ctx, tx, ty, Math.max(4, filled), 4, 2, -7643914);
      }

      float g = this.grab.update(active ? 1.0F : 0.0F);
      int knob = 5 + Math.round(g);
      int kx = Math.max(tx, Math.min(tx + tw, tx + filled));
      Render2D.circle(ctx, kx, ty + 2, knob + 1, -7643914);
      Render2D.circle(ctx, kx, ty + 2, knob - 1, -1);
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
         this.updateValue(mouseX);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean mouseDragged(double mouseX, double mouseY, int button) {
      if (this.dragging) {
         this.updateValue(mouseX);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.dragging && button == InputConstants.MOUSE_BUTTON_LEFT) {
         this.dragging = false;
         this.onChange.run();
         return true;
      } else {
         return false;
      }
   }

   private void updateValue(double mouseX) {
      double t = Math.max(0.0, Math.min(1.0, (mouseX - this.trackX()) / this.trackW()));
      this.setting.set(this.setting.getMin() + t * (this.setting.getMax() - this.setting.getMin()));
   }
}
