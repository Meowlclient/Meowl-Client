package com.lennart.nebula.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

@FunctionalInterface
public interface ModulePreview {
   void render(GuiGraphicsExtractor var1, int var2, int var3, int var4, int var5, int var6, int var7, float var8);

   default int preferredWidth() {
      return 170;
   }

   /** Klick in die Vorschau (Koordinaten wie beim Zeichnen). true = verbraucht. */
   default boolean mouseClicked(double mouseX, double mouseY, int button) {
      return false;
   }
}
