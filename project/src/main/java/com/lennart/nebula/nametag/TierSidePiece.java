package com.lennart.nebula.nametag;

import com.lennart.nebula.tiers.KitIcons;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * Tier neben dem Namen im Stil „[Icon] LT4 | Name | LT5 [Icon]“: das Kit-Icon steht außen,
 * der graue Trennstrich zeigt zum Namen.
 */
public record TierSidePiece(KitIcons.Icon kit, String label, int color, float iconSize, boolean leftOfName) implements NametagPiece {
   private static final int SEP_COLOR = -5592406;
   private static final float ICON_GAP = 2.0F;

   private float iconW() {
      return this.kit == null ? 0.0F : this.iconSize + ICON_GAP;
   }

   @Override
   public float width(Font tr) {
      return this.iconW() + tr.width(this.label) + tr.width(this.leftOfName ? " |" : "| ");
   }

   @Override
   public void draw(Font tr, NameCanvas canvas, float x, float y) {
      float top = y + 3.5F - this.iconSize / 2.0F;
      if (this.leftOfName) {
         // [Icon] LT4 |
         this.icon(canvas, x, top);
         x += this.iconW();
         canvas.text(Component.literal(this.label).getVisualOrderText(), x, y, this.color);
         canvas.text(Component.literal(" |").getVisualOrderText(), x + tr.width(this.label), y, SEP_COLOR);
      } else {
         // | LT5 [Icon]
         canvas.text(Component.literal("| ").getVisualOrderText(), x, y, SEP_COLOR);
         x += tr.width("| ");
         canvas.text(Component.literal(this.label).getVisualOrderText(), x, y, this.color);
         this.icon(canvas, x + tr.width(this.label) + ICON_GAP, top);
      }
   }

   private void icon(NameCanvas canvas, float x, float top) {
      if (this.kit != null) {
         canvas.icon(this.kit.texture(), x, top, this.iconSize, -1);
         if (this.kit.overlay() != null) {
            canvas.icon(this.kit.overlay(), x, top, this.iconSize, this.kit.tint());
         }
      }
   }
}
