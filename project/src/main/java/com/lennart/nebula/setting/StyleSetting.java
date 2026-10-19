package com.lennart.nebula.setting;

/**
 * Stil-Auswahl mit Vorschaubildern (Kosmetik, Flügel): im Menü als Raster, jedes Feld zeigt deinen Charakter mit
 * diesem Stil.
 */
public class StyleSetting extends ModeSetting {
   private final Object target;
   private final boolean fromBehind;

   /**
    * @param target     Kosmetik-Modul oder {@link com.lennart.nebula.cosmetic.PreviewOverrides#WINGS}
    * @param fromBehind Vorschau eher von hinten zeigen (z.B. Flügel, Rückenteile)
    */
   public StyleSetting(String name, String description, String defaultMode, Object target, boolean fromBehind, String... modes) {
      super(name, description, defaultMode, modes);
      this.target = target;
      this.fromBehind = fromBehind;
   }

   public Object target() {
      return this.target;
   }

   public boolean fromBehind() {
      return this.fromBehind;
   }
}
