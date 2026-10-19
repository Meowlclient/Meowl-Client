package com.lennart.nebula.module.mods;

import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.net.MeowlService;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.TextSetting;
import com.lennart.nebula.util.ChatUtil;
import net.minecraft.world.item.Items;

public class MeowlServerModule extends Module {
   public static String defaultUrl() {
      return com.lennart.nebula.net.ServerAddress.official();
   }
   private final TextSetting url = this.add(
      new TextSetting("Eigene Adresse", "Leer lassen = offizieller Meowl Server (automatisch). Nur ausfüllen, wenn du einen anderen Server willst.", "", 200)
         .validator(s -> s.isEmpty() || MeowlService.isValidUrl(s))
   );
   private String connectedUrl;
   private MeowlService.Status lastStatus;

   public MeowlServerModule() {
      super("Meowl Server", "Verbindung zum Meowl Server (für Block Images, Facecam und Sheriff Duel).", Category.MISC);
      this.icon(Items.BEACON);
      this.hidden();
      this.add(new ActionSetting("Neu verbinden", "Verbindung neu aufbauen", "Verbinden", this::reconnect));
      this.setEnabled(true);
   }

   public String targetUrl() {
      String custom = this.url.get().trim();
      return !custom.isEmpty() && !custom.contains("trycloudflare.com") ? custom : com.lennart.nebula.net.ServerAddress.official();
   }

   @Override
   public boolean persistEnabled() {
      return false;
   }

   private void reconnect() {
      this.connectedUrl = null;
   }

   @Override
   protected void onEnable() {
      this.connectedUrl = null;
   }

   @Override
   protected void onDisable() {
      MeowlService.get().disconnect();
      this.connectedUrl = null;
   }

   @Override
   public void onTick() {
      String target = this.targetUrl();
      if (target.isEmpty()) {
         if (this.connectedUrl != null) {
            MeowlService.get().disconnect();
            this.connectedUrl = null;
         }
      } else {
         if (!target.equals(this.connectedUrl) && MeowlService.isValidUrl(target)) {
            this.connectedUrl = target;
            MeowlService.get().connect(target);
         }

         MeowlService.Status s = MeowlService.get().status();
         if (s != this.lastStatus) {
            this.lastStatus = s;
            if (s == MeowlService.Status.CONNECTED || s == MeowlService.Status.ERROR) {
               ChatUtil.actionBar("Meowl Server: " + MeowlService.get().statusText());
            }
         }
      }
   }
}
