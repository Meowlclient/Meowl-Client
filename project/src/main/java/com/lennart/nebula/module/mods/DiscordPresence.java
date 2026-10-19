package com.lennart.nebula.module.mods;

import com.lennart.nebula.module.Section;
import com.google.gson.JsonObject;
import com.lennart.nebula.discord.DiscordStatus;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.net.MeowlService;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.TextSetting;
import net.minecraft.world.item.Items;

public class DiscordPresence extends Module {
   public static final String DEFAULT_APP_ID = "1557308109532176384";
   private final TextSetting appId = this.add(
      new TextSetting(
            "Eigene Application-ID",
            "Optional – leer lassen: dann wird die Meowl-Client-App genutzt (vom Client bzw. Meowl Server). Nur ausfüllen, wenn du eine eigene Discord-App willst.",
            "",
            24
         )
         .validator(s -> s.isEmpty() || s.matches("\\d{15,22}"))
         .alias(new String[]{"Application-ID"})
   );
   private final TextSetting imageKey = this.add(
      new TextSetting("Bild", "Optional – leer = Meowl-Logo (automatisch). Oder Name eines in Discord hochgeladenen Bildes", "", 64)
   );
   private final BooleanSetting showVersion = this.add(new BooleanSetting("Version anzeigen", "Meowl-Version im Status", true));
   private final BooleanSetting showMode = this.add(new BooleanSetting("Spielmodus anzeigen", "„Singleplayer“ / „Multiplayer“ / „Im Menü“", true));
   private final BooleanSetting showServer = this.add(new BooleanSetting("Server anzeigen", "Server-Adresse im Status (aus = privat)", false));
   private final BooleanSetting showTime = this.add(new BooleanSetting("Spielzeit anzeigen", "Wie lange du schon spielst", true));
   private final DiscordStatus status = new DiscordStatus();
   private final long startedAt = System.currentTimeMillis() / 1000L;
   private int timer;

   public DiscordPresence() {
      super("Discord Presence", "Zeigt in Discord „Spielt Meowl Client“ mit Meowl-Logo – funktioniert ohne Einstellungen, ohne private Daten.", Category.MISC);
      this.section(Section.MEOWL);
      this.icon(Items.NAME_TAG);
      // kein eigener Mond mehr: an/aus und Einstellungen stehen im Zahnrad oben rechts
      this.hidden();
      this.showServer.setVisibility(this.showMode::get);
      this.setEnabled(true);
   }

   private String effectiveAppId() {
      String own = this.appId.get().trim();
      if (!own.isEmpty()) {
         return own;
      } else {
         String server = MeowlService.get().discordAppId();
         return server != null && server.matches("\\d{15,22}") ? server : DEFAULT_APP_ID;
      }
   }

   public String statusText() {
      return this.status.text();
   }

   @Override
   protected void onEnable() {
      this.timer = 0;
   }

   @Override
   protected void onDisable() {
      this.status.stop();
   }

   @Override
   public void onTick() {
      if (--this.timer <= 0) {
         this.timer = 100;
         this.push();
      }
   }

   public void tickAlways() {
      if (this.isEnabled() && this.mc.level == null && --this.timer <= 0) {
         this.timer = 100;
         this.push();
      }
   }

   private void push() {
      String id = this.effectiveAppId();
      if (id.isEmpty()) {
         this.status.setText("Wartet auf Meowl Server (Discord-App)");
      } else {
         this.status.update(id, this.activity());
      }
   }

   private JsonObject activity() {
      JsonObject a = new JsonObject();
      String version = MeowlService.CLIENT_VERSION;
      a.addProperty("details", "Minecraft 1.21.11" + (this.showVersion.get() ? " · Meowl " + version : ""));
      if (this.showMode.get()) {
         String mode;
         if (this.mc.level == null) {
            mode = "Im Menü";
         } else if (this.mc.hasSingleplayerServer()) {
            mode = "Singleplayer";
         } else {
            mode = "Multiplayer";
            if (this.showServer.get() && this.mc.getCurrentServer() != null) {
               mode = mode + " · " + this.mc.getCurrentServer().ip;
            }
         }

         a.addProperty("state", mode.length() > 120 ? mode.substring(0, 120) : mode);
      }

      if (this.showTime.get()) {
         JsonObject ts = new JsonObject();
         ts.addProperty("start", this.startedAt);
         a.add("timestamps", ts);
      }

      JsonObject assets = new JsonObject();
      String key = this.imageKey.get().trim();
      if (key.isEmpty()) {
         String base = MeowlService.get().baseUrl();
         key = (base != null && base.startsWith("https://") ? base : com.lennart.nebula.net.ServerAddress.official()) + "/assets/meowl.png";
      }

      assets.addProperty("large_image", key);
      assets.addProperty("large_text", "Meowl Client " + version);
      a.add("assets", assets);
      return a;
   }
}
