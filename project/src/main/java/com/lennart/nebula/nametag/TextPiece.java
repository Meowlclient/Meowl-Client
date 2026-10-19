package com.lennart.nebula.nametag;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public record TextPiece(Component text, int color) implements NametagPiece {
   public static TextPiece of(String s, int color) {
      return new TextPiece(Component.literal(s), color);
   }

   @Override
   public float width(Font tr) {
      return tr.width(this.text);
   }

   @Override
   public void draw(Font tr, NameCanvas canvas, float x, float y) {
      canvas.text(this.text.getVisualOrderText(), x, y, this.color);
   }
}
