package com.lennart.nebula.gui.widget;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.Render2D;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class MeowlTextField {
   private String text = "";
   private int cursor;
   private boolean allSelected;
   private boolean focused;
   private int scroll;
   private final int maxLength;
   private String placeholder = "";
   private Consumer<String> onChange = s -> {};
   private Consumer<String> onSubmit = s -> {};
   private int x;
   private int y;
   private int w;
   private int h;

   public MeowlTextField(int maxLength) {
      this.maxLength = maxLength;
   }

   public MeowlTextField placeholder(String placeholder) {
      this.placeholder = placeholder;
      return this;
   }

   public MeowlTextField onChange(Consumer<String> onChange) {
      this.onChange = onChange;
      return this;
   }

   public MeowlTextField onSubmit(Consumer<String> onSubmit) {
      this.onSubmit = onSubmit;
      return this;
   }

   public String getText() {
      return this.text;
   }

   public void setText(String value) {
      this.text = value == null ? "" : value;
      if (this.text.length() > this.maxLength) {
         this.text = this.text.substring(0, this.maxLength);
      }

      this.cursor = this.text.length();
      this.allSelected = false;
      this.scroll = 0;
   }

   public boolean isFocused() {
      return this.focused;
   }

   public void setFocused(boolean focused) {
      this.focused = focused;
      if (!focused) {
         this.allSelected = false;
      }
   }

   public void setBounds(int x, int y, int w, int h) {
      this.x = x;
      this.y = y;
      this.w = w;
      this.h = h;
   }

   public boolean isHovered(double mx, double my) {
      return Render2D.inside(mx, my, this.x, this.y, this.w, this.h);
   }

   public void render(GuiGraphics ctx, Font tr, boolean invalid) {
      int border = invalid ? -1096636 : (this.focused ? -7643914 : -14012603);
      Render2D.roundRect(ctx, this.x, this.y, this.w, this.h, 3, border);
      Render2D.roundRect(ctx, this.x + 1, this.y + 1, this.w - 2, this.h - 2, 3, -15460570);
      int innerW = this.w - 8;
      int ty = this.y + (this.h - 9) / 2 + 1;
      if (this.text.isEmpty() && !this.focused) {
         ctx.drawString(tr, tr.plainSubstrByWidth(this.placeholder, innerW), this.x + 4, ty, -10656900, false);
      } else {
         this.scroll = Math.min(this.scroll, this.cursor);

         while (this.scroll < this.cursor && tr.width(this.text.substring(this.scroll, this.cursor)) > innerW - 2) {
            this.scroll++;
         }

         String visible = tr.plainSubstrByWidth(this.text.substring(this.scroll), innerW);
         if (this.allSelected && !this.text.isEmpty()) {
            ctx.fill(this.x + 4, ty - 1, this.x + 4 + tr.width(visible), ty + 9, 1082875126);
         }

         ctx.drawString(tr, visible, this.x + 4, ty, -1578509, false);
         if (this.focused && System.currentTimeMillis() / 500L % 2L == 0L) {
            int cx = this.x + 4 + tr.width(this.text.substring(this.scroll, Math.max(this.scroll, this.cursor)));
            ctx.fill(cx, ty - 1, cx + 1, ty + 9 - 1, -3885571);
         }
      }
   }

   public boolean mouseClicked(double mx, double my, Font tr) {
      boolean hit = this.isHovered(mx, my);
      this.setFocused(hit);
      if (hit) {
         int rel = (int)mx - (this.x + 4);
         int pos = this.scroll;

         while (pos < this.text.length() && tr.width(this.text.substring(this.scroll, pos + 1)) <= rel) {
            pos++;
         }

         this.cursor = pos;
      }

      return hit;
   }

   public boolean charTyped(int codepoint) {
      if (this.focused && !Character.isISOControl(codepoint) && codepoint != 167) {
         this.insert(new String(Character.toChars(codepoint)));
         return true;
      } else {
         return false;
      }
   }

   public boolean keyPressed(int key, int modifiers) {
      if (!this.focused) {
         return false;
      } else {
         boolean ctrl = com.lennart.nebula.compat.Keys.ctrl(modifiers);
         Minecraft mc = Minecraft.getInstance();
         switch (key) {
            case InputConstants.KEY_A:
               if (ctrl) {
                  this.allSelected = true;
                  this.cursor = this.text.length();
               }
               break;
            case InputConstants.KEY_C:
               if (ctrl) {
                  mc.keyboardHandler.setClipboard(this.text);
               }
               break;
            case InputConstants.KEY_V:
               if (ctrl) {
                  this.insert(mc.keyboardHandler.getClipboard().replaceAll("[\\r\\n\\t]", " "));
               }
               break;
            case InputConstants.KEY_X:
               if (ctrl) {
                  mc.keyboardHandler.setClipboard(this.text);
                  this.replaceAll("");
               }
               break;
            case InputConstants.KEY_ESCAPE:
               this.setFocused(false);
               break;
            case InputConstants.KEY_RETURN:
            case InputConstants.KEY_NUMPADENTER:
               this.onSubmit.accept(this.text);
               this.setFocused(false);
               break;
            case InputConstants.KEY_BACKSPACE:
               if (this.allSelected) {
                  this.replaceAll("");
               } else if (this.cursor > 0) {
                  this.text = this.text.substring(0, this.cursor - 1) + this.text.substring(this.cursor);
                  this.cursor--;
                  this.onChange.accept(this.text);
               }
               break;
            case InputConstants.KEY_DELETE:
               if (this.allSelected) {
                  this.replaceAll("");
               } else if (this.cursor < this.text.length()) {
                  this.text = this.text.substring(0, this.cursor) + this.text.substring(this.cursor + 1);
                  this.onChange.accept(this.text);
               }
               break;
            case InputConstants.KEY_RIGHT:
               this.allSelected = false;
               this.cursor = Math.min(this.text.length(), this.cursor + 1);
               break;
            case InputConstants.KEY_LEFT:
               this.allSelected = false;
               this.cursor = Math.max(0, this.cursor - 1);
               break;
            case InputConstants.KEY_HOME:
               this.allSelected = false;
               this.cursor = 0;
               break;
            case InputConstants.KEY_END:
               this.allSelected = false;
               this.cursor = this.text.length();
         }

         return true;
      }
   }

   private void insert(String s) {
      if (this.allSelected) {
         this.text = "";
         this.cursor = 0;
         this.allSelected = false;
      }

      int room = this.maxLength - this.text.length();
      if (room > 0) {
         if (s.length() > room) {
            s = s.substring(0, room);
         }

         this.text = this.text.substring(0, this.cursor) + s + this.text.substring(this.cursor);
         this.cursor = this.cursor + s.length();
         this.onChange.accept(this.text);
      }
   }

   private void replaceAll(String value) {
      this.text = value;
      this.cursor = value.length();
      this.allSelected = false;
      this.scroll = 0;
      this.onChange.accept(this.text);
   }
}
