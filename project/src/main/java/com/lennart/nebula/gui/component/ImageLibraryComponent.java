package com.lennart.nebula.gui.component;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.Render2D;
import com.lennart.nebula.gui.Theme;
import com.lennart.nebula.gui.widget.MeowlTextField;
import com.lennart.nebula.images.ImageProviders;
import com.lennart.nebula.setting.ImageLibrarySetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public class ImageLibraryComponent extends SettingComponent<ImageLibrarySetting> {
   private static final int HEADER_H = 22;
   private static final int SLOT_H = 46;
   private static final int SLOT_GAP = 3;
   private final List<MeowlTextField> fields = new ArrayList<>();
   private final List<int[]> buttons = new ArrayList<>();
   private Font tr;
   private static final int A_SELECT = 0;
   private static final int A_W_MINUS = 1;
   private static final int A_W_PLUS = 2;
   private static final int A_H_MINUS = 3;
   private static final int A_H_PLUS = 4;
   private static final int A_VISIBLE = 5;
   private static final int A_UP = 6;
   private static final int A_DOWN = 7;
   private static final int A_DELETE = 8;
   private static final int A_ADD = 9;

   public ImageLibraryComponent(ImageLibrarySetting setting, Runnable onChange) {
      super(setting, onChange);
   }

   private List<ImageLibrarySetting.Slot> slots() {
      return this.setting.get().slots();
   }

   @Override
   public int getHeight() {
      int n = this.slots().size();
      return 22 + n * 49 + (n == 0 ? 18 : 2);
   }

   private void syncFields() {
      while (this.fields.size() < this.slots().size()) {
         int index = this.fields.size();
         MeowlTextField f = new MeowlTextField(512).placeholder("https://i.imgur.com/…png");
         f.setText(this.slots().get(index).url());
         f.onChange(text -> {
            int i = this.fields.indexOf(f);
            if (i >= 0 && i < this.slots().size()) {
               this.setting.replace(i, this.slots().get(i).withUrl(text));
            }
         });
         f.onSubmit(text -> {
            f.setFocused(false);
            this.onChange.run();
         });
         this.fields.add(f);
      }

      while (this.fields.size() > this.slots().size()) {
         this.fields.remove(this.fields.size() - 1);
      }
   }

   private void reloadFields() {
      this.fields.clear();
      this.syncFields();
   }

   @Override
   protected void renderContent(GuiGraphicsExtractor ctx, Font text, int mouseX, int mouseY, float delta) {
      this.tr = text;
      this.syncFields();
      this.buttons.clear();
      List<ImageLibrarySetting.Slot> list = this.slots();
      ctx.text(text, this.setting.getName() + " (" + list.size() + "/64)", this.x + 8, this.y + 7, -1578509, false);
      this.button(ctx, mouseX, mouseY, this.x + this.width - 52, this.y + 4, 46, 14, "+ Bild", -1, 9, -11788907);
      if (list.isEmpty()) {
         this.small(
            ctx,
            text.plainSubstrByWidth("Noch keine Bilder – „+ Bild“ drücken und Link einfügen.", Math.round((this.width - 16) / 0.75F)),
            this.x + 8,
            this.y + 22 + 2,
            -10656900
         );
      }

      int sy = this.y + 22;

      for (int i = 0; i < list.size(); i++) {
         ImageLibrarySetting.Slot s = list.get(i);
         boolean sel = i == this.setting.get().selected();
         Render2D.roundRect(ctx, this.x + 4, sy, this.width - 8, 46, 4, sel ? -14409923 : -15394778);
         int cx = this.x + 13;
         int cy = sy + 10;
         boolean hov = Render2D.inside(mouseX, mouseY, cx - 6, cy - 6, 12, 12);
         Render2D.circle(ctx, cx, cy, 5, sel ? -7643914 : (hov ? -11841432 : -14012344));
         if (sel) {
            Render2D.circle(ctx, cx, cy, 2, -1);
         }

         this.buttons.add(new int[]{cx - 6, cy - 6, 12, 12, i, 0});
         boolean valid = s.url().isEmpty() || ImageProviders.isValid(s.url());
         MeowlTextField f = this.fields.get(i);
         f.setBounds(this.x + 22, sy + 3, this.width - 22 - 46, 14);
         f.render(ctx, text, !valid);
         int px = this.x + this.width - 40;
         int py = sy + 3;
         int pw = 32;
         int ph = 20;
         Render2D.roundRect(ctx, px, py, pw, ph, 2, -15987178);
         StringBuilder status = new StringBuilder();
         int[] size = new int[2];
         Identifier tex = valid && !s.url().isEmpty() && this.setting.preview() != null ? this.setting.preview().texture(s.url(), status, size) : null;
         if (tex != null && size[0] > 0 && size[1] > 0) {
            float k = Math.min((float)pw / size[0], (float)ph / size[1]);
            int dw = Math.max(1, Math.round(size[0] * k));
            int dh = Math.max(1, Math.round(size[1] * k));
            ctx.blit(RenderPipelines.GUI_TEXTURED, tex, px + (pw - dw) / 2, py + (ph - dh) / 2, 0.0F, 0.0F, dw, dh, dw, dh);
         }

         if (!s.visible()) {
            ctx.fill(px, py, px + pw, py + ph, -1610612736);
         }

         int ry = sy + 21;
         this.small(ctx, "B", this.x + 22, ry + 2, -7564628);
         this.button(ctx, mouseX, mouseY, this.x + 29, ry, 10, 10, "−", i, 1, -14933965);
         this.small(ctx, String.valueOf(s.w()), this.x + 42, ry + 2, -1578509);
         this.button(ctx, mouseX, mouseY, this.x + 52, ry, 10, 10, "+", i, 2, -14933965);
         this.small(ctx, "H", this.x + 68, ry + 2, -7564628);
         this.button(ctx, mouseX, mouseY, this.x + 75, ry, 10, 10, "−", i, 3, -14933965);
         this.small(ctx, String.valueOf(s.h()), this.x + 88, ry + 2, -1578509);
         this.button(ctx, mouseX, mouseY, this.x + 98, ry, 10, 10, "+", i, 4, -14933965);
         int bx = this.x + this.width - 46 - 4 - 52;
         this.button(ctx, mouseX, mouseY, bx, ry, 11, 10, s.visible() ? "◉" : "○", i, 5, s.visible() ? -14796193 : -14933965);
         this.button(ctx, mouseX, mouseY, bx + 13, ry, 11, 10, "▲", i, 6, -14933965);
         this.button(ctx, mouseX, mouseY, bx + 26, ry, 11, 10, "▼", i, 7, -14933965);
         this.button(ctx, mouseX, mouseY, bx + 39, ry, 11, 10, "✕", i, 8, -12640988);
         String info = !valid
            ? "Ungültiger Link (nur https-Bildlinks, z.B. Imgur)"
            : (s.url().isEmpty() ? "Link einfügen …" : (status.isEmpty() ? "" : status.toString()));
         if (!s.visible()) {
            info = "ausgeblendet · " + info;
         }

         this.small(ctx, text.plainSubstrByWidth(info, Math.round((this.width - 30) / 0.75F)), this.x + 22, sy + 35, valid ? -10656900 : -495247);
         sy += 49;
      }
   }

   private void button(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int bx, int by, int bw, int bh, String label, int slot, int action, int bg) {
      boolean hov = Render2D.inside(mouseX, mouseY, bx, by, bw, bh);
      Render2D.roundRect(ctx, bx, by, bw, bh, 3, hov ? Theme.lerp(bg, -1, 0.12F) : bg);
      int tw = Math.round(this.tr.width(label) * (bh < 12 ? 0.75F : 1.0F));
      if (bh < 12) {
         this.small(ctx, label, bx + (bw - tw) / 2 + 1, by + 2, -1578509);
      } else {
         ctx.text(this.tr, label, bx + (bw - tw) / 2, by + (bh - 8) / 2, -1, false);
      }

      this.buttons.add(new int[]{bx, by, bw, bh, slot, action});
   }

   private void small(GuiGraphicsExtractor ctx, String s, int sx, int sy, int color) {
      ctx.pose().pushMatrix();
      ctx.pose().translate(sx, sy);
      ctx.pose().scale(0.75F, 0.75F);
      ctx.text(this.tr, s, 0, 0, color, false);
      ctx.pose().popMatrix();
   }

   @Override
   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.tr == null) {
         return false;
      } else {
         boolean hitField = false;

         for (MeowlTextField f : this.fields) {
            boolean was = f.isFocused();
            if (f.mouseClicked(mouseX, mouseY, this.tr)) {
               hitField = true;
            } else if (was) {
               this.onChange.run();
            }
         }

         if (hitField) {
            return true;
         } else if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
         } else {
            for (int[] b : List.copyOf(this.buttons)) {
               if (Render2D.inside(mouseX, mouseY, b[0], b[1], b[2], b[3])) {
                  int i = b[4];
                  ImageLibrarySetting.Slot s = i >= 0 && i < this.slots().size() ? this.slots().get(i) : null;
                  switch (b[5]) {
                     case 0:
                        this.setting.select(i);
                        break;
                     case 1:
                        this.setting.replace(i, s.withSize(s.w() - 1, s.h()));
                        break;
                     case 2:
                        this.setting.replace(i, s.withSize(s.w() + 1, s.h()));
                        break;
                     case 3:
                        this.setting.replace(i, s.withSize(s.w(), s.h() - 1));
                        break;
                     case 4:
                        this.setting.replace(i, s.withSize(s.w(), s.h() + 1));
                        break;
                     case 5:
                        this.setting.replace(i, s.withVisible(!s.visible()));
                        break;
                     case 6:
                        this.setting.move(i, -1);
                        this.reloadFields();
                        break;
                     case 7:
                        this.setting.move(i, 1);
                        this.reloadFields();
                        break;
                     case 8:
                        this.setting.remove(i);
                        this.reloadFields();
                        break;
                     case 9:
                        ImageLibrarySetting.Slot last = this.slots().isEmpty() ? null : this.slots().get(this.slots().size() - 1);
                        this.setting.add(new ImageLibrarySetting.Slot("", last != null ? last.w() : 2, last != null ? last.h() : 2, true));
                        this.syncFields();
                        this.fields.get(this.fields.size() - 1).setFocused(true);
                        break;
                     default:
                        return false;
                  }

                  this.onChange.run();
                  return true;
               }
            }

            return false;
         }
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      for (MeowlTextField f : this.fields) {
         if (f.isFocused()) {
            if (keyCode != InputConstants.KEY_ESCAPE && keyCode != InputConstants.KEY_TAB) {
               if (!f.keyPressed(keyCode, modifiers)) {
               }

               return true;
            }

            f.setFocused(false);
            this.onChange.run();
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean charTyped(int codepoint) {
      for (MeowlTextField f : this.fields) {
         if (f.charTyped(codepoint)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean isCapturingInput() {
      return false;
   }
}
