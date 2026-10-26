package com.lennart.nebula.gui;

import com.mojang.blaze3d.platform.InputConstants;

import com.lennart.nebula.NebulaClient;
import com.lennart.nebula.gui.widget.MeowlTextField;
import com.lennart.nebula.module.Section;
import com.lennart.nebula.images.ImageCache;
import com.lennart.nebula.module.mods.BlockImages;
import com.lennart.nebula.module.mods.FriendLocator;
import com.lennart.nebula.module.mods.Friends;
import com.lennart.nebula.module.mods.MeowlPet;
import com.lennart.nebula.module.mods.Tiers;
import com.lennart.nebula.net.FriendChat;
import com.lennart.nebula.net.MeowlService;
import com.lennart.nebula.util.DynamicTextures;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;

public class FriendsScreen extends com.lennart.nebula.compat.MeowlBaseScreen {
   private static final int ROW_H = 26;
   private static final int ACCENT = Section.MEOWL.color();
   private static final int ONLINE_HERE = -11870592;
   private static final int ONLINE = -11549697;
   private static final int PENDING = -278748;
   private static final int OFFLINE = -9735552;
   private final Screen parent;
   private final Friends friends;
   private final MeowlTextField search = new MeowlTextField(16).placeholder("Name suchen oder hinzufügen …");
   private final List<FriendsScreen.Hit> hits = new ArrayList<>();
   private FriendsScreen.Tab tab = FriendsScreen.Tab.ALL;
   private String selected;
   private String confirmRemove;
   private int scroll;
   private long lastRefresh;
   private String toast = "";
   private long toastUntil;
   private boolean chatView = true;
   private final MeowlTextField chatInput = new MeowlTextField(300).placeholder("Nachricht schreiben … (Enter)");
   private String loadedFor;
   private String chatStatus = "";
   private int chatScroll;
   private int[] chatArea;
   private int picker;
   private List<Path> shots = List.of();
   private final Map<Path, FriendsScreen.Thumb> thumbs = new HashMap<>();
   private String bigImage;
   private int overlayStart;

   public FriendsScreen(Screen parent, Friends friends) {
      super(Component.literal("Freunde"));
      this.parent = parent;
      this.friends = friends;
      this.search.onSubmit(s -> this.addFromSearch());
      this.chatInput.onSubmit(s -> this.sendTyped());
   }

   /** Öffnet direkt den Chat mit einem Freund. */
   public FriendsScreen openChat(String name) {
      this.selected = name;
      this.chatView = true;
      return this;
   }

   @Override
   protected void init() {
      this.friends.refreshStatus();
      this.lastRefresh = System.currentTimeMillis();
      FriendChat.get().refreshUnread();
   }

