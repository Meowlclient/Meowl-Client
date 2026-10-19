package com.lennart.nebula.nametag;

import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;

/** Meowl-Zeichen neben dem Namen – oder das eigene Profilbild des Spielers (gleiches Format, 16 x 16). */
public record MeowlPiece(float size, Identifier picture) implements NametagPiece {
   public MeowlPiece(float size) {
      this(size, null);
   }

   @Override
   public float width(Font tr) {
      return this.size;
   }

   @Override
   public void draw(Font tr, NameCanvas canvas, float x, float y) {
      canvas.icon(this.picture != null ? this.picture : NametagIcons.meowl(), x, y + 3.5F - this.size / 2.0F, this.size, -1);
   }
}