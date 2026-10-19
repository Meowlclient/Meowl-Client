package com.lennart.nebula.module.mods;

import com.lennart.nebula.util.MeowlCommands;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.gui.FriendsScreen;
import com.lennart.nebula.module.Category;
import com.lennart.nebula.module.Module;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.nametag.NametagPiece;
import com.lennart.nebula.nametag.TextPiece;
import com.lennart.nebula.net.MeowlService;
import com.lennart.nebula.setting.ActionSetting;
import com.lennart.nebula.setting.Bind;
import com.lennart.nebula.setting.BooleanSetting;
import com.lennart.nebula.setting.ColorSetting;
import com.lennart.nebula.setting.KeybindSetting;
import com.lennart.nebula.setting.Setting;
import com.lennart.nebula.setting.StringListSetting;
import com.lennart.nebula.util.ChatUtil;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

public class Friends extends Module {
   private static Friends instance;
   private final StringListSetting friends = this.add(
      new StringListSetting("Freunde", "Deine Freunde (Spielernamen). Wird gespeichert und von allen Modulen genutzt.", List.of())
         .normalizer(t -> t.trim().matches("[A-Za-z0-9_]{3,16}") ? t.trim() : null, "Spielername, z.B. Lennart")
   );
   private final BooleanSetting serverSync = this.add(
      new BooleanSetting(
         "Mit Meowl Server abgleichen", "Liste mit dem Meowl Server teilen (nötig für Friend Locator: dort zählen nur gegenseitige Freunde)", true
      )
   );
   private final BooleanSetting mark = this.add(new BooleanSetting("Am Namen markieren", "Kleiner Stern am Namensschild deiner Freunde", true));
   private final ColorSetting markColor = this.add(new ColorSetting("Markierungs-Farbe", "Farbe des Sterns am Namen", -11870592, false));
   private final KeybindSetting toggleKey = this.add(new KeybindSetting("Freund-Taste", "Angeschauten Spieler als Freund hinzufügen bzw. entfernen", Bind.NONE));
   private final Map<String, Boolean> mutual = new ConcurrentHashMap<>();
   private Set<String> lookup = Set.of();
   private List<String> lookupSource;
   private List<String> synced;
   private boolean refreshing;
   private int syncTimer;
   private boolean keyDown;
   private volatile List<Friends.Info> infos = List.of();
   private volatile List<String> incoming = List.of();
   private final Set<String> ignored = new HashSet<>();
   private volatile long statusTime;

   public Friends() {
      super("Freunde", "Deine zentrale Freundesliste: suchen, hinzufügen, verwalten – genutzt von Friend Locator, Nametags und mehr.", Category.MEOWL);
      this.setEnabled(true);
      this.section(Section.FRIENDS);
      instance = this;
      this.tool();
      this.icon(Items.TOTEM_OF_UNDYING);
      this.add(new ActionSetting("Freunde-Status", "Zeigt im Chat, wer gegenseitig befreundet und online ist", "Anzeigen", this::printFriends));
      this.group("Liste", new Setting[]{this.friends, this.serverSync});
      this.group("Anzeige", new Setting[]{this.mark, this.markColor, this.toggleKey});
      this.markColor.setVisibility(this.mark::get);
      this.friends.setVisibility(() -> false);
      ClientTickEvents.END_CLIENT_TICK.register(c -> this.tickAlways());
      MeowlService s = MeowlService.get();
      s.on(
         "friend_request",
         d -> {
            String name = d.get("name").getAsString();
            ChatUtil.chat(
               Component.literal("§5[§dMeowl§5]§r §f" + name + " §7möchte dein Meowl-Freund sein ")
                  .append(ChatUtil.button("[Annehmen]", 4906624, "/meowl friends add " + name, "Als Freund hinzufügen"))
            );
            this.synced = null;
         }
      );
      s.on("friend_added", d -> {
         ChatUtil.chat("§f" + d.get("name").getAsString() + " §aist jetzt dein Meowl-Freund!");
         this.synced = null;
      });
      s.onConnected(() -> this.synced = null);
      MeowlCommands.add("msg", "msg <name> §7– Chat mit einem Freund öffnen", root -> ClientCommands.literal(root)
         .then(ClientCommands.argument("name", StringArgumentType.word()).executes(ctx -> {
            String n = StringArgumentType.getString(ctx, "name");
            this.mc.schedule(() -> this.mc.gui.setScreen(new FriendsScreen(null, this).openChat(n)));
            return 1;
         })));
      MeowlCommands.add(
         "friends",
         "friends [add|remove|list] <name> §7– Freundesliste (kurz: friend)",
         root -> ClientCommands.literal(root).executes(ctx -> {
            this.mc.schedule(() -> this.openTool(null));
            return 1;
         }).then(ClientCommands.literal("list").executes(ctx -> {
            this.mc.schedule(this::printFriends);
            return 1;
         })).then(ClientCommands.literal("add").then(ClientCommands.argument("name", StringArgumentType.word()).executes(ctx -> {
            String n = StringArgumentType.getString(ctx, "name");
            this.mc.schedule(() -> this.add(n, true));
            return 1;
         }))).then(ClientCommands.literal("remove").then(ClientCommands.argument("name", StringArgumentType.word()).executes(ctx -> {
            String n = StringArgumentType.getString(ctx, "name");
            this.mc.schedule(() -> this.remove(n, true));
            return 1;
         }))),
         "friend"
      );
   }

