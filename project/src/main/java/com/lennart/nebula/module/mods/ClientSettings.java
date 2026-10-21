package com.lennart.nebula.module.mods;

import com.lennart.nebula.util.MeowlCommands;
import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.net.MeowlService;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.gui.Lang;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ModeSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.TextSetting;
import com.lennart.nebula.update.UpdateManager;
import com.lennart.nebula.util.ChatUtil;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.item.Items;

public class ClientSettings extends Module {
   private final BooleanSetting checkUpdates = this.add(
      new BooleanSetting("Nach Updates suchen", "Beim Start und regelmäßig prüfen – installiert wird nur, wenn du zustimmst", true)
   );
   private final NumberSetting interval = this.add(new NumberSetting("Prüf-Intervall", "Wie oft nach Updates gesucht wird", 6.0, 1.0, 24.0, 1.0, "Std."));
   private final TextSetting updateUrl = this.add(
      new TextSetting("Update-Quelle", "Leer = dein Meowl Server. Nur ändern, wenn Updates woanders liegen.", "", 200)
         .validator(s -> s.isEmpty() || MeowlService.isValidUrl(s))
   );
   private final BooleanSetting animations = this.add(
      new BooleanSetting("Animationen", "Weiche Übergänge im Menü (aus = alles sofort, für schwache PCs)", true)
   );
   private final ModeSetting language = this.add(new ModeSetting("Sprache", "Sprache des Meowl Clients", "Deutsch", "Deutsch", "English"));
   private final NumberSetting menuScale = this.add(
      new NumberSetting("Menü-Größe", "Größe aller Meowl-Menüs wie die GUI-Größe von Minecraft (0 = wie Minecraft). Auch unten links in jedem Menü einstellbar.", 0.0, 0.0, 6.0, 1.0, "")
   );
   private final BooleanSetting borderless = this.add(
      new BooleanSetting("Randloses Vollbild", "Vollbild als randloses Fenster statt exklusiv – Discord-Streams und Alt-Tab laufen flüssig (wirkt beim nächsten F11)", true)
   );
   private final BooleanSetting planetDrag = this.add(new BooleanSetting("Planeten verschieben", "Planeten im Meowl-Menü mit der Maus verschieben", false));
   private final BooleanSetting meowlTitle = this.add(
      new BooleanSetting("Meowl-Hauptmenü", "Eigenes Hauptmenü im Weltraum-Look statt des Minecraft-Titelbildschirms", true)
   );
   /** Wurde beim ersten Start schon gefragt, welches Hauptmenü man will? (unsichtbar) */
   private final BooleanSetting menuChosen = this.add(new BooleanSetting("Hauptmenü gewählt", "Beim ersten Start gefragt", false));
   /** Einmalig: Module zum Sehen (Block Images, Sheriff Duel, Freunde, Profilbild) für alle einschalten (unsichtbar). */
   private final BooleanSetting viewersOn = this.add(new BooleanSetting("Sicht-Module an", "Einmalig eingeschaltet", false));
   private final UpdateManager updates;
   private static ClientSettings instance;

   /** Eigenes Hauptmenü statt des Minecraft-Titelbildschirms (Standard an, auch vor dem Laden der Einstellungen). */
   public static boolean meowlTitle() {
      return instance == null || instance.meowlTitle.get();
   }

   /** Der Schalter selbst (für das Zahnrad-Fenster im Meowl-Menü). */
   public static BooleanSetting meowlTitleSetting() {
      return instance == null ? null : instance.meowlTitle;
   }

   /** Nach dem Laden der Einstellungen: Module, die man zum Sehen der Sachen anderer braucht, einmalig einschalten. */
   public static boolean enableViewersOnce(com.lennart.nebula.module.ModuleManager modules) {
      if (instance == null || instance.viewersOn.get()) {
         return false;
      }

      for (com.lennart.nebula.module.Module m : modules.getModules()) {
         String n = m.getName();
         if ((n.equals("Block Images") || n.equals("Sheriff Duel") || n.equals("Freunde") || n.equals("Profilbild")) && !m.isEnabled()) {
            m.setEnabled(true);
         }
      }

      instance.viewersOn.set(true);
      return true;
   }

   /** Beim ersten Start noch nicht gefragt, welches Hauptmenü man will? */
   public static boolean askMenu() {
      return instance != null && !instance.menuChosen.get();
   }

