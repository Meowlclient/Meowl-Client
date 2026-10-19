package com.lennart.nebula.gui;

import com.lennart.nebula.emote.EmoteType;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.ModuleManager;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.setting.StyleSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Katalog des Kosmetik-Planeten: alle Kosmetik-Module mit ihren Varianten als Shop-Artikel, jeweils mit Seltenheit und
 * Preis in Sternen. Bezahlt wird noch nicht – solange gehört allen alles ({@link #OWN_EVERYTHING}).
 */
public final class ShopCatalog {
   /** Testphase: jeder besitzt alles, Preise sind nur Anzeige. */
   public static final boolean OWN_EVERYTHING = true;

   public enum Rarity {
      COMMON("Gewöhnlich", 0xFF9AA6BE, 1.0F),
      RARE("Selten", 0xFF4FA8FF, 1.7F),
      EPIC("Episch", 0xFFB45CFF, 2.8F),
      LEGENDARY("Legendär", 0xFFFFB43C, 4.5F);

      public final String label;
      public final int color;
      final float factor;

      Rarity(String label, int color, float factor) {
         this.label = label;
         this.color = color;
         this.factor = factor;
      }
   }

   /** Ein Artikel: Variante eines Moduls (mode != null), ein Emote oder das ganze Modul. */
   public record Item(Module module, String name, ModeSetting setting, String mode, EmoteType emote, Rarity rarity, int price) {
      public boolean owned() {
         return OWN_EVERYTHING;
      }

      /** Gerade getragen/aktiv? */
      public boolean equipped() {
         if (this.emote != null) {
            return false;
         }

         return this.module.isEnabled() && (this.setting == null || this.setting.is(this.mode));
      }
   }

   public record Category(Module module, String name, String blurb, int color, List<Item> items) {
   }

   /** Grundpreis je Modul (Sterne); teure, aufwendige Effekte kosten mehr. */
   private static final Map<String, Integer> BASE = Map.ofEntries(
      Map.entry("Auren", 600),
      Map.entry("Hüte", 350),
      Map.entry("Brillen & Masken", 300),
      Map.entry("Schweife", 450),
      Map.entry("Schulterbegleiter", 500),
      Map.entry("Status-Symbol", 150),
      Map.entry("Hände", 400),
      Map.entry("Accessoires", 250),
      Map.entry("Pets", 700),
      Map.entry("Watches", 400),
      Map.entry("Death Animation", 550),
      Map.entry("Totem Inspect", 250),
      Map.entry("Emotes", 180),
      Map.entry("Schwerter", 1200),
      Map.entry("Profilbild", 300)
   );
   private static final String[] LEGENDARY_WORDS = {
      "galax", "regenbogen", "rainbow", "drache", "dragon", "phönix", "phoenix", "plasma", "kosmos", "void", "engel", "neunschwanz", "rasengan",
      "chidori", "sternenwirbel", "griddy", "67", "hubschrauber", "panzer", "katana", "karambit"
   };
   private static final String[] EPIC_WORDS = {
      "feuer", "blitz", "kristall", "diamant", "netherit", "gold", "leucht", "neon", "geist", "teufel", "dämon", "eis", "frost", "sakura",
      "krone", "roboter", "cyber", "vr", "lichtschlag", "schockwelle", "spektakul", "zauber", "dab"
   };

   private ShopCatalog() {
   }

   private static boolean has(String name, String[] words) {
      String n = name.toLowerCase(Locale.ROOT);

      for (String w : words) {
         if (n.contains(w)) {
            return true;
         }
      }

      return false;
   }

   /** Seltenheit: Schlüsselwörter, sonst fest aus dem Namen (immer gleich), erster Artikel immer gewöhnlich. */
   public static Rarity rarity(String category, String name, int index) {
      if (index == 0) {
         return Rarity.COMMON;
      } else if (has(name, LEGENDARY_WORDS)) {
         return Rarity.LEGENDARY;
      } else if (has(name, EPIC_WORDS)) {
         return Rarity.EPIC;
      } else {
         int h = Math.floorMod((category + "|" + name).hashCode(), 100);
         return h < 58 ? Rarity.COMMON : (h < 88 ? Rarity.RARE : Rarity.EPIC);
      }
   }

   public static int price(String category, Rarity r, int index) {
      if (index == 0) {
         return 0;
      }

      int base = BASE.getOrDefault(category, 300);
      int p = Math.round(base * r.factor / 50.0F) * 50;
      return Math.max(100, p);
   }

   private static ModeSetting variants(Module m) {
      for (Setting<?> s : m.getSettings()) {
         if (s instanceof StyleSetting st) {
            return st;
         }
      }

      for (Setting<?> s : m.getSettings()) {
         if (s instanceof ModeSetting ms && (s.getName().equals("Stil") || s.getName().equals("Modell") || s.getName().equals("Animation"))) {
            return ms;
         }
      }

      return null;
   }

   private static String blurb(Module m) {
      String d = m.getDescription();
      int cut = d.indexOf(':');
      return cut > 10 && cut < 60 ? d.substring(0, cut) : (d.length() > 90 ? d.substring(0, 88) + " …" : d);
   }

   public static List<Category> build(ModuleManager modules) {
      List<Category> out = new ArrayList<>();
      int[] palette = {0xFFFF7EB6, 0xFF8B5CF6, 0xFF4FA8FF, 0xFF3EE0C0, 0xFFFFC857, 0xFFFF8A4A, 0xFF7CE36A, 0xFFE05CFF, 0xFF5CC8FF, 0xFFFF5F6D};
      int ci = 0;

      for (Module m : modules.getModules()) {
         if (m.isHidden() || m.getSection() != Section.COSMETICS || m.getClass().getSimpleName().equals("OtherClients")) {
            continue;
         }

         String cat = m.getName();
         List<Item> items = new ArrayList<>();
         if (m.getClass().getSimpleName().equals("Emotes")) {
            int i = 0;

            for (EmoteType t : EmoteType.listed()) {
               Rarity r = rarity(cat, t.label, i);
               items.add(new Item(m, t.label, null, null, t, r, price(cat, r, i)));
               i++;
            }
         } else {
            ModeSetting v = variants(m);
            if (v != null) {
               int i = 0;

               for (String mode : v.getModes()) {
                  if (mode.equalsIgnoreCase("Aus") || mode.startsWith("Skin:") || mode.equalsIgnoreCase("Spieler-Skin")) {
                     continue;
                  }

                  Rarity r = rarity(cat, mode, i);
                  items.add(new Item(m, mode, v, mode, null, r, price(cat, r, i)));
                  i++;
               }
            } else {
               Rarity r = has(cat, LEGENDARY_WORDS) || cat.equals("Schwerter") ? Rarity.LEGENDARY : Rarity.EPIC;
               items.add(new Item(m, cat, null, null, null, r, price(cat, r, 1)));
            }
         }

         if (!items.isEmpty()) {
            out.add(new Category(m, cat, blurb(m), palette[ci++ % palette.length], items));
         }
      }

      return out;
   }
}
