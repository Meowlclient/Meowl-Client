package com.lennart.nebula.fx;

import com.lennart.nebula.module.mods.DesertAfk;
import com.lennart.nebula.module.mods.Zoom;

/** Sichtfeld-Faktor aus allen Effekten (Zoom, AFK-Szenen). 1 = unverändert. */
public final class FovFx {
   private FovFx() {
   }

   public static float factor() {
      return DesertAfk.fovFactor() * Zoom.fovFactor();
   }
}
