package com.lennart.nebula.gui.component;

import com.lennart.nebula.gui.Animation;
import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.setting.Setting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public abstract class SettingComponent<S extends Setting<?>> {
   protected static final int PAD = 10;
   protected final S setting;
   protected final Runnable onChange;
   protected int x;
   protected int y;
   protected int width;
   private final Animation hover = new Animation(14.0F, 0.0F);

   protected SettingComponent(S setting, Runnable onChange) {
      this.setting = setting;
      this.onChange = onChange;
   }

   public S getSetting() {
      return this.setting;
   }

   public void setBounds(int x, int y, int width) {
      this.x = x;
      this.y = y;
      this.width = width;
   }

   public abstract int getHeight();

   public final void render(GuiGraphics ctx, Font text, int mouseX, int mouseY, float delta) {
      float h = this.hover.update(this.isHovered(mouseX, mouseY) ? 1.0F : 0.0F);
      if (h > 0.01F) {
         Render2D.roundRect(ctx, this.x + 3, this.y + 1, this.width - 6, this.getHeight() - 2, 5, Render2D.alpha(251658239, h));
      }

      this.renderContent(ctx, text, mouseX, mouseY, delta);
   }

   protected abstract void renderContent(GuiGraphics var1, Font var2, int var3, int var4, float var5);

   public boolean isHovered(double mouseX, double mouseY) {
      return Render2D.inside(mouseX, mouseY, this.x, this.y, this.width, this.getHeight());
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      return false;
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      return false;
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button) {
      return false;
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      return false;
   }

   public boolean charTyped(int codepoint) {
      return false;
   }

   public boolean isCapturingInput() {
      return false;
   }

   protected int labelColor() {
      return -1578509;
   }

   protected void drawLabel(GuiGraphics ctx, Font text, int rowHeight) {
      this.drawLabel(ctx, text, rowHeight, this.width - 20);
   }

   protected void drawLabel(GuiGraphics ctx, Font text, int rowHeight, int maxWidth) {
      ctx.drawString(text, trim(text, this.setting.getDisplayName(), maxWidth), this.x + 10, this.y + (rowHeight - 8) / 2, this.labelColor(), false);
   }

   protected static String trim(Font text, String s, int maxWidth) {
      if (maxWidth <= 0) {
         return "";
      } else {
         return text.width(s) <= maxWidth ? s : text.plainSubstrByWidth(s, Math.max(0, maxWidth - text.width("…"))) + "…";
      }
   }
}