   public static Friends get() {
      return instance;
   }

   @Override
   public Screen createScreen(Screen parent) {
      return new FriendsScreen(parent, this);
   }

   public List<Friends.Info> infos() {
      return this.infos;
   }

   public List<String> incoming() {
      List<String> out = new ArrayList<>();

      for (String n : this.incoming) {
         if (!this.ignored.contains(n.toLowerCase(Locale.ROOT)) && !isFriend(n)) {
            out.add(n);
         }
      }

      return out;
   }

   public void ignore(String name) {
      this.ignored.add(name.toLowerCase(Locale.ROOT));
   }

   public long statusTime() {
      return this.statusTime;
   }

   public boolean serverSync() {
      return this.serverSync.get();
   }

   public void setServerSync(boolean on) {
      this.serverSync.set(on);
      this.synced = null;
      NebulaClient.getInstance().getModuleManager().notifyChanged();
   }

   public ColorSetting markColorSetting() {
      return this.markColor;
   }

   public BooleanSetting markSetting() {
      return this.mark;
   }

   public boolean marking() {
      return this.mark.get();
   }

   public void setMarking(boolean on) {
      this.mark.set(on);
      NebulaClient.getInstance().getModuleManager().notifyChanged();
   }

   public String toggleKeyName() {
      return this.toggleKey.get().isNone() ? null : this.toggleKey.get().getDisplayName();
   }

   public void refreshStatus() {
      if (this.serverSync.get()) {
         this.refresh();
      }
   }

   public static List<String> names() {
      return instance == null ? List.of() : instance.friends.get();
   }

   public static boolean isFriend(String name) {
      return instance != null && name != null && instance.lowerSet().contains(name.toLowerCase(Locale.ROOT));
   }

   public static boolean isFriend(Player player) {
      return player != null && isFriend(player.getGameProfile().name());
   }

   public static Boolean isMutual(String name) {
      return instance != null && name != null ? instance.mutual.get(name.toLowerCase(Locale.ROOT)) : null;
   }

   public static NametagPiece namePiece(Player player) {
      Friends f = instance;
      return f != null && f.isEnabled() && f.mark.get() && isFriend(player) ? TextPiece.of("★", f.markColor.get()) : null;
   }

   private Set<String> lowerSet() {
      List<String> now = this.friends.get();
      if (now != this.lookupSource) {
         Set<String> set = new HashSet<>();
         now.forEach(n -> set.add(n.toLowerCase(Locale.ROOT)));
         this.lookup = set;
         this.lookupSource = now;
      }

      return this.lookup;
   }

   public void add(String name, boolean feedback) {
      String n = this.friends.normalize(name);
      if (n == null) {
         if (feedback) {
            ChatUtil.chat("§cUngültiger Spielername: " + name);
         }
      } else if (isFriend(n)) {
         if (feedback) {
            ChatUtil.chat("§7" + n + " ist schon in deiner Freundesliste");
         }

         this.synced = null;
      } else {
         this.friends.addEntry(n);
         NebulaClient.getInstance().getModuleManager().notifyChanged();
         if (feedback) {
            ChatUtil.chat("§a" + n + " §7zur Freundesliste hinzugefügt");
         }
      }
   }

