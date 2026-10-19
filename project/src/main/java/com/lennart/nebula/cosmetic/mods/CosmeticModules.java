package com.lennart.nebula.cosmetic.mods;

import com.lennart.nebula.module.ModuleManager;

/** Alle Kosmetik-Module an einer Stelle registrieren. */
public final class CosmeticModules {
   private CosmeticModules() {
   }

   public static void registerAll(ModuleManager manager) {
      manager.register(new Aura());
      manager.register(new Hats());
      manager.register(new FaceGear());
      manager.register(new Tails());
      manager.register(new ShoulderBuddy());
      manager.register(new MoodIcon());
      manager.register(new Hands());
      manager.register(new Accessories());
   }
}
