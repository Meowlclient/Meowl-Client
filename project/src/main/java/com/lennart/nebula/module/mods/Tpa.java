package com.lennart.nebula.module.mods;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.gui.TpaScreen;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.NumberSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.setting.StringListSetting;
import com.lennart.nebula.setting.TextSetting;
import com.lennart.nebula.util.ChatUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;

/**
 * TPA-Menü: Taste drücken – Liste deiner gespeicherten Spieler, mit einem Klick eine /tpa schicken. Eingehende
 * Anfragen werden aus dem Chat erkannt und lassen sich direkt annehmen (/tpaccept Name) oder ablehnen.
 */
public class Tpa extends Module {
   private static Tpa instance;
   private final KeybindSetting key = this.add(new KeybindSetting("Menü-Taste", "Öffnet das TPA-Menü", Bind.keyboard(InputConstants.KEY_U)));
   private final StringListSetting players = this.add(
      new StringListSetting("Spieler", "Deine Spieler für schnelle TPA-Anfragen", List.of()).normalizer(Tpa::name, "Spielername …")
   );
   private final TextSetting tpaCmd = this.add(new TextSetting("Befehl: Anfrage", "{name} = Spieler", "tpa {name}", 64));
   private final TextSetting hereCmd = this.add(new TextSetting("Befehl: Herholen", "{name} = Spieler", "tpahere {name}", 64));
   private final TextSetting acceptCmd = this.add(new TextSetting("Befehl: Annehmen", "{name} = Spieler", "tpaccept {name}", 64));
   private final TextSetting denyCmd = this.add(new TextSetting("Befehl: Ablehnen", "{name} = Spieler", "tpdeny {name}", 64));
   private final NumberSetting expiry = this.add(new NumberSetting("Anfrage gültig", "Wie lange eine Anfrage in der Liste bleibt", 120.0, 30.0, 600.0, 10.0, "s"));
   private final Map<String, Long> requests = new LinkedHashMap<>();
   private final Map<String, Boolean> hereRequests = new java.util.HashMap<>();

   public Tpa() {
      super("TPA", "TPA-Menü per Taste: Spieler speichern, mit einem Klick /tpa schicken und Anfragen direkt annehmen.", Category.MISC);
      this.section(Section.FRIENDS);
      this.icon(Items.ENDER_PEARL);
      instance = this;
      this.group("Menü", new Setting[]{this.key, this.players, this.expiry});
      this.group("Befehle (je Server)", new Setting[]{this.tpaCmd, this.hereCmd, this.acceptCmd, this.denyCmd});
      this.setEnabled(true);
   }

   public static Tpa get() {
      return instance;
   }

   private static String name(String s) {
      String t = s.trim();
      return t.matches("[A-Za-z0-9_]{2,16}") ? t : null;
   }

   @Override
   public void onTick() {
      if (this.mc.screen == null && this.key.get().isPressed() && this.mc.player != null) {
         this.mc.setScreen(new TpaScreen(this));
      }

      long now = System.currentTimeMillis();
      this.requests.values().removeIf(t -> now - t > (long)(this.expiry.get() * 1000.0));
      this.hereRequests.keySet().retainAll(this.requests.keySet());
   }

   public List<String> players() {
      return this.players.get();
   }

   public void addPlayer(String name) {
      String n = name(name);
      if (n != null && !this.players.contains(n)) {
         this.players.addEntry(n);
      }
   }

   public void removePlayer(String name) {
      this.players.removeEntry(name);
   }

   /** Spieler, die gerade auf dem Server sind (Tab-Liste), außer dir. */
   public List<String> online() {
      List<String> out = new ArrayList<>();
      if (this.mc.getConnection() != null && this.mc.player != null) {
         String me = this.mc.player.getGameProfile().name();

         // Tab-Listen enthalten oft Deko-Einträge (Spalten, Platzhalter, NPCs): nur echte Konten zählen –
         // Premium-UUIDs (Version 4) bzw. Offline-UUIDs (Version 3), sichtbar in der Tab-Liste
         java.util.Collection<PlayerInfo> all = this.mc.getConnection().getListedOnlinePlayers();
         if (all.isEmpty()) {
            all = this.mc.getConnection().getOnlinePlayers();
         }

         for (PlayerInfo info : all) {
            String n = info.getProfile().name();
            int v = info.getProfile().id().version();
            if ((v == 4 || v == 3) && !n.equalsIgnoreCase(me) && n.matches("[A-Za-z0-9_]{3,16}") && !out.contains(n)) {
               out.add(n);
            }
         }
      }

      out.sort(String.CASE_INSENSITIVE_ORDER);
      return out;
   }

   public List<String> requests() {
      return new ArrayList<>(this.requests.keySet());
   }

   /** true = /tpahere (der andere holt dich zu sich), false = /tpa (er will zu dir). */
   public boolean isHereRequest(String name) {
      return Boolean.TRUE.equals(this.hereRequests.get(name));
   }

   public long requestAge(String name) {
      Long t = this.requests.get(name);
      return t == null ? 0L : (System.currentTimeMillis() - t) / 1000L;
   }

   private void run(TextSetting cmd, String name) {
      if (this.mc.player != null && this.mc.getConnection() != null) {
         String c = cmd.get().trim().replace("{name}", name);
         if (c.startsWith("/")) {
            c = c.substring(1);
         }

         this.mc.getConnection().sendCommand(c);
      }
   }