   public void remove(String name, boolean feedback) {
      String found = null;

      for (String f : this.friends.get()) {
         if (f.equalsIgnoreCase(name)) {
            found = f;
         }
      }

      if (found != null) {
         this.friends.removeEntry(found);
         String gone = found;
         this.infos = this.infos.stream().filter(i -> !i.name().equalsIgnoreCase(gone)).toList();
         NebulaClient.getInstance().getModuleManager().notifyChanged();
         if (feedback) {
            ChatUtil.chat("§7" + found + " aus der Freundesliste entfernt");
         }
      } else if (feedback) {
         ChatUtil.chat("§7" + name + " ist nicht in deiner Freundesliste");
      }
   }

   @Override
   public void onConfigLoaded() {
      FriendLocator locator = NebulaClient.getInstance().getModuleManager().get(FriendLocator.class);
      if (locator != null) {
         List<String> old = locator.takeLegacyFriends();
         if (!old.isEmpty()) {
            List<String> merged = new ArrayList<>(this.friends.get());

            for (String n : old) {
               if (merged.stream().noneMatch(m -> m.equalsIgnoreCase(n))) {
                  merged.add(n);
               }
            }

            this.friends.set(merged);
            NebulaClient.LOGGER.info("Freundesliste aus dem Friend Locator übernommen ({} Namen)", old.size());
         }
      }
   }

   @Override
   public void onTick() {
      boolean down = this.toggleKey.get().isPressed();
      if (down && !this.keyDown && this.mc.gui.screen() == null && this.mc.crosshairPickEntity instanceof Player p) {
         String n = p.getGameProfile().name();
         if (isFriend(n)) {
            this.remove(n, true);
         } else {
            this.add(n, true);
         }
      }

      this.keyDown = down;
   }

   private void tickAlways() {
      if (this.mc.player != null && ++this.syncTimer >= 40) {
         this.syncTimer = 0;
         if (this.serverSync.get()) {
            this.syncList();
         }
      }
   }

   private void refresh() {
      if (!this.refreshing && MeowlService.get().isConnected()) {
         this.refreshing = true;
         MeowlService.get()
            .get("/friends")
            .whenComplete(
               (r, e) -> this.mc
                  .execute(
                     () -> {
                        this.refreshing = false;
                        if (e == null && r.ok() && r.json() != null) {
                           Set<String> server = new HashSet<>();
                           this.mutual.clear();
                           List<Friends.Info> list = new ArrayList<>();

                           for (JsonElement el : r.json().getAsJsonArray("friends")) {
                              JsonObject f = el.getAsJsonObject();
                              String n = f.get("name").getAsString();
                              server.add(n.toLowerCase(Locale.ROOT));
                              this.mutual.put(n.toLowerCase(Locale.ROOT), f.get("mutual").getAsBoolean());
                              list.add(
                                 new Friends.Info(
                                    n,
                                    f.has("uuid") ? f.get("uuid").getAsString() : null,
                                    f.get("mutual").getAsBoolean(),
                                    f.has("online") && f.get("online").getAsBoolean(),
                                    f.has("sameServer") && f.get("sameServer").getAsBoolean()
                                 )
                              );
                           }

                           List<String> inc = new ArrayList<>();
                           if (r.json().has("incoming")) {
                              for (JsonElement el : r.json().getAsJsonArray("incoming")) {
                                 inc.add(el.getAsJsonObject().get("name").getAsString());
                              }
                           }

                           this.infos = List.copyOf(list);
                           this.incoming = List.copyOf(inc);
                           this.statusTime = System.currentTimeMillis();
                           List<String> merged = new ArrayList<>(this.friends.get());

                           for (JsonElement el : r.json().getAsJsonArray("friends")) {
                              String n = el.getAsJsonObject().get("name").getAsString();
                              if (merged.stream().noneMatch(m -> m.equalsIgnoreCase(n))) {
                                 merged.add(n);
                              }
                           }

                           if (!merged.equals(this.friends.get())) {
                              this.friends.set(merged);
                              NebulaClient.getInstance().getModuleManager().notifyChanged();
                           }

                           List<String> base = new ArrayList<>();

                           for (String n : this.friends.get()) {
                              if (server.contains(n.toLowerCase(Locale.ROOT))) {
                                 base.add(n);
                              }
                           }

                           this.synced = base;
                        }
                     }
                  )
            );
      }
   }

