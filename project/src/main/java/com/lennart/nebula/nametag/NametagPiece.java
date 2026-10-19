package com.lennart.nebula.nametag;

import net.minecraft.client.gui.Font;

public interface NametagPiece {
   float width(Font var1);

   void draw(Font var1, NameCanvas var2, float var3, float var4);
}
