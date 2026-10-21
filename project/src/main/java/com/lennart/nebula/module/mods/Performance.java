package com.lennart.nebula.module.mods;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.cosmetic.Cosmetic;
import com.lennart.nebula.gui.PerformanceScreen;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.util.ChatUtil;
import com.lennart.nebula.util.MeowlCommands;
import com.lennart.nebula.util.Perf;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.Items;

/**
 * Leistung: zeigt, was im Client wie viel Rechenzeit pro Bild kostet (Module, eigene Kosmetik, Kosmetik anderer,
 * Hintergrund-Threads), alles direkt abschaltbar; „Alles sauber machen“ schaltet mit einem Klick alles Teure ab und
 * merkt sich den Zustand zum Zurückholen. Dazu die Leistungs-Optionen des Clients.
 */
public class Performance extends Module {
   private static Performance instance;
   private final ModeSetting backgroundFps = this.add(
      new ModeSetting("Hintergrund-FPS", "Bildrate, solange Minecraft nicht im Vordergrund ist (spart Strom und Leistung für andere Programme)", "30", "Aus", "10", "30", "60")
   );
   private final NumberSetting cosmeticRange = this.add(
      new NumberSetting("Kosmetik-Sichtweite", "Kosmetik anderer Spieler nur bis zu dieser Entfernung zeichnen", 64.0, 8.0, 128.0, 8.0, "Blöcke")
   );
   private final BooleanSetting cosmeticParticles = this.add(new BooleanSetting("Kosmetik-Partikel", "Partikel von Kosmetik (Auren, Hände …)", true));
   /** Was „Alles sauber machen“ abgeschaltet hat (zum Zurückholen). */
   private final List<Module> cleanedModules = new ArrayList<>();
   private final List<Cosmetic> cleanedOthers = new ArrayList<>();
   private double cleanedRange = -1.0;
   private boolean cleanedParticles;

   public Performance() {
      super("Leistung", "Zeigt, was wie viel FPS kostet – Module, Kosmetik und Hintergrund-Aufgaben, einzeln abschaltbar oder alles mit einem Klick.", Category.MISC);
      instance = this;
      this.section(Section.CLIENT);
      this.icon(Items.CLOCK);
      this.tool();
      MeowlCommands.add("leistung", "leistung §7– Leistungs-Menü öffnen (auch: fps)", root -> ClientCommands.literal(root).executes(ctx -> {
         this.mc.schedule(() -> this.openTool(null));
         return 1;
      }), "fps");
   }

   public static Performance get() {
      return instance;
   }

   @Override
   public Screen createScreen(Screen parent) {
      return new PerformanceScreen(parent, this);
   }

   /** Hintergrund-FPS-Grenze, 0 = keine. */
   public static int backgroundFps() {
      Performance p = instance;
      if (p == null || p.backgroundFps.is("Aus")) {
         return 0;
      }

      try {
         return Integer.parseInt(p.backgroundFps.get());
      } catch (NumberFormatException var2) {
         return 30;
      }
   }

   public static double cosmeticRangeSqr() {
      Performance p = instance;
      double r = p == null ? 64.0 : p.cosmeticRange.get();
      return r * r;
   }

   public static boolean cosmeticParticles() {
      Performance p = instance;
      return p == null || p.cosmeticParticles.get();
   }

   public ModeSetting backgroundFpsSetting() {
      return this.backgroundFps;
   }

   public NumberSetting cosmeticRangeSetting() {
      return this.cosmeticRange;
   }

   public BooleanSetting cosmeticParticlesSetting() {
      return this.cosmeticParticles;
   }

   /** Darf man dieses Modul im Leistungs-Menü abschalten? (keine versteckten Kern-Module, nicht sich selbst) */
   public static boolean switchable(Module m) {
      return m != null && !m.isHidden() && !(m instanceof Performance) && !(m instanceof MeowlServerModule) && !m.isTool();
   }

   public boolean hasCleaned() {
      return !this.cleanedModules.isEmpty() || !this.cleanedOthers.isEmpty() || this.cleanedRange >= 0.0;
   }

   /**
    * Alles sauber machen: jedes Modul, das gerade messbar Zeit kostet oder im Hintergrund arbeitet, und die Kosmetik
    * anderer Spieler abschalten; Kosmetik-Sichtweite und -Partikel auf sparsam. Der alte Zustand wird gemerkt.
    */
   public int clean() {
      int n = 0;
      Map<Object, Double> cost = Perf.msPerFrame();
      for (Module m : NebulaClient.getInstance().getModuleManager().getModules()) {
         if (!switchable(m) || !m.isEnabled()) {
            continue;
         }

         Double ms = cost.get(m);
         boolean busy = ms != null && ms > 0.01 || PerformanceScreen.backgroundModule(m);
         if (busy) {
            m.setEnabled(false);
            this.cleanedModules.add(m);
            n++;
         }
      }

      for (Module m : NebulaClient.getInstance().getModuleManager().getModules()) {
         if (m instanceof Cosmetic c && c.showsOthers()) {
            c.setShowOthers(false);
            this.cleanedOthers.add(c);
            n++;
         }
      }

      if (this.cleanedRange < 0.0) {
         this.cleanedRange = this.cosmeticRange.get();
         this.cleanedParticles = this.cosmeticParticles.get();
      }

      this.cosmeticRange.set(24.0);
      this.cosmeticParticles.set(false);
      NebulaClient.getInstance().getModuleManager().notifyChanged();
      ChatUtil.actionBar("§aLeistungsmodus: " + n + " Dinge abgeschaltet §7(im Leistungs-Menü rückgängig machen)");
      return n;
   }

   /** Rückgängig: alles wieder so wie vor „Alles sauber machen“. */
   public void restore() {
      for (Module m : this.cleanedModules) {
         m.setEnabled(true);
      }

      for (Cosmetic c : this.cleanedOthers) {
         c.setShowOthers(true);
      }

      if (this.cleanedRange >= 0.0) {
         this.cosmeticRange.set(this.cleanedRange);
         this.cosmeticParticles.set(this.cleanedParticles);
      }

      this.cleanedModules.clear();
      this.cleanedOthers.clear();
      this.cleanedRange = -1.0;
      NebulaClient.getInstance().getModuleManager().notifyChanged();
      ChatUtil.actionBar("§7Leistungsmodus aus – alles wieder wie vorher");
   }
}
