package com.lennart.nebula.nametag;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

public record TierTextPiece(String label, int color, boolean separator) implements NametagPiece {
   private static final String SEP = " |";
   private static final int SEP_COLOR = -5592406;

   @Override
   public float width(Font tr) {
      return tr.width(this.label) + (this.separator ? tr.width(" |") : 0);
   }

   @Override
   public void draw(Font tr, NameCanvas canvas, float x, float y) {
      canvas.text(Component.literal(this.label).getVisualOrderText(), x, y, this.color);
      if (this.separator) {
         canvas.text(Component.literal(" |").getVisualOrderText(), x + tr.width(this.label), y, -5592406);
      }
   }
}
