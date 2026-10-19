package com.lennart.nebula.gui.component;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.widget.MeowlTextField;
import com.lennart.nebula.setting.TextSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class TextComponent extends SettingComponent<TextSetting> {
   private static final int HEIGHT = 36;
   private final MeowlTextField field;

   public TextComponent(TextSetting setting, Runnable onChange) {
      super(setting, onChange);
      this.field = new MeowlTextField(setting.getMaxLength()).onSubmit(s -> this.commit());
      this.field.setText(setting.get());
   }

   private void commit() {
      if (!this.field.getText().equals(this.setting.get())) {
         this.setting.set(this.field.getText());
         this.onChange.run();
      }
   }

   @Override
   public int getHeight() {
      return 36;
   }

   @Override
   protected void renderContent(GuiGraphics ctx, Font text, int mouseX, int mouseY, float delta) {
      ctx.drawString(text, this.setting.getName(), this.x + 8, this.y + 5, -1578509, false);
      if (!this.field.isFocused() && !this.field.getText().equals(this.setting.get())) {
         this.field.setText(this.setting.get());
      }

      this.field.setBounds(this.x + 8, this.y + 16, this.width - 16, 15);
      this.field.render(ctx, text, !this.setting.isValid(this.field.getText()));
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      boolean wasFocused = this.field.isFocused();
      boolean hit = this.field.mouseClicked(mouseX, mouseY, Minecraft.getInstance().font);
      if (wasFocused && !hit) {
         this.commit();
      }

      return hit;
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      boolean wasFocused = this.field.isFocused();
      boolean used = this.field.keyPressed(keyCode, modifiers);
      if (wasFocused && !this.field.isFocused() && keyCode != InputConstants.KEY_ESCAPE) {
         this.commit();
      } else if (wasFocused && !this.field.isFocused()) {
         this.field.setText(this.setting.get());
      }

      return used;
   }

   @Override
   public boolean charTyped(int codepoint) {
      return this.field.charTyped(codepoint);
   }

   @Override
   public boolean isCapturingInput() {
      return this.field.isFocused();
   }
}