   private void serverAdd(String name) {
      JsonObject o = new JsonObject();
      o.addProperty("name", name);
      MeowlService.get().post("/friends/add", o).whenComplete((r, e) -> this.mc.execute(() -> {
         if (e == null && r.ok() && r.json() != null && r.json().has("mutual")) {
            boolean m = r.json().get("mutual").getAsBoolean();
            this.mutual.put(name.toLowerCase(Locale.ROOT), m);
            if (m) {
               ChatUtil.chat("§f" + name + " §aist jetzt dein Meowl-Freund!");
            }
         }
      }));
   }

   private void serverRemove(String name) {
      JsonObject o = new JsonObject();
      o.addProperty("name", name);
      MeowlService.get().post("/friends/remove", o);
      this.mutual.remove(name.toLowerCase(Locale.ROOT));
   }

   private void syncList() {
      if (MeowlService.get().isConnected()) {
         if (this.synced == null) {
            this.refresh();
         } else {
            List<String> now = this.friends.get();
            if (!now.equals(this.synced)) {
               Set<String> before = new HashSet<>();
               this.synced.forEach(nx -> before.add(nx.toLowerCase(Locale.ROOT)));
               Set<String> after = new HashSet<>();
               now.forEach(nx -> after.add(nx.toLowerCase(Locale.ROOT)));

               for (String n : now) {
                  if (!before.contains(n.toLowerCase(Locale.ROOT))) {
                     this.serverAdd(n);
                  }
               }

               for (String nx : this.synced) {
                  if (!after.contains(nx.toLowerCase(Locale.ROOT))) {
                     this.serverRemove(nx);
                  }
               }

               this.synced = List.copyOf(now);
            }
         }
      }
   }

   public void printFriends() {
      List<String> list = this.friends.get();
      if (list.isEmpty()) {
         ChatUtil.chat("§7Noch keine Freunde – füge welche im Modul „Freunde“ oder mit §f/meowl friends add <Name>§7 hinzu.");
      } else if (MeowlService.get().isConnected() && this.serverSync.get()) {
         MeowlService.get()
            .get("/friends")
            .whenComplete(
               (r, e) -> this.mc
                  .execute(
                     () -> {
                        if (e == null && r.ok()) {
                           ChatUtil.chat("§dFreunde:");

                           for (JsonElement el : r.json().getAsJsonArray("friends")) {
                              JsonObject f = el.getAsJsonObject();
                              boolean m = f.get("mutual").getAsBoolean();
                              boolean on = f.get("online").getAsBoolean();
                              boolean same = f.get("sameServer").getAsBoolean();
                              ChatUtil.chat(
                                 "§f"
                                    + f.get("name").getAsString()
                                    + " §7– "
                                    + (m ? "§a✔ befreundet" : "§e⏳ wartet auf Bestätigung")
                                    + (on ? (same ? " §b· hier online" : " §7· online") : " §8· offline")
                              );
                           }

                           for (JsonElement el : r.json().getAsJsonArray("incoming")) {
                              String n = el.getAsJsonObject().get("name").getAsString();
                              ChatUtil.chat(
                                 Component.literal("§5[§dMeowl§5]§r §f" + n + " §7will befreundet sein ")
                                    .append(ChatUtil.button("[Annehmen]", 4906624, "/meowl friends add " + n, "Als Freund hinzufügen"))
                              );
                           }
                        } else {
                           ChatUtil.chat("§dFreunde §7(" + list.size() + "): §f" + String.join("§7, §f", list));
                        }
                     }
                  )
            );
      } else {
         ChatUtil.chat("§dFreunde §7(" + list.size() + "): §f" + String.join("§7, §f", list));
      }
   }

   public record Info(String name, String uuid, boolean mutual, boolean online, boolean sameServer) {
   }
}
