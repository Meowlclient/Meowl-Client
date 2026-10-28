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
   private final BooleanSetting overlay = this.add(new BooleanSetting("Overlay im Spiel", "Kleine Anzeige im HUD: FPS und was wie viele FPS kostet", false));
   private final NumberSetting overlayRows = this.add(new NumberSetting("Overlay-Zeilen", "Wie viele Einträge das Overlay zeigt", 8.0, 3.0, 40.0, 1.0, ""));
   private final BooleanSetting overlayAll = this.add(new BooleanSetting("Alle Module", "Auch Module ohne messbare Kosten im Overlay zeigen", false));
   private final com.lennart.nebula.module.hud.HudBox box = this.hudBox("Leistung", 1.0, 30.0);
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

   public BooleanSetting overlaySetting() {
      return this.overlay;
   }

   @Override
   public void onRenderHud(net.minecraft.client.gui.GuiGraphicsExtractor ctx, float tickDelta) {
      boolean editing = com.lennart.nebula.module.hud.HudLayout.isEditing();
      if (!this.overlay.get() && !editing) {
         return;
      }

      Perf.wantBackground();
      net.minecraft.client.gui.Font font = this.mc.font;
      Map<Object, Double> cost = Perf.msPerFrame();
      List<Object[]> rows = new ArrayList<>();
      double total = 0.0;
      for (Map.Entry<Object, Double> e : cost.entrySet()) {
         total += e.getValue();
      }

      for (Module m : NebulaClient.getInstance().getModuleManager().getModules()) {
         if (m.isHidden() || !m.isEnabled() && !(m instanceof Cosmetic)) {
            continue;
         }

         double ms = cost.getOrDefault(m, 0.0);
         if (m instanceof Cosmetic c) {
            double others = cost.getOrDefault(c.othersKey(), 0.0);
            ms += others;
            if (!m.isEnabled() && others <= 0.0) {
               continue;
            }
         }

         if (ms >= 0.003 || this.overlayAll.get()) {
            rows.add(new Object[]{m.getName(), ms});
         }
      }

      for (Map.Entry<String, Double> e : Perf.background().entrySet()) {
         if (e.getValue() >= 0.5) {
            rows.add(new Object[]{e.getKey().replace("Meowl-", "") + " (Hintergrund)", -e.getValue()});
         }
      }

      rows.sort((a, b) -> Double.compare(Math.abs((Double)b[1]) * ((Double)b[1] < 0 ? 0.02 : 1.0), Math.abs((Double)a[1]) * ((Double)a[1] < 0 ? 0.02 : 1.0)));
      int n = Math.min(rows.size(), this.overlayRows.getInt());
      int w = 150;
      int h = 26 + n * 10;
      this.box.begin(ctx, w, h);
      ctx.text(font, "§l" + Perf.fps() + " FPS §7" + String.format("%.1f ms", Perf.frameMs()), 4, 4, 0xFFFFFFFF, true);
      ctx.text(font, "Meowl: −" + String.format("%.0f", Perf.fpsCost(total)) + " FPS", 4, 14, 0xFFB98CFF, true);
      for (int i = 0; i < n; i++) {
         String name = (String)rows.get(i)[0];
         double v = (Double)rows.get(i)[1];
         String val = v < 0.0 ? String.format("%.1f%% CPU", -v) : String.format("−%.1f FPS", Perf.fpsCost(v));
         double fc = v < 0.0 ? 0.0 : Perf.fpsCost(v);
         int col = fc > 10.0 ? 0xFFFF7070 : (fc > 2.0 ? 0xFFFFC857 : 0xFFBFC4D0);
         int y = 26 + i * 10;
         ctx.text(font, font.plainSubstrByWidth(name, w - 8 - font.width(val) - 4), 4, y, 0xFFE0E0E8, false);
         ctx.text(font, val, w - 4 - font.width(val), y, col, false);
      }

      this.box.end(ctx);
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
