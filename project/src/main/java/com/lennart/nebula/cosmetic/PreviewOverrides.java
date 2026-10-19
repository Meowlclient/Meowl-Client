package com.lennart.nebula.cosmetic;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Vorschaubilder im Menü: ein Render-State zeigt genau eine Kosmetik in einem bestimmten Stil (die anderen Kosmetiken
 * und Flügel bleiben weg). Gemerkt wird je Render-State-Objekt, weil das Zeichnen der Vorschau später passiert.
 */
public final class PreviewOverrides {
   /** Ziel für die Flügel (statt eines Kosmetik-Moduls). */
   public static final Object WINGS = new Object();
   /** Ziel für die Pet-Vorschau (Pet neben der Figur, keine Kosmetik). */
   public static final Object PET = new Object();
   private static final Map<Object, PreviewOverrides.Entry> MAP = Collections.synchronizedMap(new WeakHashMap<>());

   private PreviewOverrides() {
   }

   public static void put(Object renderState, Object target, String style) {
      MAP.put(renderState, new PreviewOverrides.Entry(target, style));
   }

   public static PreviewOverrides.Entry get(Object renderState) {
      return MAP.isEmpty() ? null : MAP.get(renderState);
   }

   public record Entry(Object target, String style) {
   }
}
