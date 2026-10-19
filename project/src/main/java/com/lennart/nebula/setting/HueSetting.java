package com.lennart.nebula.setting;

import java.util.function.Supplier;

/** Farbton-Verschiebung in Grad (-180..180). Wird als Regenbogen-Leiste mit Farbvorschau angezeigt. */
public class HueSetting extends NumberSetting {
   private Supplier<int[]> preview = () -> new int[]{0xFFE83A3A, 0xFFFF9A1E, 0xFFF5D020, 0xFF4AC060, 0xFF3A8AF0, 0xFF9A5AF0};

   public HueSetting(String name, String description) {
      super(name, description, 0.0, -180.0, 180.0, 5.0, "°");
   }

   /** Grundfarben, die in der Vorschau verschoben gezeigt werden (z.B. die Farben des gewählten Stils). */
   public HueSetting preview(Supplier<int[]> colors) {
      this.preview = colors;
      return this;
   }

   public int[] previewColors() {
      int[] c = this.preview.get();
      return c == null ? new int[0] : c;
   }
}