   @Override
   public void removed() {
      FriendChat.get().setOpen(null);

      for (FriendsScreen.Thumb t : this.thumbs.values()) {
         if (t.uploaded) {
            DynamicTextures.destroy(t.id);
         }
      }

      this.thumbs.clear();
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void onClose() {
      this.minecraft.gui.setScreen(this.parent);
   }

   @Override
   public void tick() {
      if (System.currentTimeMillis() - this.lastRefresh > 8000L) {
         this.lastRefresh = System.currentTimeMillis();
         this.friends.refreshStatus();
      }
   }

   private Friends.Info info(String name) {
      for (Friends.Info i : this.friends.infos()) {
         if (i.name().equalsIgnoreCase(name)) {
            return i;
         }
      }

      return null;
   }

   private PlayerInfo tabEntry(String name) {
      if (this.minecraft.getConnection() == null) {
         return null;
      } else {
         for (PlayerInfo e : this.minecraft.getConnection().getOnlinePlayers()) {
            if (e.getProfile().name().equalsIgnoreCase(name)) {
               return e;
            }
         }

         return null;
      }
   }

   private Player nearby(String name) {
      if (this.minecraft.level == null) {
         return null;
      } else {
         for (Player p : this.minecraft.level.players()) {
            if (p.getGameProfile().name().equalsIgnoreCase(name)) {
               return p;
            }
         }

         return null;
      }
   }

   private List<FriendsScreen.Entry> entries() {
      String q = this.search.getText().trim().toLowerCase(Locale.ROOT);
      List<FriendsScreen.Entry> out = new ArrayList<>();
      if (this.tab == FriendsScreen.Tab.REQUESTS) {
         for (String n : this.friends.incoming()) {
            if (q.isEmpty() || n.toLowerCase(Locale.ROOT).contains(q)) {
               out.add(new FriendsScreen.Entry(n, null, FriendsScreen.Kind.REQUEST));
            }
         }

         return out;
      } else {
         List<FriendsScreen.Entry> online = new ArrayList<>();
         List<FriendsScreen.Entry> rest = new ArrayList<>();

         for (String nx : Friends.names()) {
            if (q.isEmpty() || nx.toLowerCase(Locale.ROOT).contains(q)) {
               Friends.Info i = this.info(nx);
               boolean on = i != null && i.online() || this.tabEntry(nx) != null;
               if (this.tab != FriendsScreen.Tab.ONLINE || on) {
                  (on ? online : rest).add(new FriendsScreen.Entry(nx, i, FriendsScreen.Kind.FRIEND));
               }
            }
         }

         online.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
         rest.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
         out.addAll(online);
         out.addAll(rest);
         if (!q.isEmpty() && this.minecraft.getConnection() != null) {
            int nxx = 0;

            for (PlayerInfo e : this.minecraft.getConnection().getOnlinePlayers()) {
               String name = e.getProfile().name();
               if (nxx < 6
                  && name.toLowerCase(Locale.ROOT).contains(q)
                  && !Friends.isFriend(name)
                  && (this.minecraft.player == null || !name.equals(this.minecraft.player.getGameProfile().name()))) {
                  out.add(new FriendsScreen.Entry(name, null, FriendsScreen.Kind.SUGGESTION));
                  nxx++;
               }
            }
         }

         return out;
      }
   }

   private String[] status(String name, Friends.Info i) {
      boolean here = this.tabEntry(name) != null;
      if (i != null) {
         if (!i.mutual()) {
            return new String[]{"Wartet auf Bestätigung", hex(-278748)};
         } else if (i.sameServer() || here) {
            return new String[]{"Online · hier", hex(-11870592)};
         } else {
            return i.online() ? new String[]{"Online · anderer Server", hex(-11549697)} : new String[]{"Offline", hex(-9735552)};
         }
      } else {
         return here
            ? new String[]{"Auf diesem Server", hex(-11870592)}
            : new String[]{MeowlService.get().isConnected() && this.friends.serverSync() ? "Unbekannt beim Meowl Server" : "Lokal gespeichert", hex(-9735552)};
      }
   }

   private static String hex(int c) {
      return Integer.toString(c);
   }

   private PlayerSkin skin(String name, Friends.Info i) {
      PlayerInfo e = this.tabEntry(name);
      if (e != null) {
         return e.getSkin();
      } else {
         // nicht auf diesem Server: echten Skin über Mojang laden (zwischengespeichert), bis dahin Standard-Skin
         if (com.lennart.nebula.module.mods.pet.SkinPet.validName(name)) {
            PlayerSkin loaded = com.lennart.nebula.module.mods.pet.SkinPet.textures(name);
            if (loaded != null) {
               return loaded;
            }
         }

         UUID uuid = null;
         if (i != null && i.uuid() != null) {
            try {
               uuid = UUID.fromString(i.uuid());
            } catch (IllegalArgumentException var6) {
            }
         }

         if (uuid == null) {
            uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
         }

         return DefaultPlayerSkin.get(uuid);
      }
   }

   private void addFromSearch() {
      String n = this.search.getText().trim();
      if (!n.isEmpty()) {
         if (!n.matches("[A-Za-z0-9_]{3,16}")) {
            this.toast("§cUngültiger Spielername");
         } else if (Friends.isFriend(n)) {
            this.selected = n;
            this.toast("§7" + n + " ist schon dein Freund");
         } else {
            this.friends.add(n, false);
            this.selected = n;
            this.toast("§a" + n + " hinzugefügt" + (this.friends.serverSync() ? " – Anfrage gesendet" : ""));
         }

         this.search.setText("");
      }
   }

   private void toast(String text) {
      this.toast = text;
      this.toastUntil = System.currentTimeMillis() + 3500L;
   }

   private void button(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, int bg, int fg, int mouseX, int mouseY, Runnable action) {
      boolean hov = Render2D.inside(mouseX, mouseY, x, y, w, h);
      Render2D.roundRect(ctx, x, y, w, h, 5, hov ? Theme.lerp(bg, -1, 0.12F) : bg);
      ctx.text(this.font, label, x + (w - this.font.width(label)) / 2, y + (h - 8) / 2, fg, false);
      this.hits.add(new FriendsScreen.Hit(x, y, w, h, action));
   }

   @Override
   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
      this.hits.clear();
      context.fill(0, 0, this.width, this.height, -1476395008);
      int pw = Math.min(500, this.width - 16);
      int ph = Math.min(320, this.height - 16);
      int px = (this.width - pw) / 2;
      int py = (this.height - ph) / 2;
      Render2D.glow(context, px, py, pw, ph, 10, ACCENT, 0.4F);
      Render2D.roundRect(context, px, py, pw, ph, 10, -15855591);
      Render2D.roundRect(context, px, py, pw, 30, 10, -15065296);
      Render2D.rect(context, px, py + 29, pw, 1, -14275777);
      context.text(this.font, Component.literal("♥ Freunde").withStyle(ChatFormatting.BOLD), px + 12, py + 11, ACCENT, false);
      int onlineCount = 0;

      for (String n : Friends.names()) {
         Friends.Info i = this.info(n);
         if (i != null && i.online() || this.tabEntry(n) != null) {
            onlineCount++;
         }
      }

      context.text(this.font, Friends.names().size() + " Freunde · " + onlineCount + " online", px + 80, py + 11, -7564628, false);
      boolean connected = MeowlService.get().isConnected();
      String conn = !this.friends.serverSync() ? "● nur lokal" : (connected ? "● Meowl Server" : "● nicht verbunden");
      int connColor = !this.friends.serverSync() ? -9735552 : (connected ? -11870592 : -495247);
      int cw = this.font.width(conn);
      context.text(this.font, conn, px + pw - 40 - cw, py + 11, connColor, false);
      this.button(context, px + pw - 30, py + 7, 18, 16, "✕", -14012603, -1578509, mouseX, mouseY, this::onClose);
      int leftW = Math.max(170, (int)(pw * 0.46F));
      int lx = px + 10;
      int top = py + 38;
      int bottomBar = py + ph - 30;
      this.search.setBounds(lx, top, leftW - 46, 18);
      this.search.render(context, this.font, false);
      this.button(context, lx + leftW - 42, top, 42, 18, "+ Add", ACCENT, -1, mouseX, mouseY, this::addFromSearch);
      int ty = top + 24;
      int tx = lx;

      for (FriendsScreen.Tab t : FriendsScreen.Tab.values()) {
         int count = t == FriendsScreen.Tab.ALL ? Friends.names().size() : (t == FriendsScreen.Tab.ONLINE ? onlineCount : this.friends.incoming().size());
         String label = t.label + " " + count;
         int w = this.font.width(label) + 14;
         boolean active = this.tab == t;
         int bg = active ? ACCENT : -15394521;
         if (t == FriendsScreen.Tab.REQUESTS && count > 0 && !active) {
            bg = -12964334;
         }

         this.button(
            context, tx, ty, w, 15, label, bg, active ? -1 : (t == FriendsScreen.Tab.REQUESTS && count > 0 ? -278748 : -7564628), mouseX, mouseY, () -> {
               this.tab = t;
               this.scroll = 0;
            }
         );
         tx += w + 4;
      }

      int listTop = ty + 21;
      int listH = bottomBar - 6 - listTop;
      List<FriendsScreen.Entry> list = this.entries();
      this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, list.size() * 26 - listH)));
      Render2D.roundRect(context, lx - 2, listTop - 2, leftW + 4, listH + 4, 6, -16118764);
      context.enableScissor(lx - 2, listTop, lx + leftW + 2, listTop + listH);
      if (list.isEmpty()) {
         String empty = switch (this.tab) {
            case ONLINE -> "Gerade ist kein Freund online";
            case REQUESTS -> "Keine offenen Anfragen";
            default -> this.search.getText().isBlank() ? "Noch keine Freunde – Namen oben eingeben" : "Niemand gefunden – Enter fügt hinzu";
         };
         context.text(this.font, empty, lx + 8, listTop + 10, -10656900, false);
      }

      boolean suggestionHeader = false;

      for (int idx = 0; idx < list.size(); idx++) {
         FriendsScreen.Entry e = list.get(idx);
         int ry = listTop + idx * 26 - this.scroll;
         if (ry + 26 >= listTop && ry <= listTop + listH) {
            boolean inside = mouseY >= listTop && mouseY <= listTop + listH;
            boolean hov = inside && Render2D.inside(mouseX, mouseY, lx, ry, leftW, 23);
            boolean sel = e.kind() == FriendsScreen.Kind.FRIEND && e.name().equalsIgnoreCase(this.selected);
            int bg = sel ? -14016190 : (hov ? -14407358 : -15394521);
            Render2D.roundRect(context, lx, ry, leftW, 23, 6, bg);
            if (sel) {
               Render2D.roundRect(context, lx, ry + 4, 2, 15, 1, ACCENT);
            }

            PlayerFaceExtractor.extractRenderState(context, this.skin(e.name(), e.info()), lx + 5, ry + 3, 17);
            context.text(this.font, e.name(), lx + 28, ry + 4, -1578509, false);
            int bx = lx + leftW - 6;
            switch (e.kind()) {
               case FRIEND:
                  String[] st = this.status(e.name(), e.info());
                  int c = Integer.parseInt(st[1]);
                  context.text(this.font, st[0], lx + 28, ry + 13, Render2D.alpha(c, 0.85F), false);
                  Render2D.circle(context, bx - 4, ry + 10, 3, c);
                  int unread = FriendChat.get().unread(e.name());
                  if (unread > 0) {
                     String u = unread > 9 ? "9+" : String.valueOf(unread);
                     int uw = this.font.width(u) + 8;
                     Render2D.roundRect(context, bx - 14 - uw, ry + 5, uw, 12, 6, ACCENT);
                     context.text(this.font, u, bx - 10 - uw, ry + 7, -1, false);
                  }
                  if (inside) {
                     this.hits.add(new FriendsScreen.Hit(lx, ry, leftW, 23, () -> {
                        this.selected = e.name();
                        this.confirmRemove = null;
                     }));
                  }
                  break;
               case REQUEST:
                  context.text(this.font, "möchte dein Freund sein", lx + 28, ry + 13, -278748, false);
                  if (inside) {
                     this.button(context, bx - 52, ry + 4, 24, 15, "✔", -15445203, -11870592, mouseX, mouseY, () -> {
                        this.friends.add(e.name(), false);
                        this.selected = e.name();
                        this.toast("§a" + e.name() + " ist jetzt dein Freund");
                     });
                     this.button(context, bx - 24, ry + 4, 24, 15, "✕", -12968155, -495247, mouseX, mouseY, () -> this.friends.ignore(e.name()));
                  }
                  break;
               case SUGGESTION:
                  if (!suggestionHeader) {
                     suggestionHeader = true;
                  }

                  context.text(this.font, "Auf diesem Server", lx + 28, ry + 13, -10656900, false);
                  if (inside) {
                     this.button(context, bx - 40, ry + 4, 40, 15, "+ Add", ACCENT, -1, mouseX, mouseY, () -> {
                        this.friends.add(e.name(), false);
                        this.selected = e.name();
                        this.search.setText("");
                        this.toast("§a" + e.name() + " hinzugefügt");
                     });
                  }
            }
         }
      }

      context.disableScissor();
      String key = this.friends.toggleKeyName();
      int rx = lx + leftW + 12;
      int rw = px + pw - 10 - rx;
      Render2D.roundRect(context, rx, top, rw, bottomBar - 6 - top + 2, 8, -15394521);
      if (this.selected != null && Friends.isFriend(this.selected)) {
         this.renderDetails(context, rx, top, rw, bottomBar - 6 - top, mouseX, mouseY);
      } else {
         this.selected = null;
         int y = top + 14;
         context.text(this.font, Component.literal("Freund auswählen").withStyle(ChatFormatting.BOLD), rx + 12, y, -1578509, false);
         y += 16;

         for (String line : new String[]{
            "• Namen oben eingeben → Enter fügt hinzu",
            "• Beim Tippen erscheinen Spieler dieses Servers",
            "• Gegenseitig befreundet = Standort teilen möglich",
            "• Freunde bekommen einen ★ am Namensschild",
            key != null ? "• Taste " + key + ": angeschauten Spieler (ent)freunden" : "• Freund-Taste unter ⚙ Mehr festlegen"
         }) {
            context.text(this.font, this.font.plainSubstrByWidth(line, rw - 20), rx + 12, y, -7564628, false);
            y += 12;
         }
      }

      Render2D.rect(context, px + 10, bottomBar - 2, pw - 20, 1, -14802381);
      int fy = bottomBar + 6;
      int fx = px + 12;
      Render2D.toggleSwitch(context, fx, fy - 1, this.friends.marking() ? 1.0F : 0.0F);
      context.text(this.font, "★ am Namen", fx + 30, fy + 2, -7564628, false);
      this.hits
         .add(new FriendsScreen.Hit(fx, fy - 2, 30 + this.font.width("★ am Namen"), 16, () -> this.friends.setMarking(!this.friends.marking())));
      fx += 34 + this.font.width("★ am Namen") + 14;
      Render2D.toggleSwitch(context, fx, fy - 1, this.friends.serverSync() ? 1.0F : 0.0F);
      context.text(this.font, "Mit Meowl Server abgleichen", fx + 30, fy + 2, -7564628, false);
      int syncW = 30 + this.font.width("Mit Meowl Server abgleichen");
      this.hits.add(new FriendsScreen.Hit(fx, fy - 2, syncW, 16, () -> this.friends.setServerSync(!this.friends.serverSync())));
      this.button(context, px + pw - 92, fy - 2, 80, 16, "⚙ Mehr", -14012603, -1578509, mouseX, mouseY, () -> {
         if (this.parent instanceof MeowlScreen ms) {
            this.minecraft.gui.setScreen(ms);
            ms.openSettings(this.friends);
         } else {
            MeowlScreen ms = new MeowlScreen(NebulaClient.getInstance().getModuleManager(), NebulaClient.getInstance().getConfigManager());
            this.minecraft.gui.setScreen(ms);
            ms.openSettings(this.friends);
         }
      });
      this.overlayStart = this.hits.size();
      if (this.bigImage != null) {
         this.renderBigImage(context, mouseX, mouseY);
      }

      if (System.currentTimeMillis() < this.toastUntil) {
         int tw = this.font.width(this.toast) + 16;
         int tx0 = px + (pw - tw) / 2;
         Render2D.roundRect(context, tx0, py + 7, tw, 16, 8, -265934017);
         context.text(this.font, this.toast, tx0 + 8, py + 11, -1578509, false);
      }
   }

   private void renderDetails(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
      String name = this.selected;
      Friends.Info i = this.info(name);
      PlayerFaceExtractor.extractRenderState(ctx, this.skin(name, i), x + 10, y + 8, 26);
      ctx.text(this.font, Component.literal(name).withStyle(ChatFormatting.BOLD), x + 42, y + 10, -1578509, false);
      String[] st = this.status(name, i);
      int c = Integer.parseInt(st[1]);
      Render2D.circle(ctx, x + 45, y + 26, 3, c);
      ctx.text(this.font, st[0], x + 52, y + 22, c, false);
      // Reiter Chat / Profil
      int tx = x + w - 10;

      for (boolean chatTab : new boolean[]{false, true}) {
         String label = chatTab ? "Chat" : "Profil";
         int unread = chatTab && !this.chatView ? FriendChat.get().unread(name) : 0;
         String full = unread > 0 ? label + " " + unread : label;
         int bw = this.font.width(full) + 12;
         tx -= bw;
         boolean active = chatTab == this.chatView;
         this.button(ctx, tx, y + 10, bw, 15, full, active ? ACCENT : (unread > 0 ? -12964334 : -15065296), active ? -1 : -7564628, mouseX, mouseY, () -> {
            this.chatView = chatTab;
            this.picker = 0;
         });
         tx -= 4;
      }

      Render2D.rect(ctx, x + 8, y + 38, w - 16, 1, -14802381);
      if (this.chatView) {
         FriendChat.get().setOpen(name);
         this.renderChat(ctx, x, y + 42, w, h - 42, mouseX, mouseY, i);
      } else {
         FriendChat.get().setOpen(null);
         this.renderProfile(ctx, x, y, w, h, mouseX, mouseY, i);
      }
   }

   // ------------------------------------------------------------------ Chat

   private void renderChat(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int mouseX, int mouseY, Friends.Info info) {
      String name = this.selected;
      FriendChat chat = FriendChat.get();
      int inputY = y + h - 20;
      int areaTop = y + 2;
      int areaBottom = inputY - 4;
      this.chatArea = new int[]{x + 4, areaTop, w - 8, areaBottom - areaTop};
      if (info == null || !info.mutual()) {
         this.centered(ctx, "Schreiben geht, sobald ihr gegenseitig befreundet seid", x, w, y + h / 2 - 4, -10656900);
         return;
      } else if (!MeowlService.get().isConnected()) {
         this.centered(ctx, "Nicht mit dem Meowl Server verbunden", x, w, y + h / 2 - 4, -10656900);
         return;
      }

      if (!name.equalsIgnoreCase(this.loadedFor)) {
         this.loadedFor = name;
         this.chatScroll = 0;
         this.chatStatus = "";
         chat.loadHistory(name, err -> this.chatStatus = "§c" + err);
      }

      if (this.picker != 0) {
         this.renderPicker(ctx, x + 4, areaTop, w - 8, areaBottom - areaTop, mouseX, mouseY);
      } else {
         this.renderMessages(ctx, x + 4, areaTop, w - 8, areaBottom - areaTop, mouseX, mouseY, chat, name);
      }

      // Eingabe + Bild-Knöpfe
      int bw = 40;
      this.chatInput.setBounds(x + 6, inputY, w - 12 - bw * 2 - 8, 16);
      this.chatInput.render(ctx, this.font, false);
      this.button(ctx, x + w - 6 - bw * 2 - 4, inputY, bw, 16, "Bild", this.picker == 2 ? ACCENT : -14012603, -1578509, mouseX, mouseY, () -> this.openPicker(2));
      this.button(ctx, x + w - 6 - bw, inputY, bw, 16, "Shot", this.picker == 1 ? ACCENT : -14012603, -1578509, mouseX, mouseY, () -> this.openPicker(1));
      if (!this.chatStatus.isEmpty()) {
         int sw = this.font.width(this.chatStatus) + 10;
         Render2D.roundRect(ctx, x + (w - sw) / 2, areaBottom - 14, sw, 12, 6, -265934017);
         ctx.text(this.font, this.chatStatus, x + (w - sw) / 2 + 5, areaBottom - 12, -1578509, false);
      }
   }

   private void centered(GuiGraphicsExtractor ctx, String text, int x, int w, int y, int color) {
      String t = this.font.plainSubstrByWidth(text, w - 12);
      ctx.text(this.font, t, x + (w - this.font.width(t)) / 2, y, color, false);
   }

   private void renderMessages(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int mouseX, int mouseY, FriendChat chat, String name) {
      List<FriendChat.Msg> msgs = chat.messages(name);
      if (!chat.loaded(name)) {
         this.centered(ctx, "Lade …", x, w, y + h / 2 - 4, -10656900);
         return;
      } else if (msgs.isEmpty()) {
         this.centered(ctx, "Noch keine Nachrichten – sag Hallo!", x, w, y + h / 2 - 4, -10656900);
         return;
      }

      String me = this.minecraft.player != null ? this.minecraft.player.getGameProfile().name() : this.minecraft.getUser().getName();
      int maxBubble = Math.max(60, (int)(w * 0.72F));
      // Höhe aller Nachrichten berechnen (von unten nach oben zeichnen)
      List<int[]> heights = new ArrayList<>();
      int total = 0;

      for (FriendChat.Msg m : msgs) {
         int lines = m.text() == null ? 0 : this.font.split(Component.literal(m.text()), maxBubble - 10).size();
         int ih = m.image() == null ? 0 : this.thumbHeight(m.image(), Math.min(110, maxBubble - 10)) + 4;
         int bh = 6 + lines * 9 + ih + (lines > 0 && ih > 0 ? 2 : 0);
         heights.add(new int[]{bh, lines, ih});
         total += bh + 4;
      }

      this.chatScroll = Math.max(0, Math.min(this.chatScroll, Math.max(0, total - h + 4)));
      ctx.enableScissor(x, y, x + w, y + h);
      int cy = y + h - 2 + this.chatScroll;

      for (int idx = msgs.size() - 1; idx >= 0; idx--) {
         FriendChat.Msg m = msgs.get(idx);
         int[] hh = heights.get(idx);
         cy -= hh[0];
         if (cy + hh[0] >= y && cy <= y + h) {
            boolean mine = m.from().equalsIgnoreCase(me);
            int bw = 10;
            List<net.minecraft.util.FormattedCharSequence> lines = m.text() == null ? List.of() : this.font.split(Component.literal(m.text()), maxBubble - 10);

            for (net.minecraft.util.FormattedCharSequence l : lines) {
               bw = Math.max(bw, this.font.width(l) + 10);
            }

            int thumbW = m.image() == null ? 0 : Math.min(110, maxBubble - 10);
            if (m.image() != null) {
               bw = Math.max(bw, thumbW + 10);
            }

            int bx = mine ? x + w - bw - 2 : x + 2;
            Render2D.roundRect(ctx, bx, cy, bw, hh[0], 6, mine ? Render2D.alpha(ACCENT, 0.55F) : -14407358);
            int ty = cy + 4;

            for (net.minecraft.util.FormattedCharSequence l : lines) {
               ctx.text(this.font, l, bx + 5, ty, -1, false);
               ty += 9;
            }

            if (m.image() != null) {
               if (!lines.isEmpty()) {
                  ty += 2;
               }

               int th = hh[2] - 4;
               this.drawRemote(ctx, m.image(), bx + 5, ty, thumbW, th);
               if (Render2D.inside(mouseX, mouseY, bx + 5, ty, thumbW, th) && mouseY >= y && mouseY < y + h) {
                  ctx.fill(bx + 5, ty, bx + 5 + thumbW, ty + th, 0x22FFFFFF);
                  String url = m.image();
                  this.hits.add(new FriendsScreen.Hit(bx + 5, ty, thumbW, th, () -> this.bigImage = url));
               }
            }
         }

         cy -= 4;
      }

      ctx.disableScissor();
   }

   private int thumbHeight(String url, int w) {
      ImageCache.Entry e = FriendChat.get().images().get(url);
      if (e.state() == ImageCache.State.READY && e.width() > 0) {
         return Math.max(16, Math.min(90, Math.round((float)w * e.height() / e.width())));
      } else {
         return 50;
      }
   }

   /** Bild aus dem Netz (Cache) zeichnen – oder Platzhalter, solange es lädt. */
   private void drawRemote(GuiGraphicsExtractor ctx, String url, int x, int y, int w, int h) {
      ImageCache.Entry e = FriendChat.get().images().get(url);
      if (e.state() == ImageCache.State.READY && e.texture() != null) {
         ctx.blit(RenderPipelines.GUI_TEXTURED, e.texture(), x, y, 0.0F, 0.0F, w, h, e.width(), e.height(), e.width(), e.height());
      } else {
         Render2D.roundRect(ctx, x, y, w, h, 3, -15855591);
         String t = e.state() == ImageCache.State.FAILED ? "Bild nicht ladbar" : "lädt …";
         ctx.text(this.font, t, x + (w - this.font.width(t)) / 2, y + h / 2 - 4, -10656900, false);
      }
   }

   private void openPicker(int kind) {
      this.picker = this.picker == kind ? 0 : kind;
      if (this.picker == 1) {
         this.shots = FriendChat.screenshots();
      }
   }

   /** Auswahl: 1 = Minecraft-Screenshots, 2 = Bilder aus Block Images. */
   private void renderPicker(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int mouseX, int mouseY) {
      Render2D.roundRect(ctx, x, y, w, h, 6, -16118764);
      ctx.text(this.font, Component.literal(this.picker == 1 ? "Screenshot senden" : "Block Image senden").withStyle(ChatFormatting.BOLD), x + 6, y + 5, ACCENT, false);
      int cols = Math.max(2, (w - 8) / 70);
      int cw = (w - 8 - (cols - 1) * 4) / cols;
      int ch = cw * 9 / 16;
      int gy = y + 18;
      List<Object[]> items = new ArrayList<>();
      if (this.picker == 1) {
         for (Path p : this.shots) {
            items.add(new Object[]{p, null});
         }
      } else {
         BlockImages bi = NebulaClient.getInstance().getModuleManager().get(BlockImages.class);
         if (bi != null) {
            for (String url : bi.libraryUrls()) {
               items.add(new Object[]{null, url});
            }
         }
      }

      if (items.isEmpty()) {
         ctx.text(this.font, this.picker == 1 ? "§7Keine Screenshots (F2 im Spiel)" : "§7Keine Bilder in Block Images", x + 6, gy + 4, -10656900, false);
      }

      String to = this.selected;

      for (int i = 0; i < items.size(); i++) {
         int cx = x + 4 + i % cols * (cw + 4);
         int cy = gy + i / cols * (ch + 4);
         if (cy + ch > y + h) {
            break;
         }

         Object[] it = items.get(i);
         boolean hov = Render2D.inside(mouseX, mouseY, cx, cy, cw, ch);
         if (it[0] != null) {
            this.drawShotThumb(ctx, (Path)it[0], cx, cy, cw, ch);
         } else {
            this.drawRemote(ctx, (String)it[1], cx, cy, cw, ch);
         }

         if (hov) {

            ctx.fill(cx, cy, cx + cw, cy + ch, 0x33FFFFFF);
            ctx.fill(cx, cy + ch - 1, cx + cw, cy + ch, ACCENT);
         }

         this.hits.add(new FriendsScreen.Hit(cx, cy, cw, ch, () -> {
            this.picker = 0;
            if (it[0] != null) {
               FriendChat.get().sendScreenshot(to, (Path)it[0], s -> this.chatStatus = s);
            } else {
               FriendChat.get().send(to, null, (String)it[1], err -> this.chatStatus = "§c" + err);
            }
         }));
      }
   }

   private void drawShotThumb(GuiGraphicsExtractor ctx, Path file, int x, int y, int w, int h) {
      FriendsScreen.Thumb t = this.thumbs.computeIfAbsent(file, f -> {
         FriendsScreen.Thumb n = new FriendsScreen.Thumb(Identifier.fromNamespaceAndPath("nebula", "chatshot/" + Integer.toHexString(f.toString().hashCode())));
         java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
               java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(f.toFile());
               int tw = 128;
               int th = Math.max(1, src.getHeight() * tw / src.getWidth());
               java.awt.image.BufferedImage small = new java.awt.image.BufferedImage(tw, th, java.awt.image.BufferedImage.TYPE_INT_ARGB);
               java.awt.Graphics2D g = small.createGraphics();
               g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
               g.drawImage(src, 0, 0, tw, th, null);
               g.dispose();
               n.w = tw;
               n.h = th;
               n.pixels = small.getRGB(0, 0, tw, th, null, 0, tw);
            } catch (Exception var8) {
               n.failed = true;
            }
         });
         return n;
      });
      if (t.pixels != null && !t.uploaded) {
         DynamicTextures.upload(t.id, null, t.pixels, t.w, t.h);
         t.uploaded = true;
      }

      if (t.uploaded) {
         ctx.blit(RenderPipelines.GUI_TEXTURED, t.id, x, y, 0.0F, 0.0F, w, h, t.w, t.h, t.w, t.h);
      } else {
         Render2D.roundRect(ctx, x, y, w, h, 3, -15855591);
         ctx.text(this.font, t.failed ? "✕" : "…", x + w / 2 - 3, y + h / 2 - 4, -10656900, false);
      }
   }

   private void renderBigImage(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
      ctx.fill(0, 0, this.width, this.height, 0xE0000000);
      ImageCache.Entry e = FriendChat.get().images().get(this.bigImage);
      int maxW = this.width - 40;
      int maxH = this.height - 60;
      int w = maxW;
      int h = maxH;
      if (e.state() == ImageCache.State.READY && e.width() > 0) {
         float f = Math.min((float)maxW / e.width(), (float)maxH / e.height());
         w = Math.round(e.width() * f);
         h = Math.round(e.height() * f);
      }

      int x = (this.width - w) / 2;
      int y = (this.height - h) / 2 - 10;
      this.drawRemote(ctx, this.bigImage, x, y, w, h);
      String url = this.bigImage;
      int by = y + h + 8;
      BlockImages bi = NebulaClient.getInstance().getModuleManager().get(BlockImages.class);
      if (bi != null) {
         this.button(ctx, this.width / 2 - 104, by, 110, 16, "Als Block Image", ACCENT, -1, mouseX, mouseY, () -> {
            if (bi.addPainted(url, 2, 2)) {
               if (!bi.isEnabled()) {
                  bi.setEnabled(true);
               }

               this.toast("§aIn Block Images übernommen – Platzier-Modus an, dann Rechtsklick auf eine Wand");
            } else {
               this.toast("§cBlock-Images-Liste ist voll");
            }

            this.bigImage = null;
         });
      }

      this.button(ctx, this.width / 2 + 10, by, 94, 16, "Schließen", -14012603, -1578509, mouseX, mouseY, () -> this.bigImage = null);
   }

   private void sendTyped() {
      String text = this.chatInput.getText().strip();
      if (!text.isEmpty() && this.selected != null) {
         this.chatInput.setText("");
         this.chatScroll = 0;
         FriendChat.get().send(this.selected, text, null, err -> this.chatStatus = "§c" + err);
      }
   }

   private void renderProfile(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int mouseX, int mouseY, Friends.Info i) {
      String name = this.selected;
      int sy = y + 46;
      Render2D.rect(ctx, x + 10, sy - 4, w - 20, 1, -14802381);
      ctx.text(this.font, "STATUS", x + 12, sy, -10656900, false);
      sy += 12;
      boolean sync = this.friends.serverSync();
      Player near = this.nearby(name);
      boolean meowl = near != null && MeowlPet.peer(near) != null;
      String[][] rows = new String[][]{
         {"Freundschaft", i == null ? (sync ? "noch nicht bestätigt" : "nur lokal") : (i.mutual() ? "gegenseitig ✔" : "wartet auf " + name)},
         {"Meowl Server", i == null ? "–" : (i.online() ? "online" : "offline")},
         {"Dieser Server", this.tabEntry(name) != null ? "ja" : "nein"},
         {"In deiner Nähe", near != null && this.minecraft.player != null ? Math.round(Math.sqrt(this.minecraft.player.distanceToSqr(near))) + " Blöcke" : "nein"},
         {"Meowl Client", near == null ? "–" : (meowl ? "erkannt" : "nicht erkannt")}
      };

      for (String[] r : rows) {
         ctx.text(this.font, r[0], x + 12, sy, -7564628, false);
         int vw = this.font.width(r[1]);
         int vc = !r[1].contains("✔") && !r[1].equals("online") && !r[1].equals("ja") && !r[1].equals("erkannt")
            ? (r[1].startsWith("wartet") ? -278748 : -1578509)
            : -11870592;
         ctx.text(this.font, r[1], x + w - 12 - vw, sy, vc, false);
         sy += 12;
      }

      int ay = y + h - 50;
      Render2D.rect(ctx, x + 10, ay - 8, w - 20, 1, -14802381);
      ctx.text(this.font, "AKTIONEN", x + 12, ay - 4, -10656900, false);
      ay += 8;
      int bw = (w - 30) / 2;
      boolean canShare = i != null && i.mutual() && i.sameServer();
      this.button(ctx, x + 10, ay, bw, 16, "⌖ Standort teilen", canShare ? ACCENT : -14012603, canShare ? -1 : -10656900, mouseX, mouseY, () -> {
         FriendLocator loc = NebulaClient.getInstance().getModuleManager().get(FriendLocator.class);
         if (!canShare) {
            this.toast("§7Nur mit gegenseitigen Freunden auf demselben Server");
         } else if (loc != null) {
            loc.share(false);
            this.toast("§bStandort wird geteilt");
         }
      });
      this.button(ctx, x + 20 + bw, ay, bw, 16, "⚔ Tiers", -14012603, -1578509, mouseX, mouseY, () -> {
         Tiers t = NebulaClient.getInstance().getModuleManager().get(Tiers.class);
         if (t != null) {
            t.openTracker(name);
         }
      });
      ay += 20;
      this.button(ctx, x + 10, ay, bw, 16, "⧉ Name kopieren", -14012603, -1578509, mouseX, mouseY, () -> {
         this.minecraft.keyboardHandler.setClipboard(name);
         this.toast("§7Name kopiert");
      });
      boolean confirm = name.equalsIgnoreCase(this.confirmRemove);
      this.button(
         ctx, x + 20 + bw, ay, bw, 16, confirm ? "Wirklich entfernen?" : "✕ Entfernen", confirm ? -8446691 : -12968155, -495247, mouseX, mouseY, () -> {
            if (confirm) {
               this.friends.remove(name, false);
               this.toast("§7" + name + " entfernt");
               this.selected = null;
               this.confirmRemove = null;
            } else {
               this.confirmRemove = name;
            }
         }
      );
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      if (this.bigImage != null) {
         for (int k = this.hits.size() - 1; k >= this.overlayStart; k--) {
            FriendsScreen.Hit h = this.hits.get(k);
            if (Render2D.inside(click.x(), click.y(), h.x, h.y, h.w, h.h)) {
               h.action.run();
               return true;
            }
         }

         this.bigImage = null;
         return true;
      }

      if (this.chatInput.mouseClicked(click.x(), click.y(), this.font)) {
         this.search.setFocused(false);
         return true;
      } else if (this.search.mouseClicked(click.x(), click.y(), this.font)) {
         this.chatInput.setFocused(false);
         return true;
      } else {
         for (int k = this.hits.size() - 1; k >= 0; k--) {
            FriendsScreen.Hit h = this.hits.get(k);
            if (Render2D.inside(click.x(), click.y(), h.x, h.y, h.w, h.h)) {
               h.action.run();
               return true;
            }
         }

         this.confirmRemove = null;
         return super.mouseClicked(click, doubled);
      }
   }

   @Override
   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (this.chatArea != null && this.chatView && this.picker == 0 && Render2D.inside(mouseX, mouseY, this.chatArea[0], this.chatArea[1], this.chatArea[2], this.chatArea[3])) {
         this.chatScroll += (int)(verticalAmount * 20.0);
         return true;
      }

      this.scroll -= (int)(verticalAmount * 26.0);
      return true;
   }

   @Override
   public boolean charTyped(CharacterEvent input) {
      if (this.chatInput.isFocused()) {
         return this.chatInput.charTyped(input.codepoint());
      } else if (this.search.isFocused()) {
         this.scroll = 0;
         return this.search.charTyped(input.codepoint());
      } else {
         return super.charTyped(input);
      }
   }

   @Override
   public boolean keyPressed(KeyEvent input) {
      if (this.bigImage != null && input.key() == InputConstants.KEY_ESCAPE) {
         this.bigImage = null;
         return true;
      } else if (this.chatInput.isFocused() && input.key() == InputConstants.KEY_ESCAPE) {
         this.chatInput.setFocused(false);
         return true;
      } else if (this.chatInput.isFocused()) {
         return this.chatInput.keyPressed(input.key(), input.modifiers());
      } else if (this.search.isFocused() && input.key() != InputConstants.KEY_ESCAPE) {
         return this.search.keyPressed(input.key(), input.modifiers());
      } else if (input.key() == InputConstants.KEY_F && com.lennart.nebula.compat.Keys.ctrl(input.modifiers())) {
         this.search.setFocused(true);
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   private record Entry(String name, Friends.Info info, FriendsScreen.Kind kind) {
   }

   private record Hit(int x, int y, int w, int h, Runnable action) {
   }

   private static final class Thumb {
      final Identifier id;
      volatile int[] pixels;
      volatile int w;
      volatile int h;
      volatile boolean failed;
      boolean uploaded;

      Thumb(Identifier id) {
         this.id = id;
      }
   }

   private static enum Kind {
      FRIEND,
      REQUEST,
      SUGGESTION;
   }

   private static enum Tab {
      ALL("Alle"),
      ONLINE("Online"),
      REQUESTS("Anfragen");

      final String label;

      private Tab(String label) {
         this.label = label;
      }
   }
}
