package com.lennart.nebula.nametag;

import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

public interface NameCanvas {
   int backgroundColor();

   void text(FormattedCharSequence var1, float var2, float var3, int var4);

   void vanillaName(FormattedCharSequence var1, float var2, float var3, int var4, boolean var5);

   void icon(Identifier var1, float var2, float var3, float var4, int var5);

   void background(float var1, float var2, float var3, float var4, int var5);

   default void element(Object tag, float x, float y, float w, float h) {
   }

   /** Aktuelle Matrix in der Welt (null in GUI-Vorschauen). */
   default org.joml.Matrix4f pose() {
      return null;
   }
}
