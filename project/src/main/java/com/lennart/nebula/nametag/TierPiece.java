package com.lennart.nebula.nametag;

import com.lennart.nebula.tiers.KitIcons;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public record TierPiece(Identifier listIcon, KitIcons.Icon kit, String label, int color, float iconSize) implements NametagPiece {
   private static final float ICON_GAP = 1.5F;

   @Override
   public float width(Font tr) {
      float w = tr.width(this.label);
      if (this.listIcon != null) {
         w += this.iconSize + 1.5F;
      }

      if (this.kit != null) {
         w += this.iconSize + 1.5F;
      }

      return w;
   }

   @Override
   public void draw(Font tr, NameCanvas canvas, float x, float y) {
      float top = y + 3.5F - this.iconSize / 2.0F;
      if (this.listIcon != null) {
         canvas.icon(this.listIcon, x, top, this.iconSize, -1);
         x += this.iconSize + 1.5F;
      }

      if (this.kit != null) {
         canvas.icon(this.kit.texture(), x, top, this.iconSize, -1);
         if (this.kit.overlay() != null) {
            canvas.icon(this.kit.overlay(), x, top, this.iconSize, this.kit.tint());
         }

         x += this.iconSize + 1.5F;
      }

      canvas.text(Component.literal(this.label).getVisualOrderText(), x, y, this.color);
   }
}
