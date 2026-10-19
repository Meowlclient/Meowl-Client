package com.lennart.nebula.gui;

import com.lennart.nebula.emote.EmoteState;
import com.lennart.nebula.emote.EmoteType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Emote-Vorschau im Menü: dein Charakter spielt alle Emotes nacheinander ab; ‹ › blättert, Klick auf den Namen hält an. */
public final class EmotePreview implements ModulePreview {
   private final CosmeticPreview figure = new CosmeticPreview(() -> {});
   private int index;
   private long since = System.currentTimeMillis();
   private boolean hold;
   private int bx;
   private int by;
   private int bw;

   private EmoteType current() {
      EmoteType[] all = EmoteType.listed();
      return all[Math.floorMod(this.index, all.length)];
   }

   private void step(int d) {
      this.index += d;
      this.since = System.currentTimeMillis();
   }

   @Override
   public void render(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int mouseX, int mouseY, float delta) {
      EmoteType t = this.current();
      float shown = (System.currentTimeMillis() - this.since) / 1000.0F;
      if (!this.hold && shown > (t.loops() ? 4.0F : t.duration + 0.6F)) {
         this.step(1);
         t = this.current();
      }

      EmoteState.preview(t);
      this.figure.render(ctx, x, y, w, h - 22, mouseX, mouseY, delta);
      // Leiste unten: ‹ Name ›
      this.bx = x;
      this.by = y + h - 20;
      this.bw = w;
      Render2D.roundRect(ctx, x, this.by, w, 20, 6, 0xC0101420);
      var font = Minecraft.getInstance().font;
      ctx.centeredText(font, "‹", x + 10, this.by + 6, Render2D.inside(mouseX, mouseY, x, this.by, 20, 20) ? 0xFFB57BFF : -1);
      ctx.centeredText(font, "›", x + w - 10, this.by + 6, Render2D.inside(mouseX, mouseY, x + w - 20, this.by, 20, 20) ? 0xFFB57BFF : -1);
      ctx.centeredText(font, t.icon + " " + t.label + (this.hold ? " ⏸" : ""), x + w / 2, this.by + 6, -1);
   }

   @Override
   public boolean mouseClicked(double mx, double my, int button) {
      if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && Render2D.inside(mx, my, this.bx, this.by, this.bw, 20)) {
         if (mx < this.bx + 20) {
            this.step(-1);
         } else if (mx > this.bx + this.bw - 20) {
            this.step(1);
         } else {
            this.hold = !this.hold;
         }

         return true;
      } else {
         return this.figure.mouseClicked(mx, my, button);
      }
   }
}