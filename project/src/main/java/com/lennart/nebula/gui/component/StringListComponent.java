package com.lennart.nebula.gui.component;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.gui.widget.MeowlTextField;
import com.lennart.nebula.setting.StringListSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class StringListComponent extends SettingComponent<StringListSetting> {
   private static final int HEADER_H = 20;
   private static final int CHIP_H = 14;
   private static final int INPUT_H = 18;
   private final MeowlTextField input;
   private final List<int[]> removeButtons = new ArrayList<>();
   private int[] clearButton = new int[4];
   private int[] addButton = new int[4];
   private boolean invalid;
   private Font tr;

   public StringListComponent(StringListSetting setting, Runnable onChange) {
      super(setting, onChange);
      this.input = new MeowlTextField(64).placeholder(setting.placeholder());
      this.input.onSubmit(t -> this.submit());
      this.input.onChange(t -> this.invalid = false);
   }

   private int chipWidth(String entry) {
      return (this.tr == null ? 60 : this.tr.width(label(entry))) + (isItem(entry) ? 16 : 0) + 18;
   }

   private static boolean isItem(String entry) {
      Identifier id = Identifier.tryParse(entry);
      return id != null && entry.contains(":") && BuiltInRegistries.ITEM.containsKey(id);
   }

   private static String label(String entry) {
      return entry.startsWith("minecraft:") ? entry.substring(10) : entry;
   }

   private int chipRows() {
      int rows = this.setting.get().isEmpty() ? 0 : 1;
      int x = 0;

      for (String e : this.setting.get()) {
         int w = this.chipWidth(e);
         if (x > 0 && x + w > this.width - 16) {
            rows++;
            x = 0;
         }

         x += w + 4;
      }

      return rows;
   }

   @Override
   public int getHeight() {
      return 20 + this.chipRows() * 17 + (this.setting.isEditable() ? 24 : 4);
   }

   private void submit() {
      String value = this.setting.normalize(this.input.getText());
      if (value == null) {
         this.invalid = !this.input.getText().isBlank();
      } else {
         if (!this.setting.contains(value)) {
            this.setting.addEntry(value);
            this.onChange.run();
         }

         this.input.setText("");
      }
   }

   @Override
   protected void renderContent(GuiGraphics ctx, Font text, int mouseX, int mouseY, float delta) {
      this.tr = text;
      this.removeButtons.clear();
      List<String> entries = this.setting.get();
      ctx.drawString(text, this.setting.getName() + " (" + entries.size() + ")", this.x + 8, this.y + 6, -1578509, false);
      if (!entries.isEmpty()) {
         int bw = 40;
         this.clearButton = new int[]{this.x + this.width - bw - 6, this.y + 3, bw, 13};
         boolean hov = Render2D.inside(mouseX, mouseY, this.clearButton[0], this.clearButton[1], bw, 13);
         Render2D.roundRect(ctx, this.clearButton[0], this.clearButton[1], bw, 13, 4, hov ? -12640988 : -14280158);
         ctx.drawString(text, "Leeren", this.clearButton[0] + (bw - text.width("Leeren")) / 2, this.y + 6, -495247, false);
      }

      int cx = this.x + 8;
      int cy = this.y + 20;

      for (int i = 0; i < entries.size(); i++) {
         String e = entries.get(i);
         int w = this.chipWidth(e);
         if (cx > this.x + 8 && cx + w > this.x + this.width - 8) {
            cx = this.x + 8;
            cy += 17;
         }

         Render2D.roundRect(ctx, cx, cy, w, 14, 5, -15789540);
         int tx = cx + 5;
         if (isItem(e)) {
            ctx.pose().pushMatrix();
            ctx.pose().translate(cx + 3, cy + 1);
            ctx.pose().scale(0.75F, 0.75F);
            ctx.renderItem(com.lennart.nebula.util.SafeStack.of(BuiltInRegistries.ITEM.getValue(Identifier.parse(e))), 0, 0);
            ctx.pose().popMatrix();
            tx += 13;
         }

         ctx.drawString(text, label(e), tx, cy + 3, -7564628, false);
         int rx = cx + w - 12;
         boolean hov = Render2D.inside(mouseX, mouseY, rx, cy + 2, 10, 10);
         ctx.drawString(text, "✕", rx + 1, cy + 3, hov ? -495247 : -10656900, false);
         this.removeButtons.add(new int[]{rx, cy + 1, 11, 12, i});
         cx += w + 4;
      }

      if (this.setting.isEditable()) {
         int iy = this.y + 20 + this.chipRows() * 17 + 1;
         this.input.setBounds(this.x + 8, iy, this.width - 16 - 22, 16);
         this.input.render(ctx, text, this.invalid);
         this.addButton = new int[]{this.x + this.width - 8 - 18, iy, 18, 16};
         boolean hov = Render2D.inside(mouseX, mouseY, this.addButton[0], this.addButton[1], 18, 16);
         Render2D.roundRect(ctx, this.addButton[0], this.addButton[1], 18, 16, 4, hov ? -8635667 : -7643914);
         ctx.drawString(text, "+", this.addButton[0] + 6, iy + 4, -1, false);
      }
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.tr == null) {
         return false;
      } else if (this.setting.isEditable() && this.input.mouseClicked(mouseX, mouseY, this.tr)) {
         return true;
      } else if (button != InputConstants.MOUSE_BUTTON_LEFT) {
         return false;
      } else if (this.setting.isEditable() && Render2D.inside(mouseX, mouseY, this.addButton[0], this.addButton[1], this.addButton[2], this.addButton[3])) {
         this.submit();
         return true;
      } else if (!this.setting.get().isEmpty()
         && Render2D.inside(mouseX, mouseY, this.clearButton[0], this.clearButton[1], this.clearButton[2], this.clearButton[3])) {
         this.setting.clear();
         this.onChange.run();
         return true;
      } else {
         for (int[] b : this.removeButtons) {
            if (Render2D.inside(mouseX, mouseY, b[0], b[1], b[2], b[3]) && b[4] < this.setting.get().size()) {
               this.setting.removeEntry(this.setting.get().get(b[4]));
               this.onChange.run();
               return true;
            }
         }

         return false;
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (!this.input.isFocused()) {
         return false;
      } else if (keyCode == InputConstants.KEY_ESCAPE) {
         this.input.setFocused(false);
         return true;
      } else {
         if (!this.input.keyPressed(keyCode, modifiers)) {
         }

         return true;
      }
   }

   @Override
   public boolean charTyped(int codepoint) {
      return this.input.charTyped(codepoint);
   }
}