   /** Antwort auf die Frage beim ersten Start speichern. */
   public static void chooseMenu(boolean meowl) {
      if (instance != null) {
         instance.menuChosen.set(true);
         setMeowlTitle(meowl);
      }
   }

   /** Hauptmenü umschalten (Meowl oder Minecraft) und speichern. */
   public static void setMeowlTitle(boolean on) {
      if (instance != null) {
         instance.meowlTitle.set(on);
         com.lennart.nebula.NebulaClient c = com.lennart.nebula.NebulaClient.getInstance();
         if (c != null && c.getConfigManager() != null) {
            c.getConfigManager().save();
         }
      }
   }

   /** Vollbild randlos (Standard an, auch bevor die Einstellungen geladen sind). */
   public static boolean borderless() {
      return instance == null || instance.borderless.get();
   }

   public static boolean planetDrag() {
      return instance != null && instance.planetDrag.get();
   }

   /** GUI-Größe der Meowl-Menüs (0 = wie Minecraft). */
   public static int menuScale() {
      return instance == null ? 0 : (int)Math.round(instance.menuScale.get());
   }

   public static void setMenuScale(int value) {
      if (instance != null) {
         instance.menuScale.set((double)Math.max(0, Math.min(6, value)));
      }
   }

   public ClientSettings() {
      super("Client", "Updates und Oberfläche des Meowl Clients.", Category.MISC);
      instance = this;
      this.icon(Items.NETHER_STAR);
      this.hidden();
      this.interval.setVisibility(this.checkUpdates::get);
      this.menuChosen.setVisibility(() -> false);
      this.viewersOn.setVisibility(() -> false);
      this.updates = new UpdateManager(new UpdateManager.Settings() {
         @Override
         public boolean checkEnabled() {
            return ClientSettings.this.checkUpdates.get();
         }

         @Override
         public double intervalHours() {
            return ClientSettings.this.interval.get();
         }

         @Override
         public String sourceUrl() {
            if (!ClientSettings.this.updateUrl.get().isBlank()) {
               return ClientSettings.this.updateUrl.get().trim();
            } else {
               MeowlServerModule server = NebulaClient.getInstance().getModuleManager().get(MeowlServerModule.class);
               return server != null ? server.targetUrl() : com.lennart.nebula.net.ServerAddress.official();
            }
         }
      });
      this.add(new ActionSetting("Jetzt nach Updates suchen", "Sofort prüfen", "Prüfen", () -> this.updates.check(true)));
      this.setEnabled(true);
      ClientTickEvents.END_CLIENT_TICK.register(c -> {
         Lang.setEnglish(this.language.is("English"));
         net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
         if (com.lennart.nebula.module.hud.InventoryScale.isMeowlMenu(mc.gui.screen())) {
            com.lennart.nebula.module.hud.InventoryScale.refresh(mc.gui.screen());
         }

         this.updates.tick();
      });
      ClientLifecycleEvents.CLIENT_STOPPING.register(c -> this.updates.onShutdown());
      MeowlCommands.add("update", "update [download|restart] §7– nach Client-Updates suchen", root -> ClientCommands.literal(root).executes(c -> {
         this.updates.check(true);
         return 1;
      }).then(ClientCommands.literal("restart").executes(c -> {
         if (this.updates.state() == UpdateManager.State.READY) {
            this.updates.restartToInstall();
         } else {
            ChatUtil.chat("§7" + this.updates.message());
         }

         return 1;
      })).then(ClientCommands.literal("later").executes(c -> {
         this.updates.declineCurrent();
         ChatUtil.chat("§7Okay – du wirst beim nächsten Start wieder gefragt.");
         return 1;
      })).then(ClientCommands.literal("download").executes(c -> {
         if (this.updates.state() != UpdateManager.State.AVAILABLE && this.updates.state() != UpdateManager.State.FAILED) {
            ChatUtil.chat("§7" + this.updates.message());
         } else {
            this.updates.download();
         }

         return 1;
      })));
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   public UpdateManager updates() {
      return this.updates;
   }

   /** Wechselt zwischen Deutsch und Englisch (Knopf oben im Menü). */
   public void toggleLanguage() {
      this.language.set(this.language.is("English") ? "Deutsch" : "English");
      Lang.setEnglish(this.language.is("English"));
      NebulaClient.getInstance().getModuleManager().notifyChanged();
   }

   @Override
   public void onConfigLoaded() {
      Lang.setEnglish(this.language.is("English"));
   }

   public boolean animations() {
      return this.animations.get();
   }
}