   public void sendTpa(String name) {
      this.run(this.tpaCmd, name);
      ChatUtil.actionBar("§bTPA-Anfrage an §f" + name + " §bgeschickt");
   }

   public void sendHere(String name) {
      this.run(this.hereCmd, name);
      ChatUtil.actionBar("§b" + name + " §bzu dir geholt (Anfrage geschickt)");
   }

   public void accept(String name) {
      this.run(this.acceptCmd, name);
      this.requests.remove(name);
   }

   public void deny(String name) {
      this.run(this.denyCmd, name);
      this.requests.remove(name);
   }

   /** Erkennungsmuster echter Anfrage-Texte (Essentials, CMI, HuskHomes, deutsche Server …). */
   private static final String[] TO_YOU = new String[]{
      "requested to teleport to you",
      "requests to teleport to you",
      "wants to teleport to you",
      "would like to teleport to you",
      "sent you a teleport request",
      "sent you a tpa request",
      "teleport request from",
      "tpa request from",
      "möchte sich zu dir teleportieren",
      "will sich zu dir teleportieren",
      "möchte zu dir teleportiert werden",
      "teleportanfrage von",
      "tpa-anfrage von",
      "hat dir eine teleportanfrage",
      "hat dir eine tpa",
      "schickt dir eine teleportanfrage"
   };
   private static final String[] HERE = new String[]{
      "requested that you teleport to them",
      "requests that you teleport to them",
      "wants you to teleport to them",
      "would like you to teleport to them",
      "teleport here request",
      "tpahere request",
      "möchte, dass du dich zu",
      "möchte dass du dich zu",
      "will, dass du dich zu",
      "will dass du dich zu",
      "dich zu sich teleportieren",
      "tpahere-anfrage"
   };
   /** Folgezeilen mit Annehm-Hinweis („Type /tpaccept …“) gehören zur Zeile davor. */
   private static final String[] ACCEPT_HINT = new String[]{"/tpaccept", "/tpyes", "/tpaaccept", "tpaccept"};
   private static String pendingName;
   private static boolean pendingHere;
   private static long pendingAt;

   /** Chat-Nachricht prüfen: eingehende TPA-Anfrage? (Erkennt gängige deutsche und englische Texte.) */
   public static void onChat(Component message) {
      onChat(message, false);
   }

   /**
    * @param playerChat signierte Spieler-Nachricht – das ist nie eine Server-Anfrage, auch wenn „tpa“ drinsteht
    */
   public static void onChat(Component message, boolean playerChat) {
      Tpa t = instance;
      if (t == null || !t.isEnabled() || message == null || t.mc.player == null || playerChat) {
         return;
      }

      String plain = message.getString();
      String lower = plain.toLowerCase(Locale.ROOT).replace('’', '\'');
      boolean toYou = containsAny(lower, TO_YOU);
      boolean here = containsAny(lower, HERE);
      boolean hint = containsAny(lower, ACCEPT_HINT);
      String name = null;

      for (String n : t.online()) {
         if (com.lennart.nebula.nametag.ServerNametags.containsName(plain, n) && !looksLikeChat(plain, n)) {
            name = n;
            break;
         }
      }

      long now = System.currentTimeMillis();
      if (name != null && (toYou || here)) {
         t.addRequest(name, here);
      } else if (name != null && hint && !looksLikeChat(plain, name)) {
         // Anfrage und Hinweis in einer Zeile, aber unbekannter Wortlaut
         t.addRequest(name, lower.contains("tpahere") || lower.contains("to them") || lower.contains("zu sich"));
      } else if (name != null && (lower.contains("teleport") || lower.contains("tpa") || lower.contains("anfrage") || lower.contains("request"))) {
         // vielleicht eine Anfrage – erst zählen, wenn gleich danach der Annehm-Hinweis kommt
         pendingName = name;
         pendingHere = lower.contains("tpahere") || lower.contains("to them") || lower.contains("zu sich");
         pendingAt = now;
      } else if (name == null && hint && pendingName != null && now - pendingAt < 2000L) {
         t.addRequest(pendingName, pendingHere);
         pendingName = null;
      }
   }

   private static boolean containsAny(String s, String[] parts) {
      for (String p : parts) {
         if (s.contains(p)) {
            return true;
         }
      }

      return false;
   }

   /** „Name: Text“, „<Name> Text“, „Name » Text“ – eine normale Chat-Nachricht des Spielers. */
   private static boolean looksLikeChat(String plain, String name) {
      int i = plain.indexOf(name);
      if (i < 0) {
         return false;
      }

      String after = plain.substring(i + name.length()).stripLeading();
      return !after.isEmpty() && ":»>|›→-".indexOf(after.charAt(0)) >= 0 || i > 0 && plain.charAt(i - 1) == '<';
   }

   private void addRequest(String n, boolean here) {
      boolean isNew = !this.requests.containsKey(n);
      this.requests.put(n, System.currentTimeMillis());
      this.hereRequests.put(n, here);
      pendingName = null;
      if (isNew) {
         this.mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4F, 0.6F));
         ChatUtil.actionBar((here ? "§d" + n + " §dwill dich zu sich holen" : "§d" + n + " §dwill sich zu dir teleportieren") + " §7– [" + this.key.get().getDisplayName() + "] öffnen");
      }
   }
}