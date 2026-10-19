package com.lennart.nebula.emote;

/** Alle Emotes. {@code duration == 0} = läuft, bis man sich bewegt oder ihn erneut auslöst. */
public enum EmoteType {
   SIT("sit", "Hinsetzen", 0.0F, "⌂"),
   WAVE("wave", "Winken", 3.0F, "✋"),
   CHEER("cheer", "Jubeln", 3.0F, "★"),
   DANCE("dance", "Tanzen", 0.0F, "♪"),
   BOW("bow", "Verbeugen", 2.6F, "↓"),
   SALUTE("salute", "Salutieren", 2.8F, "⚑"),
   CLAP("clap", "Applaus", 3.0F, "✦"),
   POINT("point", "Zeigen", 2.6F, "☝"),
   FACEPALM("facepalm", "Facepalm", 3.0F, "✗"),
   DAB("dab", "Dab", 2.2F, "➚"),
   THINK("think", "Nachdenken", 0.0F, "?"),
   CROSS("cross", "Arme verschränken", 0.0F, "✕"),
   LAUGH("laugh", "Lachen", 2.6F, "☺"),
   SHRUG("shrug", "Schulterzucken", 2.0F, "~"),
   GRIDDY("griddy", "Griddy", 4.5F, "»"),
   // ID nur aus Buchstaben (der Meowl Server erlaubt keine Ziffern in Emote-IDs)
   SIX_SEVEN("six_seven", "67", 3.6F, "6"),
   KLEIN("klein", "Klein machen", 0.0F, "▾"),
   ROLLER("roller", "E-Scooter", 0.0F, "➟"),
   AUTO("auto", "Auto", 0.0F, "⛟"),
   PANZER("panzer", "Panzer", 0.0F, "▣"),
   HELI("heli", "Hubschrauber", 0.0F, "✈");

   public final String id;
   public final String label;
   public final float duration;
   public final String icon;

   EmoteType(String id, String label, float duration, String icon) {
      this.id = id;
      this.label = label;
      this.duration = duration;
      this.icon = icon;
   }

   /** Fahrzeug-Emote: man fährt bzw. fliegt mit einem eigenen Modell herum. */
   public boolean vehicle() {
      return this == ROLLER || this == AUTO || this == PANZER || this == HELI;
   }

   /** In Rad, Auswahl, Shop und Befehlen angeboten? (Der alte Felsen-Sitz nicht mehr – nur noch für AFK-Szenen.) */
   public boolean offered() {
      return this != SIT;
   }

   public static EmoteType[] listed() {
      return java.util.Arrays.stream(values()).filter(EmoteType::offered).toArray(EmoteType[]::new);
   }

   public boolean loops() {
      return this.duration <= 0.0F;
   }

   public static EmoteType byId(String id) {
      for (EmoteType t : values()) {
         if (t.id.equalsIgnoreCase(id) || t.label.equalsIgnoreCase(id) || t.name().equalsIgnoreCase(id)) {
            return t;
         }
      }

      return null;
   }
}
