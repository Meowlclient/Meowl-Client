package com.lennart.nebula.nametag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

public final class NametagConfig {
   private final Map<NametagConfig.Zone, List<NametagConfig.Element>> zones = new EnumMap<>(NametagConfig.Zone.class);

   private NametagConfig() {
      for (NametagConfig.Zone z : NametagConfig.Zone.values()) {
         this.zones.put(z, new ArrayList<>());
      }
   }

   /** Standard: Meowl-Symbol ganz außen links, Tiers links/rechts, kein Freunde-Stern, keine Zeile darüber. */
   public static final String DEFAULT = "L=MEOWL,TIER_1;R=TIER_2,POPS,VOICE;A=;H=FRIEND,TIER_KITS";

   public static NametagConfig parse(String text) {
      NametagConfig c = new NametagConfig();
      if (text != null) {
         for (String part : text.split(";")) {
            int eq = part.indexOf(61);
            if (eq > 0) {
               NametagConfig.Zone zone = null;

               for (NametagConfig.Zone z : NametagConfig.Zone.values()) {
                  if (z.key.equalsIgnoreCase(part.substring(0, eq).trim())) {
                     zone = z;
                  }
               }

               if (zone != null) {
                  for (String name : part.substring(eq + 1).split(",")) {
                     try {
                        NametagConfig.Element e = NametagConfig.Element.valueOf(name.trim().toUpperCase(Locale.ROOT));
                        if (c.zoneOf(e) == null) {
                           c.zones.get(zone).add(e);
                        }
                     } catch (IllegalArgumentException var13) {
                     }
                  }
               }
            }
         }
      }

      NametagConfig def = text != null && !text.equals(DEFAULT)
         ? parse(DEFAULT)
         : null;

      for (NametagConfig.Element e : NametagConfig.Element.values()) {
         if (c.zoneOf(e) == null) {
            NametagConfig.Zone zx = def != null ? def.zoneOf(e) : NametagConfig.Zone.HIDDEN;
            c.zones.get(zx == null ? NametagConfig.Zone.HIDDEN : zx).add(e);
         }
      }

      return c;
   }

   public String serialize() {
      StringBuilder sb = new StringBuilder();

      for (NametagConfig.Zone z : NametagConfig.Zone.values()) {
         if (sb.length() > 0) {
            sb.append(';');
         }

         sb.append(z.key).append('=');
         List<NametagConfig.Element> list = this.zones.get(z);

         for (int i = 0; i < list.size(); i++) {
            sb.append(i > 0 ? "," : "").append(list.get(i).name());
         }
      }

      return sb.toString();
   }

   public List<NametagConfig.Element> get(NametagConfig.Zone zone) {
      return this.zones.get(zone);
   }

   public NametagConfig.Zone zoneOf(NametagConfig.Element e) {
      for (Entry<NametagConfig.Zone, List<NametagConfig.Element>> entry : this.zones.entrySet()) {
         if (entry.getValue().contains(e)) {
            return entry.getKey();
         }
      }

      return null;
   }

   public void move(NametagConfig.Element e, NametagConfig.Zone zone, int index) {
      for (List<NametagConfig.Element> list : this.zones.values()) {
         list.remove(e);
      }

      List<NametagConfig.Element> target = this.zones.get(zone);
      target.add(Math.max(0, Math.min(index, target.size())), e);
   }

   public NametagConfig copy() {
      return parse(this.serialize());
   }

   public static enum Element {
      MEOWL("Meowl-Zeichen"),
      FRIEND("Freund-Stern"),
      TIER_1("Tier (Liste 1)"),
      TIER_2("Tier (Liste 2)"),
      TIER_KITS("Kit-Tiers (Zeile)"),
      POPS("Totem-Pops"),
      VOICE("Voice-Chat-Symbol");

      public final String label;

      private Element(String label) {
         this.label = label;
      }
   }

   public static enum Zone {
      LEFT("L", "Links"),
      RIGHT("R", "Rechts"),
      ABOVE("A", "Darüber"),
      HIDDEN("H", "Ausgeblendet");

      final String key;
      public final String label;

      private Zone(String key, String label) {
         this.key = key;
         this.label = label;
      }
   }
}
